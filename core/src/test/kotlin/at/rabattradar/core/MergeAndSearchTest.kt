package at.rabattradar.core

import at.rabattradar.core.merge.OfferMerger
import at.rabattradar.core.model.Chain
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.search.OfferIndex
import at.rabattradar.core.search.OfferQuery
import at.rabattradar.core.search.SortOrder
import at.rabattradar.core.search.ValidityFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MergeAndSearchTest {
    private val today = LocalDate.of(2026, 10, 27)

    private fun shop(id: String, name: String, price: Double, brand: String? = "Milka") = Offer(
        id = id, chain = Chain.BILLA, banners = listOf("BILLA"), name = name, brand = brand, price = price, regularPrice = 4.99,
        conditions = listOf(
            Conditions.minQuantity("bei 3 Tafeln", perSet = true)!!,
            Conditions.noDates("BILLA"),
            Conditions.onlineShop("BILLA"),
        ),
        imageUrl = "https://img/$id.jpg", sources = listOf(SourceId.BILLA_SHOP),
    )

    private val flyer = Offer(
        id = "mg-1", chain = Chain.BILLA, banners = listOf("BILLA PLUS"), name = "Schokolade", brand = "Milka",
        description = "div. Sorten", price = 3.32, regularPrice = 4.99,
        validFrom = LocalDate.of(2026, 10, 22), validTo = LocalDate.of(2026, 10, 28),
        flyerImageUrl = "https://flyer/1.webp", imageUrl = "https://flyer/1m.webp", sources = listOf(SourceId.MARKTGURU),
    )

    @Test
    fun `flyer offer enriches the matching shop offer and dates spread to the promotion`() {
        val merged = OfferMerger.merge(
            listOf(
                shop("billa_shop-1", "Milka Alpenmilch Schokolade", 3.32),
                shop("billa_shop-2", "Milka Oreo", 3.32),
                shop("billa_shop-3", "Manner Schnitten", 2.99, brand = "Manner"),
                flyer,
            ),
        )
        assertEquals(3, merged.size)
        val alpen = merged.single { it.id == "billa_shop-1" }
        assertEquals(listOf("BILLA", "BILLA PLUS"), alpen.banners)
        assertEquals(LocalDate.of(2026, 10, 28), alpen.validTo)
        assertEquals("https://img/billa_shop-1.jpg", alpen.imageUrl)
        assertEquals("https://flyer/1.webp", alpen.flyerImageUrl)
        assertFalse(alpen.has(ConditionType.NO_DATES))
        assertFalse(alpen.has(ConditionType.ONLINE_SHOP))
        assertNull(alpen.condition(ConditionType.WEEKDAYS))

        // Same brand, price and condition -> same promotion, dates adopted.
        val oreo = merged.single { it.id == "billa_shop-2" }
        assertEquals(LocalDate.of(2026, 10, 28), oreo.validTo)
        assertTrue(oreo.conditions.any { it.inferred && it.label == "Zeitraum übernommen" })

        val manner = merged.single { it.id == "billa_shop-3" }
        assertNull(manner.validTo)
    }

    @Test
    fun `search folds umlauts and matches compounds`() {
        val offers = listOf(
            Offer(id = "1", chain = Chain.SPAR, name = "Bergkäse", price = 2.0, validTo = today),
            Offer(id = "2", chain = Chain.HOFER, name = "Milchschokolade", price = 1.0, validFrom = today.plusDays(2), validTo = today.plusDays(5)),
            Offer(
                id = "3", chain = Chain.LIDL, name = "Frischkäse", price = 1.24, regularPrice = 1.89, discountPercent = 34,
                conditions = listOf(Conditions.lidlPlusCondition(1.24)),
            ),
            Offer(id = "4", chain = Chain.BILLA, name = "Alter Käse", price = 1.0, validTo = today.minusDays(1)),
        )
        val index = OfferIndex(offers)
        assertEquals(listOf("1", "3"), index.search(OfferQuery(text = "kaese"), today).map { it.id }.sorted())
        assertEquals(listOf("2"), index.search(OfferQuery(text = "schoko", validity = ValidityFilter.UPCOMING), today).map { it.id })
        assertTrue(index.search(OfferQuery(text = "schoko"), today).isEmpty())
        assertEquals(listOf("3"), index.search(OfferQuery(text = "lidl plus"), today).map { it.id })
        assertEquals(listOf("1"), index.search(OfferQuery(text = "käse", withoutMembership = true), today).map { it.id })
        assertEquals("3", index.search(OfferQuery(sort = SortOrder.DISCOUNT, validity = ValidityFilter.ALL), today).first().id)
    }
}
