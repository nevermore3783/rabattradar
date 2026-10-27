package at.rabattradar.core.model

import at.rabattradar.core.i18n.Lang
import at.rabattradar.core.i18n.Texts

/**
 * What you actually pay for a multi-buy ("3+3 gratis", "bei 3 Tafeln je 3.32", "ab 12 Fl. je 0.59"):
 * the minimum number of items times the advertised unit price, plus their deposit.
 */
data class Bundle(
    val quantity: Int,
    /** "3 Tafeln", "12 Fl.", "6 Stk." / "3 bars" */
    val label: String,
    /** quantity x unit price, without deposit. */
    val price: Double,
    /** quantity x deposit, if a deposit amount is known. */
    val deposit: Double?,
    /** Based on an inferred multi-buy ("2+1 gratis?"). */
    val inferred: Boolean,
) {
    val total: Double get() = price + (deposit ?: 0.0)
}

fun Offer.bundle(lang: Lang = Lang.DE): Bundle? {
    val minQty = conditions.firstOrNull { it.type == ConditionType.MIN_QUANTITY && !it.inferred }
    val multi = conditions.firstOrNull { it.type == ConditionType.MULTI_BUY }
    val quantity = minQty?.quantity ?: multi?.quantity ?: return null
    if (quantity < 2) return null
    val unit = minQty?.unit ?: multi?.unit ?: "Stk."
    return Bundle(
        quantity = quantity,
        label = "$quantity ${Texts.unitPlural(unit, minQty?.args?.get("printed"), lang)}",
        price = price * quantity,
        deposit = deposit?.amount?.let { it * quantity },
        inferred = minQty == null && multi?.inferred == true,
    )
}
