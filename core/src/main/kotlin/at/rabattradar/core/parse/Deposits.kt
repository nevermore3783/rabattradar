package at.rabattradar.core.parse

import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Deposit
import at.rabattradar.core.model.DepositKind
import at.rabattradar.core.text.euro
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Austrian deposit rules (Pfandverordnung, since 1 Jan 2025):
 *  - Einwegpfand: flat 0.25 € per plastic bottle or metal can with 0.1–3 l.
 *  - Exempt: milk and dairy drinks, beverage cartons (Tetra/Elopak), glass one-way bottles.
 *  - Mehrweg (reusable glass, crates) has its own, product-specific deposit.
 *
 * Sources state deposits in very different ways ("zzgl. Pfand 8 x € 0,25 = € 2", "EINWEG mit Pfand",
 * a `depositPrice` field, or nothing at all), so explicit statements win and everything else is inferred
 * from the container, the drink type and the volume.
 */
object Deposits {
    const val SINGLE_USE_RATE = 0.25

    private val ic = setOf(RegexOption.IGNORE_CASE)
    private val noDeposit = Regex("""pfandfrei|ohne\s+pfand""", ic)
    private val countTimesAmount = Regex("""pfand\s*(\d{1,2})\s*[x×]\s*€?\s*(\d+[.,]\d{2})""", ic)
    // The currency sign is required: "Preis exkl. Pfand Je 0,33 l" is a volume, not an amount.
    private val amountAfter = Regex("""pfand\s*(?:von\s*|je\s*)?(?:€|eur)\s*(\d+[.,]\d{2})""", ic)
    private val amountBefore = Regex("""(\d+[.,]\d{2})\s*(?:€|eur)\s*pfand""", ic)
    private val energy = Regex("""energy|red\s*bull|monster|\bpower\b""", ic)
    private val withDeposit = Regex("""mit\s+pfand|exkl\.?\s*pfand|zzgl\.?\s*pfand|zuzüglich\s+pfand|plus\s+pfand""", ic)
    private val nonFood = Regex(
        """\d\s*watt\b|\bwatt\b|kocher|maschine|bügeleisen|reiniger|spray|shampoo|duschgel|spülung|mundspül|waschmittel|weichspüler|""" +
            """wassertank|fassungsvolumen|\bakku|lampe|kanne|karaffe|trinkflasche|thermos|becher\b""",
        ic,
    )
    private val reusable = Regex("""mehrweg|\bMW\b""", ic)
    private val dairy = Regex(
        """milch|joghurt|jogurt|kakao|kefir|buttermilch|molke|actimel|yakult|\blatte\b|cappuccino|eiskaffee|skyr|ayran|lassi""", ic,
    )
    private val carton = Regex("""packung|karton|elopak|tetra|beutel|pouch|kiste|\bfass\b|partyfass|kanister|bag.in.box""", ic)
    private val can = Regex("""\bdosen?\b|\bds\b\.?|\bcan\b|-dose""", ic)
    private val pet = Regex("""\bpet\b|kunststoff|plastik""", ic)
    private val bottle = Regex("""flasche|\bfl\b\.?""", ic)
    private val glass = Regex("""\bglas""", ic)
    private val beer = Regex(
        """bier|märzen|maerzen|\blager\b|pils|zwickl|weizen|weißbier|weissbier|radler|stout|\bipa\b|cider|bräu|\bhell(es)?\b|edelstoff|export""", ic,
    )
    private val wineSpirits = Regex(
        """wein|sekt|prosecco|frizzante|spritzer|champagner|vodka|wodka|\brum\b|\bgin\b|whisk|likör|likoer|schnaps|spirituose|aperol|sirup|""" +
            """veltliner|riesling|sauvignon|chardonnay|pinot|morillon|zweigelt|blaufränkisch|merlot|cabernet|muskateller|rosé|""" +
            """\bdoc\b|\bdocg\b|\bdac\b|\baoc\b|vinho|chianti|primitivo|rioja|brandy|limoncello|bitter\b(?!\s*lemon)|aperitiv|bellini|""" +
            // A vintage year ("Riesling 2025"), but not "2000 ml".
            """\b(199\d|20[0-3]\d)\b(?!\s*(ml|g|l|w|watt|mah)\b)""",
        ic,
    )
    private val softDrink = Regex(
        """cola|fanta|sprite|pepsi|limo|almdudler|frucade|schartner|eistee|ice\s*tea|icetea|energy|red\s*bull|monster|mineral|""" +
            """vöslauer|voeslauer|römerquelle|roemerquelle|gasteiner|juvina|saft|nektar|smoothie|tonic|ginger|bitter\s*lemon|spezi|""" +
            """\bmate\b|kombucha|isotoni|sportgetränk|softdrink|soda|schorle|kracherl|7up|schweppes|rauch|pfanner|hohes\s*c|""" +
            """\bwasser\b|\bwater\b|dreh\s*und\s*trink""",
        ic,
    )
    private val multi = Regex("""(\d{1,2})\s*[x×]\s*(\d+(?:[.,]\d+)?)\s*-?\s*(ml|cl|l|lt|liter|litre)\b""", ic)
    private val volume = Regex("""(\d+(?:[.,]\d+)?)\s*-?\s*(ml|cl|l|lt|liter|litre)\b""", ic)
    private val multiNoUnit = Regex("""(\d{1,2})\s*[x×]\s*\d+[.,]\d+""", ic)
    private val pack = Regex("""(\d{1,2})er[\s-]*(?:pack|packung|träger|traeger|tray|kiste|karton)""", ic)

    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

    /** Volume of one container in litres and the number of containers, e.g. "6 x 0,5 l" -> (0.5, 6). */
    fun containers(text: String): Pair<Double?, Int> {
        multi.find(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            return toLitres(num(m.groupValues[2]), m.groupValues[3]) to n.coerceAtLeast(1)
        }
        val v = volume.find(text)?.let { toLitres(num(it.groupValues[1]), it.groupValues[2]) }
        // "Gösser Naturradler 6x0,5" (no unit) or "6er-Träger".
        val n = multiNoUnit.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: pack.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        return v to n
    }

    private fun toLitres(value: Double?, unit: String): Double? {
        value ?: return null
        return when (unit.lowercase()) {
            // Some shops label "0.25 ml" when they mean litres.
            "ml" -> if (value < 1) value else value / 1000
            "cl" -> value / 100
            else -> value
        }
    }

    private fun kindOf(amountPerContainer: Double) =
        if (abs(amountPerContainer - SINGLE_USE_RATE) < 0.001) DepositKind.SINGLE_USE else DepositKind.REUSABLE

    /**
     * @param text name, description, size, container and category of the offer
     * @param statedKind what the shop says about the packaging (PENNY: ONEWAY/REUSABLE), if anything
     */
    fun detect(text: String, statedKind: DepositKind? = null): Deposit? {
        if (noDeposit.containsMatchIn(text)) return null
        val (litres, count) = containers(text)

        countTimesAmount.find(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            val a = num(m.groupValues[2]) ?: return@let
            return Deposit(kindOf(a), a * n, n)
        }
        (amountAfter.find(text) ?: amountBefore.find(text))?.let { m ->
            val a = num(m.groupValues[1]) ?: return@let
            // "zzgl. Pfand € 0,25" is per container; a larger amount is already the total.
            return if (a <= 0.30) Deposit(kindOf(a), a * count, count) else Deposit(DepositKind.REUSABLE, a, count)
        }

        if (statedKind == DepositKind.REUSABLE || reusable.containsMatchIn(text)) return Deposit(DepositKind.REUSABLE, null, count)

        if (nonFood.containsMatchIn(text)) return null
        if (withDeposit.containsMatchIn(text)) {
            // "Preis exkl. Pfand" / "EINWEG mit Pfand": a deposit is certain; glass bottles mean Mehrweg.
            val glassy = glass.containsMatchIn(text) || (beer.containsMatchIn(text) && bottle.containsMatchIn(text))
            return if (glassy) Deposit(DepositKind.REUSABLE, null, count) else Deposit(DepositKind.SINGLE_USE, SINGLE_USE_RATE * count, count)
        }

        // --- Inference for single-use containers ---
        if (litres == null || litres < 0.1 || litres > 3.0) return null
        if (dairy.containsMatchIn(text) || carton.containsMatchIn(text)) return null
        val isBeer = beer.containsMatchIn(text)
        val isWine = wineSpirits.containsMatchIn(text)
        val isSoft = softDrink.containsMatchIn(text)
        if (!isBeer && !isSoft && !isWine) return null
        // A named can or PET bottle is covered by law, so only the other cases are guesses.
        val certain = statedKind == DepositKind.SINGLE_USE || can.containsMatchIn(text) || pet.containsMatchIn(text)
        val eligible = when {
            can.containsMatchIn(text) -> !isWine || isSoft
            pet.containsMatchIn(text) -> true
            glass.containsMatchIn(text) -> false
            // Beer, wine and spirits in "Flaschen" are glass in Austria (one-way glass has no deposit);
            // small soft drink bottles (0.2 l tonic) are usually glass too.
            bottle.containsMatchIn(text) -> isSoft && !isBeer && !isWine && litres > 0.25
            // No container named: soft drinks come in PET or cans, except small juices (cartons) - energy drinks are cans.
            // Beer could be a crate or glass.
            else -> isSoft && !isBeer && !isWine && (litres > 0.25 || energy.containsMatchIn(text))
        }
        if (!eligible) {
            val noContainer = !bottle.containsMatchIn(text) && !glass.containsMatchIn(text) && !Regex("einweg", RegexOption.IGNORE_CASE).containsMatchIn(text)
            return when {
                // "Bier 20 x 0,5 l" without container is a crate of reusable bottles.
                isBeer && count >= 10 -> Deposit(DepositKind.REUSABLE, null, count, inferred = true)
                // A single 0.5 l beer without "Flasche" is almost always a can in Austrian flyers.
                isBeer && !isWine && noContainer && count == 1 && abs(litres - 0.5) < 0.01 ->
                    Deposit(DepositKind.SINGLE_USE, SINGLE_USE_RATE, 1, inferred = true)
                else -> null
            }
        }
        return Deposit(DepositKind.SINGLE_USE, SINGLE_USE_RATE * count, count, inferred = !certain)
    }

    /** From an explicit amount (BILLA `depositPrice`) plus the shop's container type. */
    fun stated(amount: Double, kind: DepositKind?, text: String): Deposit {
        val k = kind ?: if (abs(amount / SINGLE_USE_RATE - (amount / SINGLE_USE_RATE).roundToInt()) < 0.01) DepositKind.SINGLE_USE else DepositKind.REUSABLE
        val n = if (k == DepositKind.SINGLE_USE) (amount / SINGLE_USE_RATE).roundToInt().coerceAtLeast(1) else containers(text).second
        return Deposit(k, amount, n)
    }

    fun condition(d: Deposit, price: Double): Condition {
        val amount = d.amount
        val label = when {
            amount == null -> "+ Mehrwegpfand"
            else -> "+ ${amount.euro()} Pfand" + if (d.inferred) "?" else ""
        }
        val what = when (d.kind) {
            DepositKind.SINGLE_USE -> "Einwegpfand"
            DepositKind.REUSABLE -> "Mehrwegpfand"
        }
        val detail = buildString {
            if (amount != null) {
                append("$what: ")
                if (d.containers > 1) append("${d.containers} × ${(amount / d.containers).euro()} € = ")
                append("${amount.euro()} € – wird an der Kassa zusätzlich verrechnet und bei Rückgabe erstattet. ")
                append("Preis inkl. Pfand: ${(price + amount).euro()} €.")
            } else {
                append("$what – die Höhe nennt die Quelle nicht (Flaschen und Kisten haben eigene Pfandbeträge).")
            }
            if (d.inferred) append(" Vermutet anhand von Getränkeart, Verpackung und Füllmenge – bitte im Flugblatt prüfen.")
        }
        return Condition(ConditionType.DEPOSIT, label, detail, inferred = d.inferred)
    }
}
