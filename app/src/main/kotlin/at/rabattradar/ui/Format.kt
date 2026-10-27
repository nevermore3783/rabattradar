package at.rabattradar.ui

import at.rabattradar.core.i18n.Texts
import at.rabattradar.core.model.Offer
import at.rabattradar.ui.i18n.UiStrings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Short validity label for cards: "bis Mi 28.10." / "until Wed 28.10.", "ab Do 29.10.", "nur noch heute". */
fun validityShort(offer: Offer, today: LocalDate, s: UiStrings): String? {
    val from = offer.validFrom
    val to = offer.validTo
    return when {
        from != null && from.isAfter(today) -> s.from(Texts.dayShort(from, s.lang), Texts.date(from))
        to == null -> null
        to == today -> s.lastDay
        to == today.plusDays(1) -> s.untilTomorrow
        else -> s.until(Texts.dayShort(to, s.lang), Texts.date(to))
    }
}

/** Long validity text for the detail screen. */
fun validityLong(offer: Offer, s: UiStrings): String {
    val from = offer.validFrom
    val to = offer.validTo
    fun day(d: LocalDate) = "${Texts.dayLong(d, s.lang)}, ${Texts.date(d)}${d.year}"
    return when {
        from != null && to != null -> "${day(from)} – ${day(to)}"
        to != null -> s.until("", day(to)).replace("  ", " ")
        from != null -> s.from("", day(from)).replace("  ", " ")
        else -> s.noPeriod
    }
}

fun remainingText(offer: Offer, today: LocalDate, s: UiStrings): String? {
    val from = offer.validFrom
    val to = offer.validTo
    return when {
        from != null && from.isAfter(today) -> s.startsIn(ChronoUnit.DAYS.between(today, from))
        to != null -> {
            val d = ChronoUnit.DAYS.between(today, to)
            when {
                d < 0 -> s.expired
                d == 0L -> s.endsToday
                else -> s.daysLeft(d)
            }
        }
        else -> null
    }
}
