package at.rabattradar.core.source

import at.rabattradar.core.model.TierKind
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.PriceTier
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.net.Http
import at.rabattradar.core.net.HttpException
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.text.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.abs

/** Text of one printed flyer page (BILLA/BILLA PLUS publish their flyers on Publitas with extracted text). */
data class FlyerPage(val banner: String, val publication: String, val number: Int, val lines: List<String>)

/**
 * Recovers multi-buy conditions that marktguru drops for BILLA flyer offers ("EXTREM AKTION 3+3 GRATIS,
 * BEI 6 FL. JE 1.24"). The Publitas page text contains all elements of the price tag, but two-column
 * layouts interleave neighbouring products, so matching does not rely on text order alone:
 *
 *  1. find the product name on the page,
 *  2. take the reference line "1 FL. € 2.49" whose value equals the offer's normal price, nearest to the name,
 *  3. take the multi-buy label "X+Y" that is arithmetically consistent: price x (X+Y) = normal price x X
 *     (rules out 2+1/4+2 next to a 3+3), nearest to the reference line,
 *  4. or, for "AB 2 DOSEN JE" offers, the nearest "AB n <unit> JE" line with the reference unit.
 */
class BillaFlyerText(private val http: Http) {

    val id = SourceId.BILLA_FLYER

    suspend fun pages(today: LocalDate): List<FlyerPage> = coroutineScope {
        val html = http.get(FLYER_PAGE, emptyMap())
        val week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val found = slug.findAll(html).map { it.groupValues[1] to it.groupValues[2] }.distinct().toList()
            .filter { (_, s) -> kw.find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { it >= week - 1 } ?: false }
        // Next week's flyer is usually online a day or two before it starts.
        val guessed = found.mapNotNull { (group, s) ->
            val m = kw.find(s) ?: return@mapNotNull null
            group to s.replaceRange(m.groups[1]!!.range, (m.groupValues[1].toInt() + 1).toString().padStart(2, '0'))
        }
        (found + guessed).distinct().map { (group, s) ->
            async {
                try {
                    val root = json.parseToJsonElement(http.get("$PUBLITAS/$group/$s/spreads.json", emptyMap()))
                    val banner = if (group == "billa-plus") "BILLA PLUS" else "BILLA"
                    root.arr.flatMap { it["pages"].arr }.mapNotNull { page ->
                        val text = page["text"].str ?: return@mapNotNull null
                        FlyerPage(banner, s, page["number"].lng?.toInt() ?: 0, text.lines().map { it.trim() }.filter { it.isNotEmpty() })
                    }
                } catch (e: HttpException) {
                    if (e.code == 404) emptyList() else throw e
                }
            }
        }.awaitAll().flatten()
    }

    companion object {
        const val FLYER_PAGE = "https://www.billa.at/unsere-aktionen/flugblatt"
        const val PUBLITAS = "https://view.publitas.com"

        private val slug = Regex("""view\.publitas\.com(?:\\u002F|/)(billa-at|billa-plus)(?:\\u002F|/)([a-zA-Z0-9_-]+)""")
        private val kw = Regex("""kw(\d{1,2})""")
        private val reference = Regex("""^1\s+([A-ZÄÖÜ][A-ZÄÖÜa-zäöü./]*)\s*€\s*(\d+[.,]\d{2})$""")
        private val multiLabel = Regex("""^(\d{1,2})\s*\+\s*(\d{1,2})$""")
        private val quantityLine = Regex("""^(BEI|AB)\s+(\d{1,2})\s+([A-ZÄÖÜ][A-ZÄÖÜa-zäöü./-]*)\s+JE$""")

        private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

        private fun stem(unit: String) = Text.fold(unit.substringBefore('/')).take(3)

        /** Search words of an offer (name/brand tokens and the brand's own tokens), each prefixed with a space. */
        private class Tokens(val all: List<String>, val brand: List<String>)

        private fun tokensOf(offer: Offer) = Tokens(
            offerTokens(offer).map { " $it" },
            offer.brand?.let { Text.tokens(it).filter { t -> t.length > 2 } }.orEmpty().map { " $it" },
        )

        /**
         * Index of the line where the offer's brand/name appears. A brand alone is enough - prices decide the rest.
         * [folded] are the page lines as " folded text ", prepared once per page.
         */
        private fun anchor(folded: List<String>, t: Tokens): Int? {
            val tokens = t.all
            if (tokens.isEmpty()) return null
            val brand = t.brand
            var best: Int? = null
            var bestScore = 0
            for (i in folded.indices) {
                if (tokens.none { folded[i].contains(it) }) continue
                val window = (i until minOf(i + 4, folded.size)).joinToString(" ") { folded[it] }
                val score = tokens.count { window.contains(it) } + if (brand.isNotEmpty() && brand.all { window.contains(it) }) 1 else 0
                if (score > bestScore) {
                    best = i
                    bestScore = score
                }
            }
            return best?.takeIf { bestScore >= 2 || tokens.size == 1 }
        }

        private fun offerTokens(o: Offer): List<String> =
            Text.tokens("${o.brand.orEmpty()} ${o.name}").filter { it.length > 2 && it !in setOf("div", "sorten", "oder", "billa", "bio", "immer", "gut") }.distinct()

        private fun hasQuantityCondition(o: Offer) =
            o.conditions.any { (it.type == ConditionType.MULTI_BUY || it.type == ConditionType.MIN_QUANTITY) && !it.inferred }

        private class Ref(val index: Int, val unit: String)
        private class Label(val index: Int, val buy: Int, val free: Int)

        /**
         * Adds the multi-buy / minimum quantity printed in the flyer to BILLA offers that lack it.
         * Works page by page: every price line and every "X+Y" label belongs to exactly one product, so all
         * BILLA offers on a page (also those that already have their conditions) compete for them in reading order.
         */
        fun enrichAll(offers: List<Offer>, pages: List<FlyerPage>): List<Offer> {
            val result = offers.toMutableList()
            val done = mutableSetOf<Int>()
            // Names do not change while conditions are added, so the words are prepared once per offer and page.
            val tokens = HashMap<Int, Tokens>()
            for (page in pages) {
                val folded = page.lines.map { " " + Text.fold(it) + " " }
                // 1. Offers on this page and their price line ("1 FL. € 2.49" = normal price), each line used once.
                val pairs = mutableListOf<Triple<Int, Int, Int>>() // offer index, ref line, distance
                val refLines = page.lines.withIndex().mapNotNull { (i, l) -> reference.find(l)?.let { i to it } }
                val refValues = refLines.mapNotNull { (li, m) -> num(m.groupValues[2])?.let { li to it } }
                for ((oi, o) in result.withIndex()) {
                    if (o.chain != Chain.BILLA || oi in done) continue
                    if (o.banners.isNotEmpty() && page.banner !in o.banners) continue
                    val regular = o.regularPrice ?: continue
                    // Only a page with a price line equal to the normal price can hold the offer.
                    val sameValue = refValues.filter { (_, v) -> abs(v - regular) < 0.005 }
                    if (sameValue.isEmpty()) continue
                    val at = anchor(folded, tokens.getOrPut(oi) { tokensOf(o) }) ?: continue
                    for ((li, _) in sameValue) {
                        val d = abs(li - at)
                        if (d <= 25) pairs += Triple(oi, li, d)
                    }
                }
                val refOf = mutableMapOf<Int, Ref>()
                val usedRefs = mutableSetOf<Int>()
                for ((oi, li, _) in pairs.sortedBy { it.third }) {
                    if (oi in refOf || li in usedRefs) continue
                    refOf[oi] = Ref(li, reference.find(page.lines[li])!!.groupValues[1])
                    usedRefs += li
                }

                // 2. Multi-buy labels, assigned in reading order to the first offer whose prices fit them.
                val labels = page.lines.withIndex().mapNotNull { (i, l) ->
                    multiLabel.find(l)?.let { m -> Label(i, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
                }.filter { it.buy >= 1 && it.free >= 1 && it.buy + it.free <= 24 }
                val quantities = page.lines.withIndex().mapNotNull { (i, l) ->
                    quantityLine.find(l)?.let { m -> Triple(i, m.groupValues[1].uppercase() == "BEI", m.groupValues[2].toInt() to m.groupValues[3]) }
                }
                val usedLabels = mutableSetOf<Int>()
                val usedQuantities = mutableSetOf<Int>()
                for ((oi, ref) in refOf.entries.sortedBy { it.value.index }) {
                    val o = result[oi]
                    val regular = o.regularPrice!!
                    val fits = labels.filter { it.index !in usedLabels && abs(it.index - ref.index) <= 30 }.filter {
                        val n = it.buy + it.free
                        abs(o.price * n - regular * it.buy) <= 0.012 * n
                    }
                    // Prefer a label whose "BEI n <unit> JE" line uses the same unit as the price line ("FL./PKG.").
                    val label = fits.minWithOrNull(
                        compareBy<Label> { l -> if (quantities.any { q -> q.second && q.third.first == l.buy + l.free && q.third.second == ref.unit }) 0 else 1 }
                            .thenBy { abs(it.index - ref.index) },
                    )
                    if (label != null) {
                        usedLabels += label.index
                        val n = label.buy + label.free
                        val line = quantities.filter { it.second && it.third.first == n && it.first !in usedQuantities }
                            .minByOrNull { abs(it.first - ref.index) }
                        line?.let { usedQuantities += it.first }
                        if (!hasQuantityCondition(o)) {
                            result[oi] = apply(o, label.buy, label.free, n, line?.third?.second ?: ref.unit, perSet = true, single = regular)
                        }
                        done += oi
                    }
                }

                // 3. "AB 2 DOSEN JE" without a label: same unit as the price line and close to it.
                for ((oi, ref) in refOf.entries.sortedBy { it.value.index }) {
                    if (oi in done) continue
                    val o = result[oi]
                    val from = quantities.filter { !it.second && it.first !in usedQuantities && stem(it.third.second) == stem(ref.unit) && it.third.first >= 2 }
                        .minByOrNull { abs(it.first - ref.index) }
                    if (from != null && abs(from.first - ref.index) <= 12) {
                        usedQuantities += from.first
                        if (!hasQuantityCondition(o)) {
                            result[oi] = apply(o, null, null, from.third.first, from.third.second, perSet = false, single = o.regularPrice!!)
                        }
                        done += oi
                    }
                }
            }
            return result
        }

        private fun titleUnit(raw: String) =
            raw.split('/').joinToString("/") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }

        private fun apply(offer: Offer, buy: Int?, free: Int?, n: Int, unitRaw: String, perSet: Boolean, single: Double): Offer {
            val unitText = titleUnit(unitRaw)
            val minQty = Conditions.minQuantity("${if (perSet) "bei" else "ab"} $n $unitText", perSet = perSet)
            val multi = if (buy != null && free != null) Conditions.multiBuyCondition(buy, free).copy(unit = minQty?.unit) else null
            val tiers = if (offer.tiers.none { it.kind == TierKind.SINGLE }) {
                listOf(PriceTier(TierKind.SINGLE, single, unit = minQty?.unit ?: "Stk.")) + offer.tiers
            } else offer.tiers
            return offer.copy(
                conditions = Conditions.merge(
                    offer.conditions.filterNot { it.inferred && it.type == ConditionType.MULTI_BUY },
                    listOfNotNull(multi, minQty),
                ),
                tiers = tiers,
                sources = (offer.sources + SourceId.BILLA_FLYER).distinct(),
            )
        }
    }
}
