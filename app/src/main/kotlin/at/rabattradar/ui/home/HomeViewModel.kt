package at.rabattradar.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.rabattradar.RabattRadarApp
import at.rabattradar.core.SourceStatus
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Offer
import at.rabattradar.core.search.OfferIndex
import at.rabattradar.core.search.OfferQuery
import at.rabattradar.core.search.SortOrder
import at.rabattradar.core.search.ValidityFilter
import at.rabattradar.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val offers: List<Offer> = emptyList(),
    /** Matches per chain for the current query, ignoring the chain filter (shown on the chips). */
    val chainCounts: Map<Chain, Int> = emptyMap(),
    val enabledChains: List<Chain> = Chain.entries,
    val favorites: Set<String> = emptySet(),
    val fetchedAt: Long? = null,
    val failedSources: List<SourceStatus> = emptyList(),
    val today: LocalDate = LocalDate.now(VIENNA),
)

private val VIENNA: ZoneId = ZoneId.of("Europe/Vienna")

/** User actions of the home screen - implemented by [HomeViewModel], faked in screenshot tests. */
interface HomeActions {
    fun setText(text: String)
    fun toggleChain(chain: Chain)
    fun clearChains()
    fun setValidity(v: ValidityFilter)
    fun setSort(s: SortOrder)
    fun setWithoutMembership(v: Boolean)
    fun setSingleItemPrice(v: Boolean)
    fun setIncludeOnlineShop(v: Boolean)
    fun setFavoritesOnly(v: Boolean)
    fun resetFilters()
    fun refresh()
}

class HomeViewModel(app: Application) : AndroidViewModel(app), HomeActions {
    private val container = app as RabattRadarApp
    private val repo = container.offers

    private val _query = MutableStateFlow(OfferQuery())
    val query: StateFlow<OfferQuery> = _query

    private val index = repo.state
        .map { it.snapshot }
        .distinctUntilChanged()
        .map { snapshot -> snapshot?.let { OfferIndex(it.offers) } }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<HomeUiState> = combine(
        repo.state, index, _query, container.settings.settings, container.settings.favorites,
    ) { repoState, idx, q, settings, favorites ->
        val today = LocalDate.now(VIENNA)
        val effective = q.copy(includeOnlineShop = q.includeOnlineShop && settings.includeOnlineShop)
        val allChains = idx?.search(effective.copy(chains = emptySet()), today, favorites).orEmpty()
            .filter { it.chain in settings.chains }
        val selected = q.chains.intersect(settings.chains)
        HomeUiState(
            loading = repoState.snapshot == null && (repoState.refreshing || repoState.error == null),
            refreshing = repoState.refreshing,
            error = repoState.error,
            offers = if (selected.isEmpty()) allChains else allChains.filter { it.chain in selected },
            chainCounts = allChains.groupingBy { it.chain }.eachCount(),
            enabledChains = Chain.entries.filter { it in settings.chains },
            favorites = favorites,
            fetchedAt = repoState.snapshot?.fetchedAt,
            failedSources = repoState.snapshot?.statuses?.filter { !it.ok }.orEmpty(),
            today = today,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val settings: StateFlow<Settings> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    override fun setText(text: String) { _query.update { it.copy(text = text) } }
    override fun toggleChain(chain: Chain) { _query.update { q -> q.copy(chains = if (chain in q.chains) q.chains - chain else q.chains + chain) } }
    override fun clearChains() { _query.update { it.copy(chains = emptySet()) } }
    override fun setValidity(v: ValidityFilter) { _query.update { it.copy(validity = v) } }
    override fun setSort(s: SortOrder) { _query.update { it.copy(sort = s) } }
    override fun setWithoutMembership(v: Boolean) { _query.update { it.copy(withoutMembership = v) } }
    override fun setSingleItemPrice(v: Boolean) { _query.update { it.copy(singleItemPrice = v) } }
    override fun setIncludeOnlineShop(v: Boolean) { _query.update { it.copy(includeOnlineShop = v) } }
    override fun setFavoritesOnly(v: Boolean) { _query.update { it.copy(favoritesOnly = v) } }
    override fun resetFilters() { _query.update { OfferQuery(text = it.text) } }

    fun offer(id: String): Flow<Offer?> = repo.state.map { s -> s.snapshot?.offers?.firstOrNull { it.id == id } }.distinctUntilChanged()

    override fun refresh() = repo.refreshAsync()

    fun toggleFavorite(id: String) {
        viewModelScope.launch { container.settings.toggleFavorite(id) }
    }
}
