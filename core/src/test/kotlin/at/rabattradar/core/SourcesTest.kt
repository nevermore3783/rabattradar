package at.rabattradar.core

import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.model.infoBar
import at.rabattradar.core.source.BillaShopSource
import at.rabattradar.core.source.FetchContext
import at.rabattradar.core.source.LidlSource
import at.rabattradar.core.source.MarktguruSource
import at.rabattradar.core.source.PennySource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SourcesTest {
    private val ctx = FetchContext(zipCode = "1010", today = LocalDate.of(2026, 10, 27))

    @Test
    fun `billa multi-buy produces the flyer info bar`() = runTest {
        val offers = BillaShopSource(Fixtures.http("shop.billa.at" to Fixtures.read("billa_promotions.json"))).fetch(ctx)
        val milka = offers.single { it.name == "Milka Oreo" }
        assertEquals(3.32, milka.price, 0.001)
        assertEquals(4.99, milka.regularPrice!!, 0.001)
        assertEquals(33, milka.discountPercent)
        assertTrue(milka.has(ConditionType.NO_DATES))

        val bar = milka.infoBar()
        assertEquals("1 TAFEL €4.99", bar.reference)
        assertEquals("2+1 GRATIS", bar.headline)
        assertEquals("BEI 3 TAFELN JE", bar.conditionLine)

        val radler = offers.single { it.name.startsWith("Gösser") }
        assertEquals("+ 1,20 Pfand", radler.condition(ConditionType.DEPOSIT)?.label)
        assertEquals("-40%", radler.infoBar().headline)
    }

    @Test
    fun `penny loyalty price and multi-buy`() = runTest {
        val offers = PennySource(Fixtures.http("penny.at" to Fixtures.read("penny_products.json"))).fetch(ctx)
        val cheese = offers.single { it.name == "Frischkäse Knoblauch" }
        assertEquals(1.49, cheese.price, 0.001)
        assertTrue(cheese.has(ConditionType.LOYALTY_CARD))
        assertEquals("bei 3 Becher", cheese.condition(ConditionType.MIN_QUANTITY)?.label)
        assertEquals(LocalDate.of(2026, 10, 28), cheese.validTo)
        assertEquals("jö PREIS", cheese.infoBar().headline)

        val beer = offers.single { it.name == "Helles" }
        assertEquals("ab 12 Fl.", beer.condition(ConditionType.MIN_QUANTITY)?.label)
        assertEquals("1 FL. €1.09", beer.infoBar().reference)

        val kaki = offers.single { it.name == "Kaki" }
        assertEquals("nur Do–Sa", kaki.condition(ConditionType.WEEKDAYS)?.label)
        assertTrue(kaki.conditions.any { it.type == ConditionType.NOTE && it.label.contains("Wochenend") })
    }

    @Test
    fun `lidl plus, multi-buy and exact dates`() = runTest {
        val offers = LidlSource(Fixtures.http("lidl.at" to Fixtures.read("lidl_search.json"))).fetch(ctx)
        val phil = offers.single { it.name == "Frischkäse" }
        assertEquals("Philadelphia", phil.brand)
        assertEquals(1.24, phil.price, 0.001)
        assertEquals("ohne Lidl Plus", phil.tiers.first().label)
        assertEquals("LIDL PLUS", phil.infoBar().headline)

        val monte = offers.single { it.name == "Monte Maxi" }
        assertEquals("bei 2 Stk.", monte.condition(ConditionType.MIN_QUANTITY)?.label)
        assertEquals(2.79, monte.tiers.single { it.label == "Einzeln" }.price, 0.001)
        assertEquals(LocalDate.of(2026, 11, 2), monte.validFrom)
        assertEquals(LocalDate.of(2026, 11, 4), monte.validTo)

        val saw = offers.single { it.name == "Benzin-Kettensäge" }
        assertEquals(69.99, saw.price, 0.001)
        assertTrue(saw.condition(ConditionType.LOYALTY_APP)!!.detail!!.contains("exklusiv"))
    }

    @Test
    fun `marktguru hints, inference and banner de-duplication`() = runTest {
        val http = Fixtures.http(
            "www.marktguru.at" to Fixtures.MARKTGURU_HOME,
            "api.marktguru.at" to Fixtures.read("marktguru_search.json"),
        )
        val offers = MarktguruSource(http).fetch(ctx)

        val beer = offers.single { it.name == "Märzen" }
        assertEquals(Chain.LIDL, beer.chain)
        assertEquals(14.0, beer.price, 0.001)
        assertEquals(14.8, beer.tiers.single().price, 0.001)
        assertEquals("20 x 0,5 l", beer.description)

        val tea = offers.single { it.name == "Eistee" }
        val inferred = tea.condition(ConditionType.MULTI_BUY)
        assertNotNull(inferred)
        assertTrue(inferred!!.inferred)
        assertTrue(tea.infoBar().uncertain)

        val vodka = offers.single { it.name == "Vodka" }
        assertEquals(9.49, vodka.price, 0.001)
        assertTrue(vodka.has(ConditionType.LOYALTY_CARD))

        val bread = offers.single { it.name == "Bio-Roggenbrot" }
        assertEquals(ConditionType.APP_COUPON, bread.conditions.first().type)
        assertEquals("ohne Gutschein", bread.tiers.single().label)

        val berries = offers.single { it.name == "Heidelbeeren" }
        assertEquals(Chain.HOFER, berries.chain)
        assertEquals(50, berries.discountPercent)
        assertEquals("nur Fr & Sa", berries.condition(ConditionType.WEEKDAYS)?.label)
        assertNull(berries.condition(ConditionType.MULTI_BUY))

        val snapshot = Aggregator(listOf(MarktguruSource(http))).fetch(ctx)
        val schnitzel = snapshot.offers.filter { it.name.contains("Hendl-Filetschnitzel") }
        assertEquals(1, schnitzel.size)
        assertEquals(listOf("SPAR", "SPAR Gourmet"), schnitzel.single().banners.sorted())
        assertEquals(SourceId.MARKTGURU, snapshot.statuses.single().source)
    }
}
