package at.rabattradar.core

import at.rabattradar.core.net.Http
import at.rabattradar.core.source.ReweProducts
import at.rabattradar.core.text.Text
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import kotlin.random.Random

/** The refresh got faster without changing results: these pin the rewritten parts to the old behaviour. */
class SpeedupsTest {

    /** Text.fold as it was before the fast path. */
    private fun referenceFold(input: String): String {
        var s = input.lowercase(Locale.GERMAN)
        for ((from, to) in listOf("ä" to "a", "ö" to "o", "ü" to "u", "ß" to "ss", "ae" to "a", "oe" to "o", "ue" to "u")) s = s.replace(from, to)
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return Regex("[^a-z0-9]+").replace(s, " ").trim()
    }

    @Test
    fun `fold gives the same result as before`() {
        val samples = listOf(
            "", " ", "Milka Alpenmilch", "  KÄSE-Würfel  ", "Straße", "Crème brûlée", "Rosé 2024", "Jalapeños",
            "Kelly‘s Erdnüsse", "Coca-Cola 0,33 l", "1 FL. € 2.49", "ÄÖÜ äöü ß", "Queue", "Mauer", "Ölz Milch Brötle",
            "Señor 🍕 Pizza", "İstanbul", "ǅ", "Ⅻ ①", "ﬁne", "tab\there", "Ångström", "Œuvre", "Fußball-WM",
        )
        samples.forEach { assertEquals(it, referenceFold(it), Text.fold(it)) }

        val alphabet = "aAäÄoOöÖuUüÜßeEsS éèêçñåøœ 0123456789-.,/€%+*&'‘’\"()\t\n🍕İıǅ"
        val random = Random(42)
        repeat(20_000) {
            val s = String(CharArray(random.nextInt(0, 24)) { alphabet[random.nextInt(alphabet.length)] })
            assertEquals(s, referenceFold(s), Text.fold(s))
        }
    }

    @Test
    fun `shop pages are loaded in parallel and kept in order`() = runTest {
        val requested = Collections.synchronizedList(mutableListOf<Int>())
        val http = Http { url, _ ->
            val page = Regex("page=(\\d+)").find(url)!!.groupValues[1].toInt()
            requested += page
            val items = (page * 500 until minOf(page * 500 + 500, 1157)).joinToString(",") { """{"sku":"$it"}""" }
            """{"total":1157,"results":[$items]}"""
        }
        val all = ReweProducts.fetchAll(http) { "https://shop.example/products?page=$it&pageSize=500" }
        assertEquals((0 until 1157).map { "$it" }, all.map { it.jsonObjectSku() })
        assertEquals(listOf(0, 1, 2), requested.sorted())
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectSku() =
        (this as kotlinx.serialization.json.JsonObject)["sku"]!!.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
}
