package at.rabattradar.core.search

import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Offer
import at.rabattradar.core.text.Text
import java.time.LocalDate

enum class ValidityFilter(val label: String) { NOW("Jetzt gültig"), UPCOMING("Demnächst"), ALL("Alle") }

enum class SortOrder(val label: String) {
    RELEVANCE("Relevanz"),
    DISCOUNT("Höchster Rabatt"),
    PRICE("Niedrigster Preis"),
    ENDING("Endet bald"),
}

data class OfferQuery(
    val text: String = "",
    /** Empty = all chains. */
    val chains: Set<Chain> = emptySet(),
    val validity: ValidityFilter = ValidityFilter.NOW,
    /** Hide offers whose price needs jö / Lidl Plus / an app voucher. */
    val withoutMembership: Boolean = false,
    /** Hide offers that need several items (2+1, ab 2 Stk.). */
    val singleItemPrice: Boolean = false,
    val includeOnlineShop: Boolean = true,
    val favoritesOnly: Boolean = false,
    val sort: SortOrder = SortOrder.RELEVANCE,
)

/** Pre-folded search text per offer so typing stays fast with a few thousand offers. */
class OfferIndex(val offers: List<Offer>) {
    private val name = offers.associate { it.id to " " + Text.fold("${it.brand.orEmpty()} ${it.name}") }
    private val rest = offers.associate {
        it.id to " " + Text.fold(
            (
                listOfNotNull(it.description, it.quantity, it.category, it.chain.displayName, it.banners.joinToString(" ")) +
                    it.conditions.filterNot { c -> c.inferred }.map { c -> c.label }
                ).joinToString(" "),
        )
    }

    fun search(query: OfferQuery, today: LocalDate, favorites: Set<String> = emptySet()): List<Offer> {
        val tokens = Text.tokens(query.text)
        val scored = offers.asSequence()
            .filter { !it.isExpired(today) }
            .filter { query.chains.isEmpty() || it.chain in query.chains }
            .filter {
                when (query.validity) {
                    ValidityFilter.NOW -> it.isActiveOn(today)
                    ValidityFilter.UPCOMING -> it.isUpcoming(today)
                    ValidityFilter.ALL -> true
                }
            }
            .filter { !query.withoutMembership || !it.needsMembership }
            .filter { !query.singleItemPrice || !it.needsMultiple }
            .filter { query.includeOnlineShop || !(it.has(ConditionType.ONLINE_SHOP) || it.has(ConditionType.ONLINE_ONLY)) }
            .filter { !query.favoritesOnly || it.id in favorites }
            .mapNotNull { offer -> score(offer, tokens)?.let { offer to it } }
            .toList()

        val comparator: Comparator<Pair<Offer, Int>> = when (query.sort) {
            SortOrder.RELEVANCE -> compareByDescending<Pair<Offer, Int>> { it.second }
                .thenByDescending { it.first.isActiveOn(today) }
                .thenByDescending { it.first.discountPercent ?: 0 }
            SortOrder.DISCOUNT -> compareByDescending { it.first.discountPercent ?: 0 }
            SortOrder.PRICE -> compareBy { it.first.price }
            SortOrder.ENDING -> compareBy<Pair<Offer, Int>> { it.first.validTo ?: LocalDate.MAX }
                .thenByDescending { it.first.discountPercent ?: 0 }
        }
        return scored.sortedWith(comparator.thenBy { it.first.name }).map { it.first }
    }

    /** null = no match. Every token must occur (substring match handles German compounds like "Milchschokolade"). */
    private fun score(offer: Offer, tokens: List<String>): Int? {
        if (tokens.isEmpty()) return 0
        val n = name.getValue(offer.id)
        val r = rest.getValue(offer.id)
        var score = 0
        for (t in tokens) {
            score += when {
                n.contains(" $t") -> 10
                n.contains(t) -> 6
                r.contains(" $t") -> 3
                r.contains(t) -> 1
                else -> return null
            }
        }
        return score
    }
}
