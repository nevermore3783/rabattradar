package at.rabattradar.core.source

import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.PriceTier
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.net.Http
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.text.Text
import at.rabattradar.core.text.euro
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * BILLA and PENNY (both REWE Group) share the same commerce backend ("product-discovery").
 * Prices are in cents; promotions carry structured multi-buy info:
 * `promotionText = "Bei 3 Tafeln je"`, `promotionType = PER_SET_OF | FROM`, tags like `pt-2plus1`.
 */
internal object ReweProducts {

    fun parse(item: JsonElement, chain: Chain, source: SourceId, baseUrl: String, banner: String): Offer? {
        val sku = item["sku"].str ?: return null
        val priceObj = item["price"] ?: return null
        val regular = priceObj["regular"]
        val promoCents = regular["value"].dbl ?: return null
        val promo = promoCents / 100.0
        val standard = priceObj["standard"]["value"].dbl?.div(100)
        val crossed = priceObj["crossed"].dbl?.div(100)
        val loyalty = priceObj["loyalty"]["value"].dbl?.div(100)
        val lowest = priceObj["lowestPrice"].dbl?.div(100)
        val deposit = priceObj["depositPrice"].dbl?.div(100)
        val tags = regular["tags"].arr.mapNotNull { it.str }

        val name = Text.clean(item["name"].str) ?: return null
        val brand = Text.clean(item["brand"]["name"].str)?.takeUnless { it.equals(banner, true) }
        val description = Text.clean(item["descriptionShort"].str)
        val amount = item["amount"].str?.takeUnless { it.isBlank() || it == "0" }
        val volume = item["volumeLabelShort"].str
        val quantity = when {
            amount != null && volume != null && volume.isNotBlank() -> "$amount $volume"
            else -> Text.clean(item["packageLabel"].str)
        }

        val conditions = mutableListOf<Condition>()
        val tiers = mutableListOf<PriceTier>()

        val promoText = regular["promotionText"].str
        val promoType = regular["promotionType"].str
        val minQty = promoText?.let { Conditions.minQuantity(it, perSet = promoType == "PER_SET_OF") }
        minQty?.let { conditions += it }
        tags.firstNotNullOfOrNull { Conditions.multiBuyFromTag(it) }?.let { conditions += it.copy(unit = minQty?.unit) }
        if ("pt-abverkauf" in tags) conditions += Condition(ConditionType.CLEARANCE, "Abverkauf", "Abverkaufsware – nur solange der Vorrat reicht.")
        if ("pt-satterrabatt" in tags) conditions += Condition(ConditionType.NOTE, "Satter Rabatt", "Aktion „Satter Rabatt“ laut ${banner}.")
        if (deposit != null && deposit > 0) {
            conditions += Condition(ConditionType.DEPOSIT, "+ ${deposit.euro()} Pfand", "Zuzüglich ${deposit.euro()} € Pfand (wird an der Kassa verrechnet).")
        }
        if (item["weightArticle"].bool == true) conditions += Conditions.weightPrice("per kg")!!
        Conditions.variants(listOfNotNull(name, description).joinToString(" "))?.let { conditions += it }

        val single = standard ?: crossed
        if ((minQty != null || conditions.any { it.type == ConditionType.MULTI_BUY }) && single != null && single > promo) {
            tiers += PriceTier("1 ${minQty?.unit ?: "Stk."}", single)
        }

        var headline = promo
        if (loyalty != null && loyalty < promo) {
            conditions += Conditions.joeCondition(chain, loyalty)
            tiers += PriceTier("ohne jö", promo)
            headline = loyalty
        }
        if (lowest != null && lowest > 0 && abs(lowest - headline) > 0.005) tiers += PriceTier("Bestpreis 30 Tage", lowest)

        val regularPrice = (crossed ?: standard)?.takeIf { it > headline + 0.004 }
        val discount = priceObj["discountPercentage"].dbl?.let { abs(it).roundToInt() }?.takeIf { it > 0 }
            ?: regularPrice?.let { ((1 - headline / it) * 100).roundToInt() }?.takeIf { it in 1..95 }

        val validFrom = priceObj["validityStart"].str?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val validTo = priceObj["validityEnd"].str?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        Conditions.weekdays(validFrom, validTo)?.let { conditions += it }

        val unitPrice = regular["perStandardizedQuantity"].dbl?.let { per ->
            val unit = priceObj["baseUnitShort"].str ?: return@let null
            val factor = priceObj["basePriceFactor"].str?.toDoubleOrNull() ?: 1.0
            val f = if (factor % 1.0 == 0.0) factor.toInt().toString() else factor.toString()
            "$f $unit = ${String.format(Locale.US, "%.2f", per / 100)}"
        }

        val categoryPath = item["parentCategories"].arr.map { path -> path.arr.mapNotNull { it["name"].str } }
        val category = Text.clean(item["category"].str) ?: categoryPath.firstOrNull { p -> p.none { "ngebot" in it } }?.lastOrNull()
        categoryPath.flatten().firstOrNull { "Wochenend" in it || "Wochenstart" in it }?.let { campaign ->
            conditions += Condition(ConditionType.NOTE, campaign.replace(Regex("^bis zu -\\d+%\\s*"), ""), "Teil der Aktion „$campaign“.")
        }

        return Offer(
            id = "${source.name.lowercase()}-$sku",
            chain = chain,
            banners = listOf(banner),
            name = name,
            brand = brand,
            description = description,
            quantity = quantity,
            category = category,
            price = headline,
            regularPrice = regularPrice,
            tiers = tiers,
            discountPercent = discount,
            unitPrice = unitPrice,
            conditions = Conditions.merge(conditions),
            validFrom = validFrom,
            validTo = validTo,
            imageUrl = item["images"][0].str,
            url = item["slug"].str?.let { "$baseUrl/produkte/$it" },
            sources = listOf(source),
        )
    }

    suspend fun fetchAll(http: Http, url: (page: Int) -> String): List<JsonElement> {
        val out = mutableListOf<JsonElement>()
        var page = 0
        while (page < 50) {
            val root = json.parseToJsonElement(http.get(url(page), emptyMap()))
            val results = root["results"].arr
            out += results
            val total = root["total"].lng ?: 0
            if (results.isEmpty() || out.size >= total) break
            page++
        }
        return out
    }
}

/** BILLA online shop promotions (`inPromotion=true`): best source for BILLA multi-buy conditions, but no end dates. */
class BillaShopSource(private val http: Http) : OfferSource {
    override val id = SourceId.BILLA_SHOP
    override val chains = setOf(Chain.BILLA)

    override suspend fun fetch(ctx: FetchContext): List<Offer> =
        ReweProducts.fetchAll(http) { page -> "$BASE/api/product-discovery/products?page=$page&pageSize=500&inPromotion=true" }
            .filter { it["inPromotion"].bool != false }
            .mapNotNull { ReweProducts.parse(it, Chain.BILLA, id, BASE, "BILLA") }
            .map { offer ->
                if (offer.validTo != null) return@map offer
                offer.copy(
                    conditions = Conditions.merge(
                        offer.conditions,
                        listOf(
                            Condition(
                                ConditionType.NO_DATES, "Datum unbekannt",
                                "Der BILLA Online-Shop nennt keinen Aktionszeitraum. BILLA-Wochenaktionen laufen meist von Donnerstag bis Mittwoch.",
                            ),
                            Condition(ConditionType.ONLINE_SHOP, "lt. Online-Shop", "Preis aus dem BILLA Online-Shop – in der Filiale kann er abweichen."),
                        ),
                    ),
                )
            }

    companion object {
        const val BASE = "https://shop.billa.at"
    }
}

/** PENNY weekly offers from penny.at - includes validity dates and jö/App prices. */
class PennySource(private val http: Http) : OfferSource {
    override val id = SourceId.PENNY_WEB
    override val chains = setOf(Chain.PENNY)

    override suspend fun fetch(ctx: FetchContext): List<Offer> =
        ReweProducts.fetchAll(http) { page -> "$BASE/api/product-discovery/products?page=$page&pageSize=500" }
            .mapNotNull { ReweProducts.parse(it, Chain.PENNY, id, BASE, "PENNY") }

    companion object {
        const val BASE = "https://www.penny.at"
    }
}
