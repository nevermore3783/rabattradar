package at.rabattradar.core

import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.DepositKind
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.bundle
import at.rabattradar.core.model.infoBar
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.parse.Deposits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositsAndBundleTest {

    private fun amount(text: String, kind: DepositKind? = null) = Deposits.detect(text, kind)?.amount

    @Test
    fun `explicit deposit statements win`() {
        val crate = Deposits.detect("Goldbräu 5,0 % Vol. 20 x 0,5 l zzgl. Pfand € 0,20")!!
        assertEquals(DepositKind.REUSABLE, crate.kind)
        assertEquals(4.0, crate.amount!!, 0.001)
        assertEquals(20, crate.containers)

        val cans = Deposits.detect("Energy Drink zzgl. Pfand 8 x € 0,25 = € 2 8 x 250 ml")!!
        assertEquals(2.0, cans.amount!!, 0.001)
        assertEquals(8, cans.containers)
        assertFalse(cans.inferred)

        assertEquals(0.25, amount("Kühles Blondes Märzen zzgl. Pfand € 0,25 5,0% Vol. 0,5 l")!!, 0.001)
        // "Je 0,33 l" after "Pfand" is a volume, not the amount.
        assertEquals(0.25, amount("Lemon Soda Preis exkl. Pfand Je 0,33 l Softdrinks")!!, 0.001)
        assertEquals(1.5, amount("Stiegl Goldbr. 6x0,5lt. Ds. 3.000 ml EINWEG mit Pfand Dose")!!, 0.001)
        assertNull(Deposits.detect("Sodastream Sirup Cola 440 ML EINWEG ohne Pfand PET Flasche"))
        assertNull(Deposits.detect("Bier-Adventkalender 24 x 0,33 l Pfandfrei"))
    }

    @Test
    fun `single-use deposit only for plastic and metal`() {
        val can = Deposits.detect("Wieselburger Gold 0.5 liter Dose Bier & Radler", DepositKind.SINGLE_USE)!!
        assertEquals(0.25, can.amount!!, 0.001)
        assertFalse(can.inferred)
        // One-way glass beer bottles carry no deposit.
        assertNull(Deposits.detect("Helles 0.33 liter Flasche Bier & Radler", DepositKind.SINGLE_USE))
        assertNull(Deposits.detect("Märzen 0,5-Liter-Einweg-Flasche Bier"))
        // Cartons, dairy, wine and non-food are exempt.
        assertNull(Deposits.detect("Eistee Pfirsich oder Zitrone 0,75-Liter-Elopak Softdrinks"))
        assertNull(Deposits.detect("Kakaomilch 220 ml Dose", DepositKind.SINGLE_USE))
        assertNull(Deposits.detect("Grüner Veltliner BIO 2025 Niederösterreich 0,75 Liter Getränke"))
        assertNull(Deposits.detect("Wasserkocher max. 2.200 Watt, ca. 1,7 Liter"))
        assertNull(Deposits.detect("Shot 0.08 liter Flasche", DepositKind.SINGLE_USE))
    }

    @Test
    fun `soft drinks without container are inferred, reusable is flagged without amount`() {
        val cola = Deposits.detect("Cola verschiedene Sorten 1,5 l Softdrinks")!!
        assertEquals(0.25, cola.amount!!, 0.001)
        assertTrue(cola.inferred)
        assertEquals(1.5, amount("Almdudler oder Spezi 6x 0,33 l Softdrinks")!!, 0.001)
        val beer = Deposits.detect("Egger Märzen div. Sorten 0,5 Liter Bier")!!
        assertEquals(0.25, beer.amount!!, 0.001)
        assertTrue(beer.inferred)
        assertNull(Deposits.detect("Märzen 0,33 Liter Einweg Bier"))

        val mw = Deposits.detect("Hell 20 x 0,5-Liter-Mehrweg-Flasche")!!
        assertEquals(DepositKind.REUSABLE, mw.kind)
        assertNull(mw.amount)

        val billa = Deposits.stated(1.20, DepositKind.REUSABLE, "Gösser Naturradler 6x0,5")
        assertEquals(6, billa.containers)
        assertEquals(4, Deposits.stated(1.0, null, "Dosen").containers)
    }

    @Test
    fun `package price and deposit appear in the info bar`() {
        val beer = Offer(
            id = "1", chain = Chain.PENNY, name = "Wieselburger Gold", price = 0.79,
            conditions = listOf(Conditions.minQuantity("ab 24 Dosen")!!),
        ).withDeposit(Deposits.detect("0,5 l Dose Bier", DepositKind.SINGLE_USE))

        val bundle = beer.bundle()!!
        assertEquals(24, bundle.quantity)
        assertEquals("24 Dosen", bundle.label)
        assertEquals(18.96, bundle.price, 0.001)
        assertEquals(6.0, bundle.deposit!!, 0.001)
        assertEquals(24.96, bundle.total, 0.001)
        assertEquals(1.04, beer.priceWithDeposit!!, 0.001)
        assertEquals("+ 0,25 Pfand", beer.condition(ConditionType.DEPOSIT)?.label)

        val bar = beer.infoBar()
        assertEquals("+€0.25 PFAND", bar.depositLine)
        assertEquals("= €1.04", bar.withDepositLine)
        assertEquals("24 DOSEN = €18.96", bar.packageLine)

        val milka = Offer(
            id = "2", chain = Chain.BILLA, name = "Milka", price = 3.32,
            conditions = listOf(Conditions.multiBuyCondition(2, 1), Conditions.minQuantity("Bei 3 Tafeln je", perSet = true)!!),
        )
        assertEquals("3 TAFELN = €9.96", milka.infoBar().packageLine)
        assertNull(milka.infoBar().depositLine)

        // 3+3: six items, the package line uses the multi-buy quantity.
        val sixPack = Offer(id = "3", chain = Chain.SPAR, name = "Joghurt", price = 0.5, conditions = listOf(Conditions.multiBuyCondition(3, 3)))
        assertEquals("6 STK. = €3.00", sixPack.infoBar().packageLine)

        val single = Offer(id = "4", chain = Chain.HOFER, name = "Heidelbeeren", price = 1.99)
        assertNull(single.bundle())
    }
}
