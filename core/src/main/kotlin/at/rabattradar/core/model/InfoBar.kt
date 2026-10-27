package at.rabattradar.core.model

import at.rabattradar.core.i18n.Lang
import at.rabattradar.core.i18n.Texts
import java.util.Locale

/**
 * Content of the flyer-style price tag drawn at the bottom left of every offer picture:
 *
 * ```
 * [ 1 TAFEL €4.99  ]   <- reference line (single / normal / without-app price)
 * [   2+1 GRATIS   ]   <- headline (deal type)
 * [ BEI 3 TAFELN JE]   <- condition line
 *   3.32  +€0.25 PFAND <- price, with the deposit in small print beside it
 *         = €3.57
 * [3 TAFELN = €9.96]   <- package price for multi-buys
 * ```
 */
data class InfoBar(
    val reference: String?,
    val headline: String,
    val conditionLine: String?,
    val price: Double,
    /** "+€0.25 PFAND" */
    val depositLine: String?,
    /** "= €3.57" - price including deposit. */
    val withDepositLine: String?,
    /** "3 TAFELN = €9.96" */
    val packageLine: String?,
    /** Price needs a card/app - drawn in the loyalty colour. */
    val membership: Boolean,
    /** Headline is a guess (e.g. inferred 2+1). */
    val uncertain: Boolean,
)

private fun upper(s: String) = s.uppercase(Locale.GERMAN)

private fun money(v: Double) = "€" + String.format(Locale.US, "%.2f", v)

private fun Lang.pick(de: String, en: String) = if (this == Lang.DE) de else en

fun Offer.infoBar(lang: Lang = Lang.DE): InfoBar {
    val multi = condition(ConditionType.MULTI_BUY)
    val minQty = condition(ConditionType.MIN_QUANTITY)
    // jö is the better known name when PENNY lists "jö Karte + PENNY App".
    val loyalty = condition(ConditionType.LOYALTY_CARD) ?: condition(ConditionType.LOYALTY_APP) ?: condition(ConditionType.APP_COUPON)
    val weekdays = condition(ConditionType.WEEKDAYS)
    val unit = minQty?.unit ?: multi?.unit ?: "Stk."

    val withoutApp = tiers.firstOrNull { it.kind == TierKind.WITHOUT }
    val loyaltyHeadline = loyalty?.let {
        when {
            it.key == CondKey.LIDL_PLUS -> "LIDL PLUS"
            it.type == ConditionType.LOYALTY_CARD -> lang.pick("jö PREIS", "jö PRICE")
            it.type == ConditionType.APP_COUPON -> lang.pick("APP-GUTSCHEIN", "APP VOUCHER")
            else -> lang.pick("APP-PREIS", "APP PRICE")
        }
    }
    // If the big price is the member price, that is the most important caveat and goes on top.
    val headlineIsLoyalty = loyaltyHeadline != null && (withoutApp != null || multi == null)
    val headline = when {
        headlineIsLoyalty -> loyaltyHeadline!!
        multi != null -> upper(multi.label(lang))
        discountPercent != null && discountPercent > 0 -> "-$discountPercent%"
        has(ConditionType.CLEARANCE) -> lang.pick("ABVERKAUF", "CLEARANCE")
        else -> lang.pick("AKTION", "DEAL")
    }

    fun quantityLine(n: Int, set: Boolean, printed: String?): String {
        val units = upper(Texts.unitPlural(unit, printed, lang))
        return when {
            lang == Lang.DE -> if (set) "BEI $n $units JE" else "AB $n $units JE"
            set -> "EACH, BUY $n $units"
            else -> "EACH FROM $n $units"
        }
    }

    val conditionLine = when {
        minQty != null -> quantityLine(minQty.quantity ?: 0, minQty.args["set"] == "true", minQty.args["printed"])
        multi != null && multi.quantity != null && !multi.inferred -> quantityLine(multi.quantity, true, null)
        loyalty != null && !headlineIsLoyalty -> lang.pick("MIT ", "WITH ") + upper(loyalty.label(lang))
        weekdays != null -> upper(weekdays.label(lang))
        has(ConditionType.ONLINE_ONLY) -> lang.pick("NUR ONLINE", "ONLINE ONLY")
        has(ConditionType.WEIGHT_PRICE) -> "PER KG"
        headlineIsLoyalty && loyalty?.type == ConditionType.APP_COUPON -> lang.pick("GUTSCHEIN AKTIVIEREN", "ACTIVATE VOUCHER")
        else -> null
    }

    val single = tiers.firstOrNull { it.kind == TierKind.SINGLE }
    val oneUnit = upper("1 " + Texts.unitSingular(unit, lang))
    val reference = when {
        (multi != null || minQty != null) && single != null -> "$oneUnit ${money(single.price)}"
        (multi != null || minQty != null) && regularPrice != null -> "$oneUnit ${money(regularPrice)}"
        withoutApp != null -> "${upper(withoutApp.label(lang))} ${money(withoutApp.price)}"
        regularPrice != null && regularPrice > price -> lang.pick("STATT ", "WAS ") + money(regularPrice)
        validTo != null -> lang.pick("BIS ", "UNTIL ") + upper(Texts.dayShort(validTo, lang)) + " " + Texts.date(validTo)
        else -> null
    }

    val depositAmount = deposit?.amount
    val mark = if (deposit?.inferred == true) "?" else ""
    val bundle = bundle(lang)
    return InfoBar(
        reference = reference,
        headline = headline,
        conditionLine = conditionLine,
        price = price,
        depositLine = when {
            depositAmount != null -> "+${money(depositAmount)} " + lang.pick("PFAND", "DEPOSIT") + mark
            deposit != null -> lang.pick("+ MEHRWEG-PFAND", "+ RETURNABLE DEPOSIT")
            else -> null
        },
        withDepositLine = depositAmount?.let { "= ${money(price + it)}" },
        packageLine = bundle?.let { "${upper(it.label)} = ${money(it.price)}" + if (it.inferred) "?" else "" },
        membership = loyalty != null,
        uncertain = multi?.inferred == true && minQty == null,
    )
}
