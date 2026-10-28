package at.rabattradar.core.parse

import at.rabattradar.core.i18n.Lang
import at.rabattradar.core.i18n.Texts
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.CondKey
import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.text.Text
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Extracts caveats ("2+1 gratis", "ab 2 Pkg. je", "Lidl Plus", ...) from German offer texts. */
object Conditions {

    private val multiBuy = Regex("""(?<![\d.,])(\d{1,2})\s*\+\s*(\d{1,2})(?!\s*(?:g|kg|ml|l|%|wg)\b)\s*(?:gratis|free|geschenkt)""", RegexOption.IGNORE_CASE)
    private val billaTag = Regex("""pt-(\d+)plus(\d+)""")
    private val minQty = Regex(
        """(?:mengenvorteil\s+)?\b(ab|bei)\s+(\d{1,2})\s+([A-Za-zÄÖÜäöüß][\wÄÖÜäöüß-]*\.?|\d+er-[\wÄÖÜäöüß-]+\.?)(?:\s+(je))?""",
        RegexOption.IGNORE_CASE,
    )
    private val weight = Regex("""\b(per|je|pro)\s*(kg|kilo|kilogramm)\b""", RegexOption.IGNORE_CASE)
    private val variants = Regex("""\b(versch\.?|verschiedene|div\.?|diverse)\s+sorten\b""", RegexOption.IGNORE_CASE)
    private val onlineOnly = Regex("""nur\s+online|online\s+verf[üu]gbar|nur\s+im\s+online""", RegexOption.IGNORE_CASE)
    private val lidlPlus = Regex("""lidl\s*plus""", RegexOption.IGNORE_CASE)
    private val sparApp = Regex("""spar[\s-]*app""", RegexOption.IGNORE_CASE)
    private val pennyApp = Regex("""penny[\s-]*app""", RegexOption.IGNORE_CASE)
    private val billaApp = Regex("""billa[\s-]*app""", RegexOption.IGNORE_CASE)
    private val joe = Regex("""\bj[öo]e?[\s-]*(karte|bonus|club)|(?<![\wäöü])jö(?![\wäöü])""", RegexOption.IGNORE_CASE)

    /** "HINWEIS: MIT LIDL PLUS APP 0.49 €" / "HINWEIS: NUR ONLINE VERFÜGBAR" as used by marktguru. */
    private val hint = Regex("""HINWEIS:\s*(MIT\s+[A-ZÄÖÜ][A-ZÄÖÜ +\-]*(?:APP|KARTE|GUTSCHEIN))(?:\s+(\d+[.,]\d{2})\s*€)?|HINWEIS:\s*(NUR ONLINE VERFÜGBAR)""")

    data class Hint(val program: String, val price: Double?)

    /** Pulls marktguru "HINWEIS:" blocks out of a description. */
    fun extractHints(description: String?): Pair<String?, List<Hint>> {
        if (description.isNullOrBlank()) return null to emptyList()
        val hints = hint.findAll(description).map { m ->
            val program = (m.groups[1]?.value ?: m.groups[3]?.value ?: "").trim()
            Hint(program, m.groups[2]?.value?.let(Text::parsePrice))
        }.toList()
        val rest = Text.clean(hint.replace(description, " "))
        return rest to hints
    }

    fun multiBuy(text: String): Condition? {
        val m = multiBuy.find(text) ?: return null
        return multiBuyCondition(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    fun multiBuyFromTag(tag: String): Condition? =
        billaTag.find(tag)?.let { multiBuyCondition(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }

    fun multiBuyCondition(buy: Int, free: Int) = Condition(
        type = ConditionType.MULTI_BUY,
        key = CondKey.MULTI_BUY,
        quantity = buy + free,
        args = mapOf("buy" to "$buy", "free" to "$free"),
    )

    /**
     * "Bei 3 Packungen je 1.86" with a single price of 2.79 is mathematically a 2+1 offer even when the
     * shop does not tag it as such: 3 x 1.86 = 2 x 2.79.
     */
    fun multiBuyFromSet(quantity: Int, promoPrice: Double, singlePrice: Double): Condition? {
        if (quantity < 2 || singlePrice <= promoPrice) return null
        for (free in 1 until quantity) {
            if (abs(promoPrice * quantity - singlePrice * (quantity - free)) <= 0.011 * quantity) {
                return multiBuyCondition(quantity - free, free)
            }
        }
        return null
    }

    /** "Helles*" - an asterisk points to a footnote in the printed flyer. */
    fun footnote(name: String): Condition? =
        if (name.contains('*')) {
            Condition(ConditionType.NOTE, CondKey.FOOTNOTE)
        } else null

    /**
     * Minimum quantity from texts like "Bei 3 Tafeln je", "ab 2 Pkg. je", "Mengenvorteil ab 2 Stk. je",
     * "ab 2 6er-Träger je nur 4,14". [perSet] means the price only applies to complete sets.
     */
    fun minQuantity(text: String, perSet: Boolean? = null): Condition? {
        for (m in minQty.findAll(text)) {
            val n = m.groupValues[2].toIntOrNull() ?: continue
            val rawUnit = m.groupValues[3]
            val unitWord = rawUnit.trimEnd('.').lowercase()
            if (n < 2) continue
            if (unitWord in setOf("kilo", "kg", "gramm", "g", "liter", "l", "ml", "jahren", "jahre", "monaten", "uhr", "personen", "teile", "°c")) continue
            // "ab 3.99" etc. never reaches here (needs a unit word); "bei 30 °C" filtered above.
            val word = m.groupValues[1].lowercase()
            val set = perSet ?: (word == "bei")
            return Condition(
                ConditionType.MIN_QUANTITY, CondKey.MIN_QUANTITY, quantity = n, unit = singularUnit(rawUnit),
                args = mapOf("set" to "$set", "printed" to pluralShort(rawUnit, n)),
            )
        }
        return null
    }

    private val unitMap = mapOf(
        "packungen" to "Pkg.", "packung" to "Pkg.", "pkg" to "Pkg.", "pk" to "Pkg.", "pckg" to "Pkg.",
        "tafeln" to "Tafel", "tafel" to "Tafel",
        "flaschen" to "Fl.", "flasche" to "Fl.", "fl" to "Fl.",
        "dosen" to "Dose", "dose" to "Dose", "ds" to "Dose",
        "stk" to "Stk.", "stück" to "Stk.", "stueck" to "Stk.", "st" to "Stk.",
        "becher" to "Becher", "beutel" to "Beutel", "gläser" to "Glas", "glas" to "Glas",
        "kartons" to "Karton", "karton" to "Karton", "tuben" to "Tube", "tube" to "Tube",
        "rollen" to "Rolle", "rolle" to "Rolle", "briefe" to "Brief", "brief" to "Brief",
        "boxen" to "Box", "box" to "Box", "pakete" to "Paket", "paket" to "Paket",
        "riegel" to "Riegel", "tassen" to "Tasse", "blister" to "Blister", "netze" to "Netz",
        "kisten" to "Kiste", "kiste" to "Kiste", "tray" to "Tray", "trays" to "Tray", "waschgang" to "WG",
        "gl" to "Glas", "tfl" to "Tafel", "be" to "Becher", "träger" to "Träger",
    )

    private val packUnit = Regex("\\d+er-.*")

    fun singularUnit(raw: String): String {
        val w = raw.trimEnd('.').lowercase()
        unitMap[w]?.let { return it }
        if (packUnit.matches(w)) return raw.trimEnd('.').substringAfter('-').replaceFirstChar { it.uppercase() }
        return raw.trimEnd('.').replaceFirstChar { it.uppercase() }
    }

    /** The unit as printed ("Tafeln", "6er-Träger"), with the usual flyer abbreviations ("Pkg.", "Stk.", "Fl."). */
    private fun pluralShort(raw: String, n: Int): String {
        if (n == 1) return singularUnit(raw)
        val abbreviated = unitMap[raw.trimEnd('.').lowercase()]
        return if (abbreviated == "Pkg." || abbreviated == "Stk." || abbreviated == "Fl.") abbreviated else raw.replaceFirstChar { it.uppercase() }
    }

    /** Loyalty / app requirements mentioned in a text. */
    fun membership(text: String, chain: Chain): List<Condition> {
        val out = mutableListOf<Condition>()
        if (lidlPlus.containsMatchIn(text)) out += lidlPlusCondition()
        if (sparApp.containsMatchIn(text)) out += sparAppCondition()
        if (pennyApp.containsMatchIn(text)) out += pennyAppCondition()
        if (billaApp.containsMatchIn(text)) out += Condition(ConditionType.APP_COUPON, CondKey.BILLA_APP)
        if (joe.containsMatchIn(text)) out += joeCondition(chain)
        return out
    }

    private fun priceArgs(price: Double?) = price?.let { mapOf("price" to "$it") }.orEmpty()

    fun lidlPlusCondition(price: Double? = null, exclusive: Boolean = false, highlight: String? = null) = Condition(
        ConditionType.LOYALTY_APP, CondKey.LIDL_PLUS,
        args = priceArgs(price) + (if (exclusive) mapOf("exclusive" to "true") else emptyMap()) +
            (highlight?.let { mapOf("highlight" to it) } ?: emptyMap()),
    )

    fun sparAppCondition(price: Double? = null) = Condition(ConditionType.APP_COUPON, CondKey.SPAR_APP, args = priceArgs(price))

    fun pennyAppCondition(price: Double? = null) = Condition(ConditionType.LOYALTY_APP, CondKey.PENNY_APP, args = priceArgs(price))

    fun joeCondition(chain: Chain, price: Double? = null) =
        Condition(ConditionType.LOYALTY_CARD, CondKey.JOE, args = priceArgs(price) + ("chain" to chain.name))

    fun onlineOnly(): Condition = Condition(ConditionType.ONLINE_ONLY, CondKey.ONLINE_ONLY)

    fun onlineOnly(text: String): Condition? = if (onlineOnly.containsMatchIn(text)) onlineOnly() else null

    fun weightPrice(): Condition = Condition(ConditionType.WEIGHT_PRICE, CondKey.WEIGHT_PRICE)

    fun weightPrice(text: String): Condition? = if (weight.containsMatchIn(text)) weightPrice() else null

    fun variants(text: String): Condition? =
        if (variants.containsMatchIn(text)) Condition(ConditionType.VARIANTS, CondKey.VARIANTS) else null

    fun onlineShop(store: String) = Condition(ConditionType.ONLINE_SHOP, CondKey.ONLINE_SHOP, args = mapOf("store" to store))

    fun noDates(store: String) = Condition(ConditionType.NO_DATES, CondKey.NO_DATES, args = mapOf("store" to store))

    fun note(key: CondKey, args: Map<String, String> = emptyMap(), inferred: Boolean = false) =
        Condition(ConditionType.NOTE, key, inferred = inferred, args = args)

    /** All text-derivable conditions. */
    fun fromText(text: String, chain: Chain): List<Condition> = listOfNotNull(
        multiBuy(text),
        minQuantity(text),
        onlineOnly(text),
        weightPrice(text),
        variants(text),
    ) + membership(text, chain)

    /**
     * marktguru drops multi-buy conditions: an offer printed as "1 Pkg. 0,99 – bei 3 Pkg. je 0,66 – 2+1 gratis"
     * shows up as plain price 0.66 / old 0.99. A price of exactly 2/3 of the normal price is a strong hint.
     */
    fun inferMultiBuy(price: Double, regular: Double?): Condition? {
        if (regular == null || regular <= price) return null
        if (abs(regular * 2.0 / 3.0 - price) <= 0.011) {
            return Condition(
                ConditionType.MULTI_BUY, CondKey.MULTI_BUY, inferred = true, quantity = 3,
                args = mapOf("buy" to "2", "free" to "1", "regular" to "$regular"),
            )
        }
        return null
    }

    fun dayShort(d: LocalDate) = Texts.dayShort(d, Lang.DE)
    fun dayLong(d: LocalDate) = Texts.dayLong(d, Lang.DE)

    /** Short promotions (1-3 days) are flagged, e.g. "nur Fr & Sa". */
    fun weekdays(from: LocalDate?, to: LocalDate?): Condition? {
        if (from == null || to == null || to.isBefore(from)) return null
        val days = ChronoUnit.DAYS.between(from, to) + 1
        if (days > 3) return null
        return Condition(ConditionType.WEEKDAYS, CondKey.WEEKDAYS, args = mapOf("from" to "$from", "to" to "$to"))
    }

    fun fmt(d: LocalDate) = Texts.date(d)

    /** Keeps the first condition per type (inferred ones only if nothing explicit exists). */
    fun merge(vararg lists: List<Condition>): List<Condition> {
        val all = lists.flatMap { it }
        val out = LinkedHashMap<String, Condition>()
        for (c in all.sortedBy { it.inferred }) {
            val key = when (c.type) {
                ConditionType.LOYALTY_CARD, ConditionType.LOYALTY_APP, ConditionType.APP_COUPON -> "${c.type}:${c.key}"
                else -> c.type.name
            }
            if (c.inferred && (c.type == ConditionType.MULTI_BUY || c.type == ConditionType.MIN_QUANTITY) &&
                out.values.any { !it.inferred && (it.type == ConditionType.MULTI_BUY || it.type == ConditionType.MIN_QUANTITY) }
            ) continue
            out.putIfAbsent(key, c)
        }
        return out.values.sortedBy { order.indexOf(it.type) }
    }

    private val order = listOf(
        ConditionType.LOYALTY_APP, ConditionType.LOYALTY_CARD, ConditionType.APP_COUPON,
        ConditionType.MULTI_BUY, ConditionType.MIN_QUANTITY, ConditionType.WEEKDAYS, ConditionType.ONLINE_ONLY,
        ConditionType.CLEARANCE, ConditionType.WEIGHT_PRICE, ConditionType.VARIANTS, ConditionType.DEPOSIT,
        ConditionType.ONLINE_SHOP, ConditionType.NO_DATES, ConditionType.NOTE,
    )
}
