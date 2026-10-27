package at.rabattradar.core.source

import at.rabattradar.core.model.TierKind
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.PriceTier
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.net.Http
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.parse.Deposits
import at.rabattradar.core.text.Text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlin.math.roundToInt

/**
 * Promotions of the INTERSPAR online shop (FACT-Finder search, `is-on-promotion:true`).
 * Mostly wine, drinks and household goods - the weekly SPAR flyer itself comes from marktguru,
 * because spar.at is behind Cloudflare bot protection.
 */
class SparShopSource(private val http: Http) : OfferSource {
    override val id = SourceId.SPAR_SHOP
    override val chains = setOf(Chain.SPAR)

    override suspend fun fetch(ctx: FetchContext): List<Offer> {
        val hits = mutableListOf<JsonElement>()
        var page = 1
        while (page <= 10) {
            val root = json.parseToJsonElement(
                http.get("$SEARCH?query=*&q=*&page=$page&hitsPerPage=$PAGE&filter=is-on-promotion:true", emptyMap()),
            )
            hits += root["hits"].arr
            val pageCount = root["paging"]["pageCount"].lng ?: 1
            if (page >= pageCount) break
            page++
        }
        return hits.mapNotNull { parse(it["masterValues"] ?: return@mapNotNull null) }.distinctBy { it.id }
    }

    private fun JsonElement?.first(): String? = ((this as? JsonArray)?.firstOrNull() ?: this).str

    internal fun parse(v: JsonElement): Offer? {
        if (v["is-on-promotion"].str != "true") return null
        val id = v["product-number"].str ?: return null
        val price = v["price"].dbl ?: return null
        val regular = v["regular-price"].dbl?.takeIf { it > price + 0.004 }
        val name = Text.clean(v["short-description"].str ?: v["short-description-2"].str ?: v["name"].str) ?: return null
        val brand = Text.clean(v["ecr-brand"].first() ?: v["title"].str)?.let(Text::titleCaseBrand)
        val quantity = Text.clean(v["short-description-3"].str)?.lowercase()
        val promoText = Text.clean(v["promotion-text"].str)

        val conditions = mutableListOf(
            Conditions.onlineShop("INTERSPAR"),
            Conditions.noDates("INTERSPAR"),
        )
        val tiers = mutableListOf<PriceTier>()
        promoText?.let { Conditions.minQuantity(it, perSet = false) }?.let {
            conditions += it
            if (regular != null) tiers += PriceTier(TierKind.SINGLE, regular, unit = it.unit)
        }
        if (v["item-type"].str == "WeightProduct") conditions += Conditions.weightPrice()

        val category = v["category-names"].str?.split('|')?.getOrNull(1)?.takeIf { it.isNotBlank() }
        return Offer(
            id = "spar-$id",
            chain = Chain.SPAR,
            banners = listOf("INTERSPAR"),
            name = name,
            brand = brand?.takeUnless { name.startsWith(it, ignoreCase = true) },
            description = null,
            quantity = quantity,
            category = category,
            price = price,
            regularPrice = regular,
            tiers = tiers,
            discountPercent = regular?.let { ((1 - price / it) * 100).roundToInt() }?.takeIf { it in 1..95 },
            unitPrice = v["price-per-unit"].str?.let { ppu ->
                // "4.03 €/kg" -> "1 kg = 4.03"
                val m = Regex("""([\d.,]+)\s*€\s*/\s*(\S+)""").find(ppu) ?: return@let ppu
                "1 ${m.groupValues[2]} = ${m.groupValues[1]}"
            },
            conditions = Conditions.merge(conditions),
            imageUrl = v["image-url"].str,
            url = v["url"].str?.let { "https://www.interspar.at/shop/lebensmittel$it" },
            sources = listOf(SourceId.SPAR_SHOP),
        ).withDeposit(
            // short-description-3 carries "3.000 ML EINWEG mit Pfand" / "440 ML EINWEG ohne Pfand".
            Deposits.detect("${v["name"].str.orEmpty()} $name ${quantity.orEmpty()} ${v["packing-type"].str.orEmpty()} ${category.orEmpty()}"),
        )
    }

    companion object {
        const val SEARCH = "https://search-spar.spar-ics.com/fact-finder/rest/v4/search/products_lmos_at"
        const val PAGE = 500
    }
}
