package at.rabattradar.ui

import at.rabattradar.core.model.Offer
import at.rabattradar.core.parse.Conditions
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

fun euro(value: Double): String = String.format(Locale.GERMANY, "%.2f €", value)

/** Short validity label for cards: "bis Mi 28.10.", "ab Do 29.10.", "nur heute". */
fun validityShort(offer: Offer, today: LocalDate): String? {
    val from = offer.validFrom
    val to = offer.validTo
    return when {
        from != null && from.isAfter(today) -> "ab ${Conditions.dayShort(from)} ${Conditions.fmt(from)}"
        to == null -> null
        to == today -> "nur noch heute"
        to == today.plusDays(1) -> "bis morgen"
        else -> "bis ${Conditions.dayShort(to)} ${Conditions.fmt(to)}"
    }
}

/** Long validity text for the detail screen. */
fun validityLong(offer: Offer, today: LocalDate): String {
    val from = offer.validFrom
    val to = offer.validTo
    fun day(d: LocalDate) = "${Conditions.dayLong(d)}, ${Conditions.fmt(d)}${d.year}"
    return when {
        from != null && to != null -> "${day(from)} – ${day(to)}"
        to != null -> "bis ${day(to)}"
        from != null -> "ab ${day(from)}"
        else -> "Zeitraum nicht angegeben"
    }
}

fun remainingText(offer: Offer, today: LocalDate): String? {
    val from = offer.validFrom
    val to = offer.validTo
    return when {
        from != null && from.isAfter(today) -> {
            val d = ChronoUnit.DAYS.between(today, from)
            if (d == 1L) "startet morgen" else "startet in $d Tagen"
        }
        to != null -> {
            val d = ChronoUnit.DAYS.between(today, to)
            when {
                d < 0 -> "abgelaufen"
                d == 0L -> "endet heute"
                d == 1L -> "noch 2 Tage (bis morgen)"
                else -> "noch ${d + 1} Tage"
            }
        }
        else -> null
    }
}
