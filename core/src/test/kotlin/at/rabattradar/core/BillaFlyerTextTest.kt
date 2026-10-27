package at.rabattradar.core

import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.model.infoBar
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.source.BillaFlyerText
import at.rabattradar.core.source.FlyerPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real text of BILLA flyer page 16 and BILLA PLUS page 6 (KW 43/2026). */
class BillaFlyerTextTest {

    private val pages = Json.parseToJsonElement(Fixtures.read("billa_flyer_pages.json")).jsonArray.map { p ->
        val o = p.jsonObject
        FlyerPage(
            banner = o["banner"]!!.jsonPrimitive.content,
            publication = "fixture",
            number = o["number"]!!.jsonPrimitive.int,
            lines = o["lines"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    private fun flyerOffer(id: String, brand: String?, name: String, price: Double, regular: Double, banner: String = "BILLA") = Offer(
        id = id, chain = Chain.BILLA, banners = listOf(banner), name = name, brand = brand, price = price, regularPrice = regular,
        conditions = listOfNotNull(Conditions.inferMultiBuy(price, regular)), sources = listOf(SourceId.MARKTGURU),
    )

    private fun multi(o: Offer) = o.conditions.firstOrNull { it.type == ConditionType.MULTI_BUY }?.label

    @Test
    fun `recovers 3+3 and 2+2 next to a 4+2 with interleaved two-column text`() {
        val result = BillaFlyerText.enrichAll(
            listOf(
                flyerOffer("cola", "Coca-Cola", "Cola", 1.24, 2.49),
                flyerOffer("wq", "Waldquelle", "Himbeere", 0.62, 1.25),
                flyerOffer("eistee", "Rauch", "Eistee Zitrone", 0.99, 1.99),
                flyerOffer("monster", "Monster", "Energydrink", 1.19, 1.69),
            ),
            pages,
        ).associateBy { it.id }

        val cola = result.getValue("cola")
        assertEquals("3+3 gratis", multi(cola))
        assertEquals("BEI 6 FL. JE", cola.infoBar().conditionLine)
        assertEquals("1 FL. €2.49", cola.infoBar().reference)
        assertEquals("6 FL. = €7.44", cola.infoBar().packageLine)
        assertTrue(SourceId.BILLA_FLYER in cola.sources)

        assertEquals("3+3 gratis", multi(result.getValue("wq")))
        assertEquals("2+2 gratis", multi(result.getValue("eistee")))
        // "AB 2 DOSEN JE" without a label.
        assertEquals("ab 2 Dosen", result.getValue("monster").condition(ConditionType.MIN_QUANTITY)?.label)
    }

    @Test
    fun `labels are not shared - two Sekt offers take the 1+1 labels, Eistee gets 2+2`() {
        val result = BillaFlyerText.enrichAll(
            listOf(
                flyerOffer("goldeck", "Goldeck", "Sekt", 4.49, 8.99, "BILLA PLUS"),
                flyerOffer("eistee", "Rauch", "Eistee Pfirsich", 0.99, 1.99, "BILLA PLUS"),
            ),
            pages,
        ).associateBy { it.id }
        assertEquals("1+1 gratis", multi(result.getValue("goldeck")))
        assertEquals("2+2 gratis", multi(result.getValue("eistee")))
    }

    @Test
    fun `offers that are not in the flyer stay untouched`() {
        val offer = flyerOffer("x", "Manner", "Schnitten", 1.99, 3.99)
        assertNull(multi(BillaFlyerText.enrichAll(listOf(offer), pages).single()))
    }
}
