package at.rabattradar.core.text

import java.text.Normalizer
import java.util.Locale

/** Text helpers shared by sources, merger and search. */
object Text {
    private val germanFold = listOf(
        "ä" to "a", "ö" to "o", "ü" to "u", "ß" to "ss",
        "ae" to "a", "oe" to "o", "ue" to "u",
    )
    private val nonAlnum = Regex("[^a-z0-9]+")
    private val html = Regex("<[^>]+>")
    private val spaces = Regex("\\s+")

    /**
     * Lowercase, fold umlauts (so "kaese", "käse" and "kase" are equal), strip accents and
     * replace everything that is not a letter or digit by a space.
     */
    fun fold(input: String): String {
        var s = input.lowercase(Locale.GERMAN)
        for ((from, to) in germanFold) s = s.replace(from, to)
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return nonAlnum.replace(s, " ").trim()
    }

    fun tokens(input: String): List<String> = fold(input).split(' ').filter { it.isNotEmpty() }

    fun stripHtml(input: String): String =
        spaces.replace(html.replace(input.replace("</li>", " · ").replace("<br>", " "), " "), " ")
            .replace("&amp;", "&").replace("&nbsp;", " ").replace("&quot;", "\"")
            .trim().trim('·').trim()

    fun clean(input: String?): String? = input?.let { spaces.replace(it, " ").trim() }?.takeIf { it.isNotEmpty() }

    /** "1.99" -> 1.99, "1,99 €" -> 1.99. */
    fun parsePrice(input: String): Double? =
        Regex("(\\d+)[.,](\\d{1,2})").find(input)?.let { m ->
            "${m.groupValues[1]}.${m.groupValues[2].padEnd(2, '0')}".toDoubleOrNull()
        }

    fun titleCaseBrand(input: String): String =
        if (input.length > 3 && input == input.uppercase(Locale.GERMAN)) {
            input.lowercase(Locale.GERMAN).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.GERMAN) } }
        } else input
}

fun Double.euro(): String = String.format(Locale.GERMANY, "%.2f", this)
