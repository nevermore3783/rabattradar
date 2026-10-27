package at.rabattradar.core.source

import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.CondKey
import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.PriceTier
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.model.TierKind
import at.rabattradar.core.net.Http
import at.rabattradar.core.net.HttpException
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.text.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Recovers multi-buy conditions that marktguru drops for SPAR-group flyer offers ("1 Ds. 1,39 ab 24 Ds. je 0,69
 * 12+12 GRATIS 24er-Tray 16,56"). SPAR, EUROSPAR, SPAR Gourmet and INTERSPAR publish their flyers on iPaper,
 * whose hosts - unlike spar.at - are not behind Cloudflare, and every flipbook embeds the text of its pages.
 * The flipbook addresses follow a fixed scheme, the date being the first day of the flyer:
 *
 *  - flugblatt.spar.at/{region}/{spar|eurospar}/{yyMMdd}-1-flugblatt-kw-{week}/
 *  - flugblatt.spar.at/{region}/spar-gourmet/{yyMMdd}-1-flugblatt-kw-{week}-{week+1}/
 *  - flugblatt.interspar.at/{region}/{region}_kw{week}/
 *
 * A page's text is one run of words in layout order, with neighbouring products interleaved, so matching
 * works on character positions rather than on order:
 *
 *  1. the price block "1 Ds. 1,39 ab 24 Ds. je" whose single price equals the offer's normal price,
 *  2. the offer's brand or name close to it,
 *  3. a label "X+Y" with X+Y = 24 and price x (X+Y) = normal price x X, or the offer price right after "je",
 *  4. a pack price ("24er-Tray 16,56", "2 x 12er-Tray 15,36") equal to quantity x price.
 */
class SparFlyerText(private val http: Http) {

    val id = SourceId.SPAR_FLYER

    /**
     * Pages of the flyers that start on one of [offerStarts] (the marktguru validity dates of SPAR offers),
     * plus the INTERSPAR flyers of those weeks, the current and the next week.
     */
    suspend fun pages(zip: String, today: LocalDate, offerStarts: List<LocalDate>): List<FlyerPage> = coroutineScope {
        val region = region(zip)
        val starts = offerStarts
            .filter { !it.isBefore(today.minusDays(21)) && !it.isAfter(today.plusDays(8)) }
            .groupingBy { it }.eachCount()
            .filter { it.value >= 3 }.entries.sortedByDescending { it.value }.take(3).map { it.key }
            .ifEmpty { listOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY))) }

        val flipbooks = mutableListOf<Flipbook>()
        for (start in starts) {
            val day = start.format(yyMMdd)
            for (w in weekVariants(week(start))) {
                flipbooks += Flipbook("SPAR", listOf("$SPAR_HOST/$region/spar/$day-1-flugblatt-kw-$w/"))
                flipbooks += Flipbook("EUROSPAR", listOf("$SPAR_HOST/$region/eurospar/$day-1-flugblatt-kw-$w/"))
                if (region in GOURMET_REGIONS) {
                    val next = (week(start) % 52 + 1).let { if (w.length == 2 && it < 10) "0$it" else "$it" }
                    flipbooks += Flipbook("SPAR Gourmet", listOf("$SPAR_HOST/$region/spar-gourmet/$day-1-flugblatt-kw-$w-$next/"))
                }
            }
        }
        val weeks = (starts.map(::week) + week(today) + week(today.plusWeeks(1))).distinct()
        val inter = intersparRegion(zip)
        for (w in weeks) {
            // Regional INTERSPAR flyers occasionally carry typos in their address; Vienna's is the fallback.
            flipbooks += Flipbook("INTERSPAR", listOf("$INTERSPAR_HOST/${inter.first}/${inter.second}_kw$w/", "$INTERSPAR_HOST/wien/wien_kw$w/").distinct())
        }
        flipbooks.distinct().map { async { load(it) } }.awaitAll().flatten()
    }

    private data class Flipbook(val banner: String, val urls: List<String>)

    private suspend fun load(book: Flipbook): List<FlyerPage> {
        for (url in book.urls) {
            val html = try {
                http.get(url, emptyMap())
            } catch (e: HttpException) {
                if (e.code == 404) continue else throw e
            }
            val texts = pageTexts(html) ?: return emptyList()
            return texts.mapIndexedNotNull { i, t -> t.takeIf { it.isNotBlank() }?.let { FlyerPage(book.banner, url, i + 1, listOf(it)) } }
        }
        return emptyList()
    }

    companion object {
        const val SPAR_HOST = "https://flugblatt.spar.at"
        const val INTERSPAR_HOST = "https://flugblatt.interspar.at"

        private val GOURMET_REGIONS = setOf("wien", "niederoesterreich", "burgenland")
        private val yyMMdd = DateTimeFormatter.ofPattern("yyMMdd")

        private fun week(d: LocalDate) = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

        /** The zero-padded form is a guess for January - both are tried. */
        private fun weekVariants(w: Int) = if (w < 10) listOf("$w", "0$w") else listOf("$w")

        /** Federal state of an Austrian postal code, as used in the flyer addresses. */
        fun region(zip: String): String = when (zip.trim().take(4).toIntOrNull() ?: 1010) {
            in 1000..1999 -> "wien"
            in 2000..3999 -> "niederoesterreich"
            in 4000..4999, in 5210..5299 -> "oberoesterreich"
            in 5000..5999 -> "salzburg"
            in 6000..6699, in 9900..9999 -> "tirol"
            in 6700..6999 -> "vorarlberg"
            in 7000..7999 -> "burgenland"
            in 8000..8999 -> "steiermark"
            in 9000..9899 -> "kaernten"
            else -> "wien"
        }

        /** INTERSPAR splits Burgenland and has its own East Tyrol flyer. */
        private fun intersparRegion(zip: String): Pair<String, String> {
            val z = zip.trim().take(4).toIntOrNull() ?: 1010
            return when {
                z in 9900..9999 -> "osttirol" to "osttirol"
                z in 7000..7399 -> "burgenland" to "burgenland_nord"
                z in 7400..7999 -> "burgenland" to "burgenland_sued"
                else -> region(zip).let { it to it }
            }
        }

        /** The `pageTexts` array of the flipbook's `window.staticSettings`. */
        internal fun pageTexts(html: String): List<String>? {
            val key = html.indexOf("\"pageTexts\":")
            if (key < 0) return null
            val start = html.indexOf('[', key)
            if (start < 0) return null
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until html.length) {
                val c = html[i]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> if (--depth == 0) {
                        return json.parseToJsonElement(html.substring(start, i + 1)).arr.map { it.str.orEmpty() }
                    }
                }
            }
            return null
        }

        // --- Matching ---------------------------------------------------------------------------------------

        private const val PRICE = """\d{1,3},\s?\d{2}"""
        private const val UNIT = """[A-Za-zÄÖÜäöüß]{2,12}\.?"""

        /** "1 Ds. 1,39 ab 24 Ds. je 0,69", "1 Packung 3,69/3,79 ab 2 Packungen je 2 99", "1 Stück 0,94 GRATIS bei 3 Stück je". */
        private val block = Regex(
            """\b1\s+($UNIT)\s+($PRICE(?:\s*/\s*$PRICE)?)\s+(?:GRATIS\s+)?(ab|bei)\s+(\d{1,2})\s+($UNIT)\s+je\b(?:\s+(\d{1,3})[,\s]\s?(\d{2})(?!\d))?""",
        )

        /** "12+12 GRATIS", "4 + 2 GRATIS", "12 + 12 G R AT I S" - only accepted when the numbers fit the prices. */
        private val label = Regex("""(?<![\d,])(\d{1,2})\s*\+\s*(\d{1,2})(?![\d,])""")

        private val pack = Regex("""(?:\b(\d)\s*x\s*)?\b(\d{1,2})er-(Tray|Träger|Kiste|Karton|Pack|Packung)\s+(\d{1,3}),\s?(\d{2})(?!\d)""")

        private val stopWords = setOf(
            "versch", "sorten", "oder", "und", "mit", "spar", "bio", "natur", "pur", "premium", "enjoy", "budget",
            "der", "die", "das", "fur", "von", "aus", "neu", "jetzt", "per", "liter", "classic", "original",
            "thisisnobrand", "gourmet",
        )

        private class Block(val start: Int, val end: Int, val regulars: List<Double>, val word: String, val n: Int, val unit: String, val deal: Double?)
        private class Label(val pos: Int, val buy: Int, val free: Int)
        private class Pack(val pos: Int, val quantity: Int, val price: Double, val text: String)

        private class Page(val page: FlyerPage, val text: String) {
            val blocks = block.findAll(text).map { m ->
                Block(
                    start = m.range.first, end = m.range.last + 1,
                    regulars = m.groupValues[2].split('/').mapNotNull { money(it) },
                    word = m.groupValues[3].lowercase(), n = m.groupValues[4].toInt(), unit = m.groupValues[5],
                    deal = m.groups[6]?.let { money("${it.value},${m.groupValues[7]}") },
                )
            }.toList()
            val labels = label.findAll(text).map { Label(it.range.first, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
                .filter { it.buy >= 1 && it.free >= 1 }.toList()
            val packs = pack.findAll(text).map { m ->
                val count = m.groupValues[1].toIntOrNull()
                Pack(
                    pos = m.range.first,
                    quantity = (count ?: 1) * m.groupValues[2].toInt(),
                    price = money("${m.groupValues[4]},${m.groupValues[5]}") ?: 0.0,
                    text = "${count?.let { "$it x " }.orEmpty()}${m.groupValues[2]}er-${m.groupValues[3]}",
                )
            }.toList()
        }

        private class Match(
            val page: Page, val block: Block, val label: Label?, val score: Int, val sameBanner: Boolean,
            /** The price the block was matched for, and the normal price. */
            val price: Double, val regular: Double,
        )

        private fun money(s: String): Double? = s.replace(Regex("\\s"), "").replace(',', '.').toDoubleOrNull()

        private fun offerTokens(o: Offer): List<String> =
            Text.tokens("${o.brand.orEmpty()} ${o.name}").filter { it.length > 2 && it !in stopWords }.distinct()

        private fun hasQuantityCondition(o: Offer) =
            o.conditions.any { (it.type == ConditionType.MULTI_BUY || it.type == ConditionType.MIN_QUANTITY) && !it.inferred }

        /** The offer price printed somewhere around the block. */
        private fun priceNear(text: String, b: Block, price: Double): Boolean =
            Regex(printed(price)).containsMatchIn(text.substring(max(0, b.start - 300), min(text.length, b.end + 300)))

        /** Adds the multi-buy / minimum quantity printed in the SPAR-group flyers to the SPAR offers that lack it. */
        fun enrichAll(offers: List<Offer>, pages: List<FlyerPage>): List<Offer> {
            if (pages.isEmpty()) return offers
            val parsed = pages.map { p -> Page(p, p.lines.joinToString(" ").replace(Regex("\\s+"), " ")) }
            return offers.map { o -> if (o.chain == Chain.SPAR && !hasQuantityCondition(o)) enrich(o, parsed) ?: o else o }
        }

        private fun enrich(o: Offer, pages: List<Page>): Offer? {
            val tokens = offerTokens(o).ifEmpty { return null }
            best(o, o.price, tokens, pages)?.let { return apply(o, it) }
            // "Mit SPAR-App-Gutschein -50%": the headline is the voucher price, the multi-buy applies without the voucher.
            for (tier in o.tiers.filter { it.kind == TierKind.WITHOUT }) {
                best(o, tier.price, tokens, pages)?.let { return applyWithout(o, it, tier) }
            }
            // A guessed "2+1 gratis?" that the flyer prints as a plain reduction ("statt 7,49 4,99").
            if (o.conditions.any { it.inferred && it.type == ConditionType.MULTI_BUY } && plainReduction(o, tokens, pages)) {
                return o.copy(conditions = o.conditions.filterNot { it.inferred && it.type == ConditionType.MULTI_BUY })
            }
            return null
        }

        /** Price block for [price], with the product named close by. */
        private fun best(o: Offer, price: Double, tokens: List<String>, pages: List<Page>): Match? {
            var best: Match? = null
            for (p in pages) {
                for (b in p.blocks) {
                    // Offers without a normal price are matched by their promo price after "je" instead.
                    val regular = o.regularPrice?.takeIf { r -> b.regulars.any { abs(it - r) < 0.005 } }
                        ?: b.regulars.firstOrNull()?.takeIf { o.regularPrice == null && b.deal != null && abs(b.deal - price) < 0.005 }
                        ?: continue
                    if (regular <= price) continue
                    val label = p.labels
                        .filter { abs(it.pos - b.start) <= 300 && it.buy + it.free == b.n && abs(price * b.n - regular * it.buy) <= 0.012 * b.n }
                        .minByOrNull { abs(it.pos - b.start) }
                    val priceFits = if (b.deal != null) abs(b.deal - price) < 0.005 else label != null || priceNear(p.text, b, price)
                    if (!priceFits || (o.regularPrice == null && label == null)) continue
                    // Two-column layouts put neighbours next to each other - the product itself must be named close by.
                    val score = nameScore(p.text, b.start - 350, b.end + 150, tokens)
                    if (score == 0) continue
                    val m = Match(p, b, label, score, p.page.banner in o.banners, price, regular)
                    if (best == null || better(m, best)) best = m
                }
            }
            return best
        }

        private fun nameScore(text: String, from: Int, to: Int, tokens: List<String>): Int {
            val window = " " + Text.fold(text.substring(max(0, from), min(text.length, to))) + " "
            return tokens.count { window.contains(" $it") }
        }

        private fun plainReduction(o: Offer, tokens: List<String>, pages: List<Page>): Boolean {
            val regular = o.regularPrice ?: return false
            val pattern = Regex("""statt\s+${printed(regular)}\s+${printed(o.price)}""")
            return pages.any { p -> pattern.findAll(p.text).any { nameScore(p.text, it.range.first - 350, it.range.last + 150, tokens) > 0 } }
        }

        /** "4,99" or INTERSPAR's "4 99". */
        private fun printed(price: Double): String {
            val cents = (price * 100).roundToInt()
            return """(?<![\d,])${cents / 100}[,\s]\s?${"%02d".format(cents % 100)}(?!\d)"""
        }

        private fun better(a: Match, b: Match): Boolean = compareValuesBy(
            a, b, { it.score }, { it.label != null }, { it.sameBanner },
        ) > 0

        /** "ab 6 Fl." / "bei 24 Ds." with the printed pack price, if one fits. */
        private fun minQuantity(m: Match): Condition? {
            val b = m.block
            val pack = m.page.packs
                .filter { abs(it.pos - b.start) <= 400 && it.quantity % b.n == 0 && abs(it.price - it.quantity * m.price) <= 0.011 * it.quantity }
                .minByOrNull { abs(it.pos - b.start) }
            return Conditions.minQuantity("${b.word} ${b.n} ${b.unit}", perSet = m.label != null || b.word == "bei")?.let { c ->
                if (pack != null && pack.quantity == b.n) c.copy(args = c.args + mapOf("pack" to pack.text, "packPrice" to "${pack.price}")) else c
            }
        }

        private fun apply(o: Offer, m: Match): Offer? {
            val minQty = minQuantity(m) ?: return null
            val multi = m.label?.let { Conditions.multiBuyCondition(it.buy, it.free).copy(unit = minQty.unit) }
            val tiers = if (o.tiers.none { it.kind == TierKind.SINGLE }) {
                listOf(PriceTier(TierKind.SINGLE, m.regular, unit = minQty.unit)) + o.tiers
            } else o.tiers
            return o.copy(
                regularPrice = m.regular,
                discountPercent = o.discountPercent ?: ((1 - o.price / m.regular) * 100).roundToInt().takeIf { it in 1..95 },
                conditions = Conditions.merge(
                    o.conditions.filterNot { it.inferred && it.type == ConditionType.MULTI_BUY },
                    listOfNotNull(multi, minQty),
                ),
                tiers = tiers,
                sources = (o.sources + SourceId.SPAR_FLYER).distinct(),
            )
        }

        /** The multi-buy belongs to the price without voucher/app - kept as a note so it does not apply to the headline. */
        private fun applyWithout(o: Offer, m: Match, tier: PriceTier): Offer? {
            val minQty = minQuantity(m) ?: return null
            val note = Conditions.note(
                CondKey.WITHOUT_DEAL,
                minQty.args + mapOf("price" to "${m.price}", "program" to tier.program.orEmpty()) +
                    (m.label?.let { mapOf("buy" to "${it.buy}", "free" to "${it.free}") } ?: emptyMap()),
            ).copy(quantity = minQty.quantity, unit = minQty.unit)
            return o.copy(
                conditions = Conditions.merge(o.conditions.filterNot { it.inferred && it.type == ConditionType.MULTI_BUY }, listOf(note)),
                sources = (o.sources + SourceId.SPAR_FLYER).distinct(),
            )
        }
    }
}
