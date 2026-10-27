package at.rabattradar.core.parse

import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.text.Text
import at.rabattradar.core.text.euro
import java.time.DayOfWeek
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
    private val deposit = Regex("""\b(pfand|mehrweg|einweg)""", RegexOption.IGNORE_CASE)
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
        label = "$buy+$free gratis",
        detail = "Beim Kauf von ${buy + free} Stück sind $free gratis. Der Aktionspreis je Stück gilt nur, wenn ${buy + free} (oder ein Vielfaches) gekauft werden.",
        quantity = buy + free,
    )

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
            val unit = singularUnit(rawUnit)
            val label = if (set) "bei $n ${pluralShort(rawUnit, n)}" else "ab $n ${pluralShort(rawUnit, n)}"
            val detail = if (set) {
                "Aktionspreis je $unit gilt nur beim Kauf von $n ${pluralShort(rawUnit, n)} (bzw. einem Vielfachen davon). Einzeln gilt der Normalpreis."
            } else {
                "Aktionspreis je $unit gilt ab dem Kauf von $n ${pluralShort(rawUnit, n)}. Einzeln gilt der Normalpreis."
            }
            return Condition(ConditionType.MIN_QUANTITY, label, detail, quantity = n, unit = unit)
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
        "kisten" to "Kiste", "tray" to "Tray", "trays" to "Tray", "waschgang" to "WG",
    )

    fun singularUnit(raw: String): String {
        val w = raw.trimEnd('.').lowercase()
        unitMap[w]?.let { return it }
        if (Regex("\\d+er-.*").matches(w)) return raw.trimEnd('.').substringAfter('-').replaceFirstChar { it.uppercase() }
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
        if (billaApp.containsMatchIn(text)) out += Condition(ConditionType.APP_COUPON, "BILLA App", "Preis nur mit Coupon aus der BILLA App.")
        if (joe.containsMatchIn(text)) out += joeCondition(chain)
        return out
    }

    fun lidlPlusCondition(price: Double? = null) = Condition(
        ConditionType.LOYALTY_APP, "Lidl Plus",
        "Preis nur mit der Lidl Plus App (Coupon in der App aktivieren und an der Kassa scannen)." +
            (price?.let { " Mit Lidl Plus: ${it.euro()} €." } ?: ""),
    )

    fun sparAppCondition(price: Double? = null) = Condition(
        ConditionType.APP_COUPON, "SPAR App",
        "Preis nur mit Gutschein aus der SPAR App (Gutschein vorher aktivieren)." + (price?.let { " Mit Gutschein: ${it.euro()} €." } ?: ""),
    )

    fun pennyAppCondition(price: Double? = null) = Condition(
        ConditionType.LOYALTY_APP, "PENNY App",
        "Preis nur mit der PENNY App bzw. jö Karte." + (price?.let { " Mit App/jö: ${it.euro()} €." } ?: ""),
    )

    fun joeCondition(chain: Chain, price: Double? = null) = Condition(
        ConditionType.LOYALTY_CARD, "jö",
        "Preis nur für Mitglieder des jö Bonus Clubs (jö Karte oder jö App an der Kassa vorzeigen)" +
            (if (chain == Chain.PENNY) " bzw. mit der PENNY App." else ".") + (price?.let { " jö-Preis: ${it.euro()} €." } ?: ""),
    )

    fun onlineOnly(text: String): Condition? =
        if (onlineOnly.containsMatchIn(text)) Condition(ConditionType.ONLINE_ONLY, "nur online", "Nur im Online-Shop erhältlich, nicht in der Filiale.") else null

    fun weightPrice(text: String): Condition? =
        if (weight.containsMatchIn(text)) Condition(ConditionType.WEIGHT_PRICE, "Preis pro kg", "Preis gilt pro Kilogramm – der Endpreis hängt vom Gewicht ab.") else null

    fun variants(text: String): Condition? =
        if (variants.containsMatchIn(text)) Condition(ConditionType.VARIANTS, "versch. Sorten", "Der Preis gilt für mehrere Sorten/Varianten – nicht jede Sorte ist in jeder Filiale erhältlich.") else null

    fun deposit(text: String): Condition? =
        if (deposit.containsMatchIn(text)) Condition(ConditionType.DEPOSIT, "+ Pfand", "Zuzüglich Pfand (wird an der Kassa verrechnet).") else null

    /** All text-derivable conditions. */
    fun fromText(text: String, chain: Chain): List<Condition> = listOfNotNull(
        multiBuy(text),
        minQuantity(text),
        onlineOnly(text),
        weightPrice(text),
        variants(text),
        deposit(text),
    ) + membership(text, chain)

    /**
     * marktguru drops multi-buy conditions: an offer printed as "1 Pkg. 0,99 – bei 3 Pkg. je 0,66 – 2+1 gratis"
     * shows up as plain price 0.66 / old 0.99. A price of exactly 2/3 of the normal price is a strong hint.
     */
    fun inferMultiBuy(price: Double, regular: Double?): Condition? {
        if (regular == null || regular <= price) return null
        if (abs(regular * 2.0 / 3.0 - price) <= 0.011) {
            return Condition(
                ConditionType.MULTI_BUY, "2+1 gratis?",
                "Der Preis entspricht genau 2/3 des Normalpreises (${regular.euro()} €). Im Flugblatt steht dann meist „2+1 gratis“ – " +
                    "der Preis gilt nur beim Kauf von 3 Stück. Die Datenquelle nennt die Bedingung nicht, bitte im Flugblatt prüfen.",
                inferred = true, quantity = 3,
            )
        }
        return null
    }

    private val dayShort = mapOf(
        DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Di", DayOfWeek.WEDNESDAY to "Mi", DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa", DayOfWeek.SUNDAY to "So",
    )
    private val dayLong = mapOf(
        DayOfWeek.MONDAY to "Montag", DayOfWeek.TUESDAY to "Dienstag", DayOfWeek.WEDNESDAY to "Mittwoch", DayOfWeek.THURSDAY to "Donnerstag",
        DayOfWeek.FRIDAY to "Freitag", DayOfWeek.SATURDAY to "Samstag", DayOfWeek.SUNDAY to "Sonntag",
    )

    fun dayShort(d: LocalDate) = dayShort.getValue(d.dayOfWeek)
    fun dayLong(d: LocalDate) = dayLong.getValue(d.dayOfWeek)

    /** Short promotions (1-3 days) are flagged, e.g. "nur Fr & Sa". */
    fun weekdays(from: LocalDate?, to: LocalDate?): Condition? {
        if (from == null || to == null || to.isBefore(from)) return null
        val days = ChronoUnit.DAYS.between(from, to) + 1
        if (days > 3) return null
        val label = when (days) {
            1L -> "nur ${dayShort(from)}"
            2L -> "nur ${dayShort(from)} & ${dayShort(to)}"
            else -> "nur ${dayShort(from)}–${dayShort(to)}"
        }
        val detail = if (days == 1L) {
            "Aktion gilt nur am ${dayLong(from)}, ${fmt(from)}."
        } else {
            "Aktion gilt nur von ${dayLong(from)}, ${fmt(from)} bis ${dayLong(to)}, ${fmt(to)}."
        }
        return Condition(ConditionType.WEEKDAYS, label, detail)
    }

    fun fmt(d: LocalDate) = "%02d.%02d.".format(d.dayOfMonth, d.monthValue)

    /** Keeps the first condition per type (inferred ones only if nothing explicit exists). */
    fun merge(vararg lists: List<Condition>): List<Condition> {
        val all = lists.flatMap { it }
        val out = LinkedHashMap<String, Condition>()
        for (c in all.sortedBy { it.inferred }) {
            val key = when (c.type) {
                ConditionType.LOYALTY_CARD, ConditionType.LOYALTY_APP, ConditionType.APP_COUPON -> "${c.type}:${c.label}"
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
