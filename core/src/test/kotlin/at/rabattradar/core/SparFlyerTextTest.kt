package at.rabattradar.core

import at.rabattradar.core.i18n.Lang
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.CondKey
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.PriceTier
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.model.TierKind
import at.rabattradar.core.model.bundle
import at.rabattradar.core.model.infoBar
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.source.FlyerPage
import at.rabattradar.core.source.SparFlyerText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real page texts of the SPAR (pages 3, 5, 10, 12), EUROSPAR (6) and INTERSPAR Vienna (7, 12, 15) flyers of KW 43/2026. */
class SparFlyerTextTest {

    private val pages = Json.parseToJsonElement(Fixtures.read("spar_flyer_pages.json")).jsonArray.map { p ->
        val o = p.jsonObject
        FlyerPage(
            banner = o["banner"]!!.jsonPrimitive.content,
            publication = o["publication"]!!.jsonPrimitive.content,
            number = o["number"]!!.jsonPrimitive.int,
            lines = listOf(o["text"]!!.jsonPrimitive.content),
        )
    }

    /** What marktguru delivers: prices without the condition, possibly a guessed "2+1 gratis?". */
    private fun flyerOffer(id: String, brand: String?, name: String, price: Double, regular: Double?, banner: String = "SPAR") = Offer(
        id = id, chain = Chain.SPAR, banners = listOf(banner), name = name, brand = brand, price = price, regularPrice = regular,
        conditions = listOfNotNull(Conditions.inferMultiBuy(price, regular)), sources = listOf(SourceId.MARKTGURU),
    )

    private fun enrich(vararg offers: Offer) = SparFlyerText.enrichAll(offers.toList(), pages).associateBy { it.id }

    private fun multi(o: Offer) = o.condition(ConditionType.MULTI_BUY)?.label

    @Test
    fun `recovers the multi-buys of the four flyer examples`() {
        val r = enrich(
            flyerOffer("energy", "S Budget", "Energy Drink", 0.29, 0.59),
            flyerOffer("wafer", "Lindt", "Choco Wafer", 3.49, 6.99),
            flyerOffer("cola1l", "Coca-Cola", "Cola", 1.32, 1.99),
            flyerOffer("cans", "Coca-Cola", "Cola", 0.69, 1.39),
        )

        val energy = r.getValue("energy").infoBar()
        assertEquals("12+12 GRATIS", energy.headline)
        assertEquals("BEI 24 DS. JE", energy.conditionLine)
        assertEquals("1 DOSE €0.59", energy.reference)
        assertEquals("24ER-TRAY = €6.96", energy.packageLine)
        assertTrue(SourceId.SPAR_FLYER in r.getValue("energy").sources)

        assertEquals("1+1 gratis", multi(r.getValue("wafer")))
        assertEquals("BEI 2 PKG. JE", r.getValue("wafer").infoBar().conditionLine)

        // 1.32 is 2/3 of 1.99, which used to be guessed as "2+1 gratis?" - the flyer says 4+2.
        val cola = r.getValue("cola1l")
        assertEquals("4+2 gratis", multi(cola))
        assertFalse(cola.conditions.any { it.inferred })
        assertEquals("6ER-TRAY = €7.92", cola.infoBar().packageLine)
        assertEquals("TRAY OF 6 = €7.92", cola.infoBar(Lang.EN).packageLine)
        assertEquals("EACH, BUY 6 BOTTLES", cola.infoBar(Lang.EN).conditionLine)

        assertEquals("12+12 gratis", multi(r.getValue("cans")))
        assertEquals("24er-Tray", r.getValue("cans").bundle()?.label)
        assertEquals(16.56, r.getValue("cans").bundle()!!.price, 0.001)
    }

    @Test
    fun `pack of two trays and minimum quantity without label`() {
        val r = enrich(
            flyerOffer("goesser", "Gösser", "Märzen", 0.64, 1.29),
            flyerOffer("desperados", "Desperados", "Tequila Bier", 1.49, 2.19),
        )
        assertEquals("12+12 gratis", multi(r.getValue("goesser")))
        assertEquals("2 x 12er-Tray", r.getValue("goesser").bundle()?.label)
        assertEquals("2 trays of 12", r.getValue("goesser").bundle(Lang.EN)?.label)

        val desperados = r.getValue("desperados")
        assertNull(multi(desperados))
        assertEquals("ab 6 Fl.", desperados.condition(ConditionType.MIN_QUANTITY)?.label)
        assertEquals("1 FL. €2.19", desperados.infoBar().reference)
    }

    @Test
    fun `INTERSPAR layout with spaced prices and a label before the price block`() {
        val r = enrich(flyerOffer("salzstangerl", "Spar Natur pur", "Bio-Salzstangerl", 0.62, 0.94, "INTERSPAR"))
        assertEquals("2+1 gratis", multi(r.getValue("salzstangerl")))
        assertEquals("bei 3 Stk.", r.getValue("salzstangerl").condition(ConditionType.MIN_QUANTITY)?.label)
    }

    @Test
    fun `plain reductions keep no condition and lose a wrong guess`() {
        val r = enrich(
            // "statt 7,49 4 99" - exactly 2/3, but a plain -33%.
            flyerOffer("mondseer", "Schärdinger", "Mondseer", 4.99, 7.49, "INTERSPAR"),
            // Neighbours with the same prices must not lend their "ab 6 Fl." / "1+1" to these.
            flyerOffer("jalapenos", "Spar", "Jalapeños", 1.29, 1.99, "EUROSPAR"),
            flyerOffer("schotter", "Weingut Hannes Reeh", "Cuvée Roter Schotter", 5.99, 11.99, "INTERSPAR"),
        )
        r.values.forEach { o ->
            assertTrue(o.id, o.conditions.none { it.type == ConditionType.MULTI_BUY || it.type == ConditionType.MIN_QUANTITY })
            assertFalse(o.id, SourceId.SPAR_FLYER in o.sources)
        }
    }

    @Test
    fun `multi-buy without the SPAR app voucher is kept as a note`() {
        val waldquelle = flyerOffer("wq", "Waldquelle", "Frucht", 0.62, 1.25).copy(
            conditions = listOf(Conditions.sparAppCondition(0.62)),
            tiers = listOf(PriceTier(TierKind.WITHOUT, 0.83, program = "voucher")),
        )
        val o = enrich(waldquelle).getValue("wq")
        val note = o.conditions.single { it.key == CondKey.WITHOUT_DEAL }
        assertEquals("ohne Gutschein: 4+2 gratis", note.label)
        assertEquals("without voucher: 4+2 free", note.label(Lang.EN))
        assertNull(o.condition(ConditionType.MIN_QUANTITY))
        assertEquals("APP-GUTSCHEIN", o.infoBar().headline)
    }

    @Test
    fun `page texts are read from the flipbook settings`() {
        val html = """<script>window.staticSettings ={"url":"/x/","pageTexts":["1 Ds. 1,39 ab 24 Ds. je 0,69 \"neu\" [Aktion]",null,"S. 3"],"device":{}};</script>"""
        assertEquals(listOf("1 Ds. 1,39 ab 24 Ds. je 0,69 \"neu\" [Aktion]", "", "S. 3"), SparFlyerText.pageTexts(html))
        assertNull(SparFlyerText.pageTexts("<html>404</html>"))
    }

    @Test
    fun `postal codes map to the regions in the flyer addresses`() {
        assertEquals("wien", SparFlyerText.region("1010"))
        assertEquals("niederoesterreich", SparFlyerText.region("3100"))
        assertEquals("oberoesterreich", SparFlyerText.region("5280"))
        assertEquals("salzburg", SparFlyerText.region("5020"))
        assertEquals("tirol", SparFlyerText.region("9900"))
        assertEquals("vorarlberg", SparFlyerText.region("6850"))
        assertEquals("kaernten", SparFlyerText.region("9020"))
    }
}
