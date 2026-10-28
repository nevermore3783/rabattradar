package at.rabattradar.core.source

import at.rabattradar.core.model.TierKind
import at.rabattradar.core.model.CondKey
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.PriceTier
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.net.Http
import at.rabattradar.core.net.HttpException
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.parse.Deposits
import at.rabattradar.core.text.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Flyer offers from marktguru.at, which digitises the printed leaflets of all Austrian chains.
 *
 * It is the only practical source for HOFER (hofer.at is behind Akamai bot protection) and the
 * best one for the SPAR group (spar.at is behind Cloudflare). Weak spots, compensated elsewhere:
 * multi-buy conditions ("2+1 gratis") are usually lost, so [Conditions.inferMultiBuy] flags suspicious
 * prices, and the shop sources (Lidl/PENNY/BILLA) are merged on top where available.
 *
 * The API needs two keys that the marktguru web app embeds in its HTML; they are scraped at runtime.
 * Querying the banner name ("hofer") returns that banner's complete offer set.
 */
class MarktguruSource(
    private val http: Http,
    private val keyStore: KeyStore = KeyStore.InMemory(),
) : OfferSource {

    interface KeyStore {
        fun load(): Keys?
        fun save(keys: Keys)

        class InMemory : KeyStore {
            private var keys: Keys? = null
            override fun load() = keys
            override fun save(keys: Keys) { this.keys = keys }
        }
    }

    data class Keys(val apiKey: String, val clientKey: String)

    override val id = SourceId.MARKTGURU
    override val chains = setOf(Chain.BILLA, Chain.SPAR, Chain.HOFER, Chain.LIDL, Chain.PENNY)

    private val keyMutex = Mutex()

    /** Query term per chain; results are filtered by advertiser below. */
    private val queries = mapOf(
        Chain.BILLA to listOf("billa"),
        Chain.SPAR to listOf("spar"),
        Chain.HOFER to listOf("hofer"),
        Chain.LIDL to listOf("lidl"),
        Chain.PENNY to listOf("penny"),
    )

    suspend fun fetch(ctx: FetchContext, chains: Set<Chain>): List<Offer> = coroutineScope {
        val raw = chains.flatMap { queries[it].orEmpty() }.distinct()
            .map { term -> async { search(term, ctx.zipCode) } }
            .awaitAll()
            .flatten()
        raw.distinctBy { it["id"].str }.mapNotNull { parse(it, ctx) }.filter { it.chain in chains }
    }

    override suspend fun fetch(ctx: FetchContext): List<Offer> = fetch(ctx, chains)

    private suspend fun keys(force: Boolean): Keys = keyMutex.withLock {
        if (!force) keyStore.load()?.let { return it }
        val html = http.get(HOMEPAGE, emptyMap())
        val api = Regex(""""apiKey"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
        val client = Regex(""""clientKey"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
        if (api == null || client == null) error("marktguru: API-Schlüssel nicht gefunden (Seitenaufbau geändert?)")
        Keys(api, client).also(keyStore::save)
    }

    private suspend fun search(term: String, zip: String): List<JsonElement> = coroutineScope {
        suspend fun page(offset: Int): JsonElement {
            val url = "$SEARCH?as=web&q=${URLEncoder.encode(term, "UTF-8")}&zipCode=$zip&limit=$PAGE&offset=$offset"
            val body = try {
                getWithKeys(url, keys(force = false))
            } catch (e: HttpException) {
                if (e.code != 401 && e.code != 403) throw e
                getWithKeys(url, keys(force = true))
            }
            return json.parseToJsonElement(body)
        }
        val first = page(0)
        val batch = first["results"].arr
        val total = (first["totalResults"].lng ?: 0).toInt()
        // Further pages (rare: more than 1000 offers) are loaded at the same time.
        val rest = if (batch.isEmpty()) emptyList() else {
            (batch.size until total step batch.size).map { offset -> async { page(offset)["results"].arr } }.awaitAll().flatten()
        }
        batch + rest
    }

    private suspend fun getWithKeys(url: String, keys: Keys) =
        http.get(url, mapOf("x-apikey" to keys.apiKey, "x-clientkey" to keys.clientKey))

    internal fun parse(o: JsonElement, ctx: FetchContext): Offer? {
        val id = o["id"].lng ?: return null
        val advertiser = o["advertisers"].arr.mapNotNull { it["uniqueName"].str }.firstNotNullOfOrNull { BANNERS[it] } ?: return null
        val (chain, banner) = advertiser
        val price = o["price"].dbl ?: return null
        val productName = Text.clean(o["product"]["name"].str)
        val (description, hints) = Conditions.extractHints(o["description"].str)
        val name = productName ?: description ?: return null
        val brand = Text.clean(o["brand"]["name"].str)?.takeUnless {
            it.startsWith("thisisnobrand") || it.equals(banner, true) || it.equals(chain.displayName, true)
        }
        val oldPrice = o["oldPrice"].dbl?.takeIf { it > price }

        val validity = o["validityDates"].arr.mapNotNull { v ->
            val from = v["from"].str?.let(::isoInstantToDate)
            val to = v["to"].str?.let(::isoInstantToDate)
            if (from == null && to == null) null else from to to
        }
        val validFrom = validity.mapNotNull { it.first }.minOrNull()
        val validTo = validity.mapNotNull { it.second }.maxOrNull()

        val text = listOfNotNull(name, description).joinToString(" ")
        val conditions = mutableListOf<Condition>()
        val tiers = mutableListOf<PriceTier>()
        var headline = price

        for (hint in hints) {
            val p = hint.program.uppercase()
            when {
                "LIDL PLUS" in p -> conditions += Conditions.lidlPlusCondition(hint.price)
                "SPAR" in p -> conditions += Conditions.sparAppCondition(hint.price)
                "PENNY" in p || "JÖ" in p -> {
                    conditions += Conditions.joeCondition(chain, hint.price)
                    if ("APP" in p) conditions += Conditions.pennyAppCondition(hint.price)
                }
                "ONLINE" in p -> conditions += Conditions.onlineOnly()
                "APP" in p -> conditions += Condition(ConditionType.APP_COUPON, CondKey.APP_GENERIC)
            }
            // "MIT LIDL PLUS APP 0.49 €": the shown price is the normal promo price, the hint the member price.
            if (hint.price != null && hint.price < headline) {
                tiers += PriceTier(TierKind.WITHOUT, headline, program = if ("SPAR" in p) "voucher" else if ("LIDL" in p) "Lidl Plus" else "app")
                headline = hint.price
            }
        }
        if (o["requiresLoyalityMembership"].bool == true && conditions.none { it.type == ConditionType.LOYALTY_APP || it.type == ConditionType.LOYALTY_CARD }) {
            conditions += when (chain) {
                Chain.LIDL -> Conditions.lidlPlusCondition()
                Chain.PENNY, Chain.BILLA -> Conditions.joeCondition(chain)
                else -> Condition(ConditionType.LOYALTY_CARD, CondKey.LOYALTY_GENERIC)
            }
        }
        conditions += Conditions.fromText(text, chain)
        Conditions.weekdays(validFrom, validTo)?.let { conditions += it }
        if (oldPrice != null && chain != Chain.HOFER && chain != Chain.LIDL && conditions.none { it.type == ConditionType.MULTI_BUY || it.type == ConditionType.MIN_QUANTITY }) {
            Conditions.inferMultiBuy(price, oldPrice)?.let { conditions += it }
        }

        val unitPrice = o["referencePrice"].dbl?.let { ref ->
            val unit = o["unit"]["shortName"].str ?: return@let null
            if (unit.equals("Stk", true) && o["quantity"].dbl == 1.0) null else "1 ${unit.lowercase()} = ${"%.2f".format(java.util.Locale.US, ref)}"
        }
        val regular = oldPrice ?: tiers.firstOrNull()?.price?.takeIf { it > headline }
        return Offer(
            id = "mg-$id",
            chain = chain,
            banners = listOf(banner),
            name = name,
            brand = brand,
            description = description?.takeUnless { it == name },
            quantity = null,
            category = o["categories"].arr.firstNotNullOfOrNull { it["name"].str },
            price = headline,
            regularPrice = regular,
            tiers = tiers,
            discountPercent = regular?.let { ((1 - headline / it) * 100).roundToInt() }?.takeIf { it in 1..95 },
            unitPrice = unitPrice,
            conditions = Conditions.merge(conditions),
            validFrom = validFrom,
            validTo = validTo,
            imageUrl = "$CDN/offers/$id/images/default/0/medium.webp",
            flyerImageUrl = "$CDN/offers/$id/images/default/0/large.webp",
            url = null,
            sources = listOf(SourceId.MARKTGURU),
        ).withDeposit(Deposits.detect("$text ${o["categories"].arr.joinToString(" ") { it["name"].str.orEmpty() }}"))
    }

    companion object {
        const val HOMEPAGE = "https://www.marktguru.at/"
        const val SEARCH = "https://api.marktguru.at/api/v1/offers/search"
        const val CDN = "https://cdn.marktguru.at/api/v1"
        const val PAGE = 1000

        val BANNERS = mapOf(
            "billa" to (Chain.BILLA to "BILLA"),
            "billa-plus" to (Chain.BILLA to "BILLA PLUS"),
            "spar" to (Chain.SPAR to "SPAR"),
            "eurospar" to (Chain.SPAR to "EUROSPAR"),
            "interspar" to (Chain.SPAR to "INTERSPAR"),
            "spar-gourmet" to (Chain.SPAR to "SPAR Gourmet"),
            "hofer" to (Chain.HOFER to "HOFER"),
            "lidl" to (Chain.LIDL to "Lidl"),
            "penny" to (Chain.PENNY to "PENNY"),
        )

        /** marktguru retailer ids, used for the official logos. */
        val RETAILER_IDS = mapOf(
            "BILLA" to 12810, "BILLA PLUS" to 12780, "SPAR" to 12816, "EUROSPAR" to 12730,
            "INTERSPAR" to 12753, "HOFER" to 12747, "Lidl" to 12769, "PENNY" to 12802,
        )

        fun logoUrl(banner: String): String? = RETAILER_IDS[banner]?.let { "$CDN/retailers/$it/images/logos/0/small.png" }
    }
}
