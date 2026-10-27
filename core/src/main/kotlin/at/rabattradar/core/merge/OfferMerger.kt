package at.rabattradar.core.merge

import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.text.Text
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Combines offers from all sources:
 *  1. the same flyer offer listed for several banners (BILLA + BILLA PLUS, SPAR + EUROSPAR + INTERSPAR)
 *     becomes one offer with several banners;
 *  2. flyer offers (marktguru) are matched to shop offers of the same chain (same price, similar name,
 *     overlapping dates). The shop offer keeps its structured conditions and packshot and gains the flyer
 *     crop, banners and - for BILLA, whose shop has no dates - the flyer validity.
 */
object OfferMerger {

    private val stopwords = setOf(
        "oder", "od", "und", "mit", "versch", "verschiedene", "sorten", "div", "diverse", "je", "per", "pro", "ca",
        "g", "kg", "ml", "l", "stk", "stuck", "pkg", "packung", "flasche", "dose", "becher", "glas", "netz", "beutel",
        "x", "er", "in", "aus", "von", "der", "die", "das", "fur", "bio", "klasse", "i", "ii",
    )

    private val primarySources = setOf(SourceId.LIDL_WEB, SourceId.PENNY_WEB, SourceId.BILLA_SHOP, SourceId.SPAR_SHOP)

    fun merge(offers: List<Offer>): List<Offer> {
        val (flyer, primary) = offers.partition { o -> o.sources.none { it in primarySources } }
        val flyerDeduped = dedupeBanners(flyer)

        val result = primary.associateBy { it.id }.toMutableMap()
        val unmatched = mutableListOf<Offer>()
        val primaryByChain = primary.groupBy { it.chain }

        for (f in flyerDeduped) {
            val candidates = primaryByChain[f.chain].orEmpty()
                .map { it to score(f, it) }
                .filter { it.second >= MIN_SCORE }
                .sortedByDescending { it.second }
            if (candidates.isEmpty()) {
                unmatched += f
                continue
            }
            val best = candidates.first().first
            result[best.id] = combine(result.getValue(best.id), f)
            // "versch. Sorten" flyer items match several shop SKUs - give the others the flyer dates too.
            for ((other, _) in candidates.drop(1)) {
                val current = result.getValue(other.id)
                if (current.validTo == null) result[other.id] = adoptDates(current, f)
            }
        }
        val merged = propagatePromoDates(result.values.toList())
        return (merged + unmatched).map { it.copy(imageUrl = it.imageUrl ?: it.flyerImageUrl) }
    }

    /**
     * BILLA runs brand-wide promotions ("Milka div. Sorten, bei 3 Tafeln je 3.32"): the flyer lists one item,
     * the shop 20 SKUs. Shop offers without dates take them from a dated offer with the same promotion
     * signature (brand, price, single price, conditions).
     */
    private fun propagatePromoDates(offers: List<Offer>): List<Offer> {
        fun signature(o: Offer) = listOf(
            o.chain, o.brand?.let(Text::fold), o.price, o.regularPrice, o.tiers.firstOrNull()?.price,
            o.conditions.filter { it.type == ConditionType.MULTI_BUY || it.type == ConditionType.MIN_QUANTITY }.map { it.label.lowercase() },
        )
        val dated = offers.filter { it.validTo != null && it.brand != null }.groupBy(::signature)
        return offers.map { o ->
            if (o.validTo != null || o.brand == null || !o.has(ConditionType.NO_DATES)) return@map o
            val source = dated[signature(o)]?.firstOrNull() ?: return@map o
            val adopted = adoptDates(o, source)
            adopted.copy(
                conditions = Conditions.merge(
                    adopted.conditions,
                    listOf(
                        Condition(
                            ConditionType.NOTE, "Zeitraum übernommen",
                            "Aktionszeitraum vom gleichartigen Flugblatt-Angebot „${source.name}“ übernommen (gleiche Marke, gleicher Preis, gleiche Bedingung).",
                            inferred = true,
                        ),
                    ),
                ),
            )
        }
    }

    /** Same product + price + dates in several banners of one chain -> one offer. */
    fun dedupeBanners(offers: List<Offer>): List<Offer> =
        offers.groupBy { listOf(it.chain, Text.fold("${it.brand.orEmpty()} ${it.name}"), it.price, it.validFrom, it.validTo) }
            .values.map { group ->
                val first = group.minBy { it.id }
                if (group.size == 1) first else first.copy(
                    banners = group.flatMap { it.banners }.distinct(),
                    description = group.mapNotNull { it.description }.maxByOrNull { it.length },
                    conditions = Conditions.merge(*group.map { it.conditions }.toTypedArray()),
                )
            }

    private fun tokens(o: Offer): Set<String> =
        Text.tokens("${o.brand.orEmpty()} ${o.name}").filter { it.length > 1 && it !in stopwords && it.any(Char::isLetter) }.toSet()

    private fun prices(o: Offer): Set<Double> =
        (listOf(o.price) + o.tiers.filter { it.label.startsWith("ohne") }.map { it.price }).toSet()

    /** 0..1 similarity, 0 if prices or dates rule a match out. */
    fun score(flyer: Offer, shop: Offer): Double {
        if (flyer.chain != shop.chain) return 0.0
        val fp = prices(flyer)
        val sp = prices(shop)
        if (fp.none { a -> sp.any { b -> abs(a - b) < 0.011 } }) return 0.0
        if (flyer.validFrom != null && flyer.validTo != null && shop.validFrom != null && shop.validTo != null) {
            if (flyer.validTo.isBefore(shop.validFrom) || shop.validTo.isBefore(flyer.validFrom)) return 0.0
        }
        val a = tokens(flyer)
        val b = tokens(shop)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.count { x -> b.any { y -> x == y || (minOf(x.length, y.length) >= 4 && (x.startsWith(y) || y.startsWith(x))) } }
        return common.toDouble() / minOf(a.size, b.size)
    }

    private fun adoptDates(shop: Offer, flyer: Offer): Offer {
        // A flyer with the same promotion confirms it is a regular in-store offer.
        val conditions = shop.conditions.filterNot {
            it.type == ConditionType.NO_DATES || it.type == ConditionType.WEEKDAYS ||
                (it.type == ConditionType.ONLINE_SHOP && SourceId.BILLA_SHOP in shop.sources)
        }
        return shop.copy(
            validFrom = flyer.validFrom,
            validTo = flyer.validTo,
            banners = (shop.banners + flyer.banners).distinct(),
            conditions = Conditions.merge(conditions, listOfNotNull(Conditions.weekdays(flyer.validFrom, flyer.validTo))),
        )
    }

    private fun combine(shop: Offer, flyer: Offer): Offer {
        val dated = if (shop.validTo == null && flyer.validTo != null) adoptDates(shop, flyer) else shop
        val confirmed = dated.conditions.filterNot { it.type == ConditionType.ONLINE_SHOP && SourceId.BILLA_SHOP in shop.sources }
        val regular = dated.regularPrice ?: flyer.regularPrice?.takeIf { it > dated.price }
        return dated.copy(
            banners = (dated.banners + flyer.banners).distinct(),
            description = dated.description ?: flyer.description,
            category = dated.category ?: flyer.category,
            unitPrice = dated.unitPrice ?: flyer.unitPrice,
            regularPrice = regular,
            discountPercent = dated.discountPercent
                ?: regular?.let { ((1 - dated.price / it) * 100).roundToInt() }?.takeIf { it in 1..95 },
            conditions = Conditions.merge(confirmed, flyer.conditions.filterNot { it.inferred }),
            imageUrl = dated.imageUrl ?: flyer.imageUrl,
            flyerImageUrl = flyer.flyerImageUrl,
            sources = (dated.sources + flyer.sources).distinct(),
        )
    }

    private const val MIN_SCORE = 0.6
}
