package at.rabattradar.core.model

import at.rabattradar.core.parse.Conditions
import java.util.Locale

/**
 * Content of the flyer-style price tag drawn at the bottom left of every offer picture:
 *
 * ```
 * [ 1 TAFEL €4.99  ]   <- reference line (single / normal / without-app price)
 * [   2+1 GRATIS   ]   <- headline (deal type)
 * [ BEI 3 TAFELN JE]   <- condition line
 *        3.32          <- price
 * ```
 */
data class InfoBar(
    val reference: String?,
    val headline: String,
    val conditionLine: String?,
    val price: Double,
    /** Price needs a card/app - drawn in the loyalty colour. */
    val membership: Boolean,
    /** Headline is a guess (e.g. inferred 2+1). */
    val uncertain: Boolean,
)

private fun upper(s: String) = s.uppercase(Locale.GERMAN)

private fun money(v: Double) = "€" + String.format(Locale.US, "%.2f", v)

fun Offer.infoBar(): InfoBar {
    val multi = condition(ConditionType.MULTI_BUY)
    val minQty = condition(ConditionType.MIN_QUANTITY)
    val loyalty = conditions.firstOrNull {
        it.type == ConditionType.LOYALTY_APP || it.type == ConditionType.LOYALTY_CARD || it.type == ConditionType.APP_COUPON
    }
    val weekdays = condition(ConditionType.WEEKDAYS)
    val unit = minQty?.unit ?: multi?.unit ?: "Stk."

    val headline = when {
        multi != null -> upper(multi.label)
        loyalty != null -> when {
            loyalty.label.equals("Lidl Plus", true) -> "LIDL PLUS"
            loyalty.type == ConditionType.LOYALTY_CARD -> "jö PREIS"
            loyalty.type == ConditionType.APP_COUPON -> "APP-GUTSCHEIN"
            else -> "APP-PREIS"
        }
        discountPercent != null && discountPercent > 0 -> "-$discountPercent%"
        has(ConditionType.CLEARANCE) -> "ABVERKAUF"
        else -> "AKTION"
    }

    val conditionLine = when {
        minQty != null -> upper(minQty.label) + " JE"
        multi != null && multi.quantity != null && !multi.inferred -> "BEI ${multi.quantity} ${upper(unit)} JE"
        loyalty != null && (multi != null || discountPercent != null) -> "MIT ${upper(loyalty.label)}"
        weekdays != null -> upper(weekdays.label)
        has(ConditionType.ONLINE_ONLY) -> "NUR ONLINE"
        has(ConditionType.WEIGHT_PRICE) -> "PER KG"
        loyalty != null -> "MIT ${upper(loyalty.label)}"
        else -> null
    }

    val single = tiers.firstOrNull { it.label.startsWith("1 ") || it.label.startsWith("Einzeln") }
    val withoutApp = tiers.firstOrNull { it.label.startsWith("ohne", ignoreCase = true) }
    val reference = when {
        (multi != null || minQty != null) && single != null -> "1 ${upper(unit)} ${money(single.price)}"
        (multi != null || minQty != null) && regularPrice != null -> "1 ${upper(unit)} ${money(regularPrice)}"
        withoutApp != null -> "${upper(withoutApp.label)} ${money(withoutApp.price)}"
        regularPrice != null && regularPrice > price -> "STATT ${money(regularPrice)}"
        validTo != null -> "BIS ${Conditions.dayShort(validTo).uppercase()} ${Conditions.fmt(validTo)}"
        else -> null
    }

    return InfoBar(
        reference = reference,
        headline = headline,
        conditionLine = conditionLine,
        price = price,
        membership = loyalty != null,
        uncertain = multi?.inferred == true && minQty == null,
    )
}
