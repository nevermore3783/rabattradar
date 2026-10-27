package at.rabattradar.core

import at.rabattradar.core.i18n.Lang
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.parse.Conditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConditionsTest {

    @Test
    fun `multi buy is recognised but extra content is not`() {
        assertEquals("2+1 gratis", Conditions.multiBuy("Milka 2+1 GRATIS")?.label)
        assertEquals(6, Conditions.multiBuy("3 + 3 gratis")?.quantity)
        assertNull(Conditions.multiBuy("250 g + 50 g Gratis! per Packung"))
        assertEquals("2+1 gratis", Conditions.multiBuyFromTag("pt-2plus1")?.label)
    }

    @Test
    fun `minimum quantities from flyer texts`() {
        val set = Conditions.minQuantity("Bei 3 Tafeln je", perSet = true)!!
        assertEquals("bei 3 Tafeln", set.label)
        assertEquals("Tafel", set.unit)
        assertEquals(3, set.quantity)

        assertEquals("ab 2 Pkg.", Conditions.minQuantity("ab 2 Packungen")?.label)
        assertEquals("ab 2 Stk.", Conditions.minQuantity("Mengenvorteil ab 2 Stk. je")?.label)
        assertEquals("Träger", Conditions.minQuantity("6 x 0,5-Liter-Flasche ab 2 6er-Träger je nur 4,14")?.unit)
        assertNull(Conditions.minQuantity("für Kinder ab 3 Jahren geeignet"))
        assertNull(Conditions.minQuantity("ab 3.99 oder Dekofrüchte"))
        assertNull(Conditions.minQuantity("ab 0.100 Kilo"))
    }

    @Test
    fun `marktguru hints are extracted and removed from the description`() {
        val (rest, hints) = Conditions.extractHints("HINWEIS: MIT LIDL PLUS APP 14,00 € 20 x 0,5 l")
        assertEquals("20 x 0,5 l", rest)
        assertEquals(14.0, hints.single().price!!, 0.001)
        assertTrue(hints.single().program.contains("LIDL PLUS"))

        val (rest2, hints2) = Conditions.extractHints("HINWEIS: MIT PENNY APP + JÖ KARTE 9.49 € od. RED 38 % vol.")
        assertEquals("od. RED 38 % vol.", rest2)
        assertEquals(9.49, hints2.single().price!!, 0.001)

        val (_, online) = Conditions.extractHints("HINWEIS: NUR ONLINE VERFÜGBAR 4K Smart TV")
        assertEquals("NUR ONLINE VERFÜGBAR", online.single().program)
    }

    @Test
    fun `membership programs`() {
        val labels = Conditions.membership("Preis mit jö Bonus Club und PENNY App", Chain.PENNY).map { it.label }
        assertTrue("jö" in labels)
        assertTrue("PENNY App" in labels)
        assertEquals(ConditionType.APP_COUPON, Conditions.membership("MIT SPAR-APP-GUTSCHEIN", Chain.SPAR).single().type)
        assertTrue(Conditions.membership("Joghurt", Chain.BILLA).isEmpty())
    }

    @Test
    fun `two thirds of the normal price hints at a hidden 2+1`() {
        val inferred = Conditions.inferMultiBuy(0.66, 0.99)
        assertNotNull(inferred)
        assertTrue(inferred!!.inferred)
        assertNull(Conditions.inferMultiBuy(1.99, 3.99))
    }

    @Test
    fun `set prices reveal the multi-buy behind them`() {
        assertEquals("2+1 gratis", Conditions.multiBuyFromSet(3, 1.72, 2.59)?.label)
        assertEquals("1+1 gratis", Conditions.multiBuyFromSet(2, 1.39, 2.79)?.label)
        assertNull(Conditions.multiBuyFromSet(3, 2.00, 2.59))
        assertNotNull(Conditions.footnote("Helles*"))
        assertNull(Conditions.footnote("Helles"))
    }

    @Test
    fun `short promotions are flagged with weekdays`() {
        // 2026-10-30 is a Friday.
        assertEquals("nur Fr & Sa", Conditions.weekdays(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31))?.label)
        assertEquals("nur Mo–Mi", Conditions.weekdays(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4))?.label)
        assertEquals("Fri & Sat only", Conditions.weekdays(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31))?.label(Lang.EN))
        assertNull(Conditions.weekdays(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 4)))
    }
}
