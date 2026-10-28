package at.rabattradar.core.text

import java.text.Normalizer
import java.util.Locale

/** Text helpers shared by sources, merger and search. */
object Text {
    private val germanFold = listOf(
        "ä" to "a", "ö" to "o", "ü" to "u", "ß" to "ss",
        "ae" to "a", "oe" to "o", "ue" to "u",
    )
    private val marks = Regex("\\p{M}+")
    private val price = Regex("(\\d+)[.,](\\d{1,2})")
    private val html = Regex("<[^>]+>")
    private val spaces = Regex("\\s+")

    /**
     * Lowercase, fold umlauts (so "kaese", "käse" and "kase" are equal), strip accents and
     * replace everything that is not a letter or digit by a space.
     */
    fun fold(input: String): String {
        var s = input.lowercase(Locale.GERMAN)
        for ((from, to) in germanFold) s = s.replace(from, to)
        // Called for every word of every offer and flyer page: ASCII needs no accent stripping.
        if (s.any { it.code >= 0x80 }) s = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")
        // Runs of anything but a-z/0-9 become one space, none at the ends.
        val out = StringBuilder(s.length)
        var gap = false
        for (c in s) {
            if (c in 'a'..'z' || c in '0'..'9') {
                if (gap && out.isNotEmpty()) out.append(' ')
                gap = false
                out.append(c)
            } else {
                gap = true
            }
        }
        return out.toString()
    }

    fun tokens(input: String): List<String> = fold(input).split(' ').filter { it.isNotEmpty() }

    fun stripHtml(input: String): String =
        spaces.replace(html.replace(input.replace("</li>", " · ").replace("<br>", " "), " "), " ")
            .replace("&amp;", "&").replace("&nbsp;", " ").replace("&quot;", "\"")
            .trim().trim('·').trim()

    fun clean(input: String?): String? = input?.let { spaces.replace(it, " ").trim() }?.takeIf { it.isNotEmpty() }

    /** "1.99" -> 1.99, "1,99 €" -> 1.99. */
    fun parsePrice(input: String): Double? =
        price.find(input)?.let { m ->
            "${m.groupValues[1]}.${m.groupValues[2].padEnd(2, '0')}".toDoubleOrNull()
        }

    fun titleCaseBrand(input: String): String =
        if (input.length > 3 && input == input.uppercase(Locale.GERMAN)) {
            input.lowercase(Locale.GERMAN).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.GERMAN) } }
        } else input
}

fun Double.euro(): String = String.format(Locale.GERMANY, "%.2f", this)
