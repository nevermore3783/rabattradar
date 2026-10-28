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
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.parse.Deposits
import at.rabattradar.core.text.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlin.math.roundToInt

/**
 * In-store offers ("Filialangebote") from the lidl.at search API. `q=*` returns every current and
 * upcoming promotion (productOrigin = progress_event) with exact start/end dates, Lidl Plus prices
 * and multi-buy packaging texts such as "Bei 2 Stk. je 4x 100 g".
 */
class LidlSource(private val http: Http) : OfferSource {
    override val id = SourceId.LIDL_WEB
    override val chains = setOf(Chain.LIDL)

    override suspend fun fetch(ctx: FetchContext): List<Offer> = coroutineScope {
        suspend fun page(offset: Int) = json.parseToJsonElement(
            http.get("$BASE/q/api/search?q=*&offset=$offset&fetchsize=$PAGE&locale=de_AT&assortment=AT&version=v2.0.0", emptyMap()),
        )
        val first = page(0)
        val batch = first["items"].arr
        val total = (first["numFound"].lng ?: 0).toInt()
        // The API returns at most 60 items per request, so the remaining pages are loaded at the same time.
        val rest = if (batch.isEmpty()) emptyList() else {
            (batch.size until minOf(total, MAX_ITEMS) step batch.size).map { offset -> async { page(offset)["items"].arr } }.awaitAll().flatten()
        }
        (batch + rest).mapNotNull { parse(it) }.distinctBy { it.id }
    }

    internal fun parse(item: JsonElement): Offer? {
        val g = item["gridbox"]["data"] ?: return null
        val p = g["price"] ?: return null
        val lidlPlus = g["lidlPlus"][0]
        val plusPrice = lidlPlus["price"]["price"].dbl
        // Lidl Plus exclusive deals have no regular price at all.
        val plusOnly = p["price"].dbl == null && plusPrice != null
        val price = p["price"].dbl ?: plusPrice ?: return null
        val productId = g["productId"].lng ?: g["itemId"].lng ?: return null

        val fullTitle = Text.clean(g["fullTitle"].str) ?: return null
        val title = Text.clean(g["title"].str ?: g["keyfacts"]["title"].str) ?: fullTitle
        // fullTitle = "MILBONA Emmentaler Stück", title = "Emmentaler Stück" -> brand "Milbona"
        val brand = fullTitle.removeSuffix(title).trim().takeIf { it.isNotEmpty() && it != fullTitle }?.let(Text::titleCaseBrand)
        val description = g["keyfacts"]["description"].str?.let(Text::stripHtml)?.takeIf { it.isNotBlank() }
        val packaging = Text.clean(p["packaging"]["text"].str)
        val basePrice = Text.clean(p["basePrice"]["text"].str)

        val conditions = mutableListOf<Condition>()
        val tiers = mutableListOf<PriceTier>()

        // "1 kg = 5.79 | Einzeln = 2.79"
        val parts = basePrice?.split('|')?.map { it.trim() }.orEmpty()
        val unitPrice = parts.firstOrNull { !it.startsWith("Einzeln") }?.takeIf { it.isNotBlank() }
        val single = parts.firstOrNull { it.startsWith("Einzeln") }?.let(Text::parsePrice)

        packaging?.let { Conditions.minQuantity(it) }?.let { conditions += it }
        if (single != null && single > price) tiers += PriceTier(TierKind.SINGLE, single)

        val oldPrice = p["oldPrice"].dbl ?: p["discount"]["deletedPrice"].dbl
        var headline = price
        if (plusPrice != null && plusPrice <= price) {
            conditions += Conditions.lidlPlusCondition(plusPrice, exclusive = plusOnly, highlight = lidlPlus["highlightText"].str)
            if (plusPrice < price) {
                tiers += PriceTier(TierKind.WITHOUT, price, program = "Lidl Plus")
                headline = plusPrice
            }
        }

        val text = listOfNotNull(title, description, packaging).joinToString(" ")
        Conditions.multiBuy(text)?.let { conditions += it }
        Conditions.weightPrice(packaging ?: "")?.let { conditions += it }
        Conditions.variants(text)?.let { conditions += it }

        val validFrom = g["storeStartDate"].lng?.let(::epochSecondsToDate)
        val validTo = g["storeEndDate"].lng?.let(::epochSecondsToDate)
        Conditions.weekdays(validFrom, validTo)?.let { conditions += it }
        if (g["stockAvailability"]["onlineAvailable"].bool == true) {
            conditions += Conditions.note(CondKey.ALSO_ONLINE)
        }

        val regular = (oldPrice ?: tiers.firstOrNull { it.kind == TierKind.WITHOUT }?.price)?.takeIf { it > headline }
        val discount = regular?.let { ((1 - headline / it) * 100).roundToInt() }?.takeIf { it in 1..95 }

        val image = g["imageList_V2"][0]["variants"].arr.firstOrNull { it["ratio"].str == "4:3" }?.get("sizes")?.get("mediumUrl").str
            ?: g["image"].str
        val category = g["keyfacts"]["wonCategoryPrimary"].str?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: g["category"].str

        return Offer(
            id = "lidl-$productId",
            chain = Chain.LIDL,
            banners = listOf("Lidl"),
            name = title,
            brand = brand,
            description = description,
            quantity = packaging?.replace(packagingPrefix, "")?.takeIf { it.isNotBlank() },
            category = category,
            price = headline,
            regularPrice = regular,
            tiers = tiers,
            discountPercent = discount,
            unitPrice = unitPrice,
            conditions = Conditions.merge(conditions),
            validFrom = validFrom,
            validTo = validTo,
            imageUrl = image,
            url = g["canonicalUrl"].str?.let { "$BASE$it" },
            sources = listOf(SourceId.LIDL_WEB),
        ).withDeposit(Deposits.detect("$fullTitle ${description.orEmpty()} ${packaging.orEmpty()} ${category.orEmpty()}"))
    }

    companion object {
        const val BASE = "https://www.lidl.at"
        const val PAGE = 60
        private const val MAX_ITEMS = 3000
        private val packagingPrefix = Regex("^(Je|Bei \\d+ Stk\\. je|Ab \\d+ Stk\\. je)\\s*", RegexOption.IGNORE_CASE)
    }
}
