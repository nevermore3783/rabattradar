package at.rabattradar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.rabattradar.core.model.Chain
import at.rabattradar.core.search.OfferQuery
import at.rabattradar.core.search.SortOrder
import at.rabattradar.core.search.ValidityFilter
import at.rabattradar.ui.brand.StoreLogo
import at.rabattradar.ui.components.OfferCard
import at.rabattradar.ui.i18n.LocalStrings
import at.rabattradar.ui.theme.FlyerFont
import at.rabattradar.core.i18n.Texts

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenOffer: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    HomeContent(state, query, viewModel, onOpenOffer, onOpenSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    query: OfferQuery,
    actions: HomeActions,
    onOpenOffer: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(query) { gridState.scrollToItem(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.statusBarsPadding().padding(top = 4.dp, bottom = 8.dp)) {
                    Header(state.refreshing, onRefresh = actions::refresh, onOpenSettings = onOpenSettings)
                    SearchField(query.text, actions::setText)
                    ChainChips(state, query, actions)
                    FilterRow(query, actions, onOpenFilters = { showFilters = true })
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing && state.offers.isNotEmpty(),
            onRefresh = actions::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> LoadingState()
                state.offers.isEmpty() && state.fetchedAt == null -> ErrorState(state.error, actions::refresh)
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 164.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ResultHeader(state) }
                    if (state.offers.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyResults(query, actions) }
                    }
                    items(state.offers, key = { it.id }) { offer ->
                        OfferCard(
                            offer = offer,
                            today = state.today,
                            favorite = offer.id in state.favorites,
                            onClick = { onOpenOffer(offer.id) },
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FilterSheet(query, actions)
        }
    }
}

@Composable
private fun Header(refreshing: Boolean, onRefresh: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Rabatt",
            fontFamily = FlyerFont, fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Radar",
            fontFamily = FlyerFont, fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
        }
        IconButton(onClick = onRefresh, enabled = !refreshing) { Icon(Icons.Filled.Refresh, LocalStrings.current.refresh) }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, LocalStrings.current.settings) }
    }
}

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    TextField(
        value = text,
        onValueChange = onChange,
        placeholder = { Text(LocalStrings.current.searchPlaceholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (text.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, LocalStrings.current.clearSearch) }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChainChips(state: HomeUiState, query: OfferQuery, actions: HomeActions) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        item {
            FilterChip(
                selected = query.chains.isEmpty(),
                onClick = actions::clearChains,
                label = { Text("${LocalStrings.current.all}  ${state.chainCounts.values.sum()}") },
                modifier = Modifier.height(40.dp),
            )
        }
        items(state.enabledChains.size) { i ->
            val chain = state.enabledChains[i]
            FilterChip(
                selected = chain in query.chains,
                onClick = { actions.toggleChain(chain) },
                leadingIcon = { StoreLogo(chain, width = 38.dp, height = 24.dp) },
                label = { Text("${state.chainCounts[chain] ?: 0}") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.height(40.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(query: OfferQuery, actions: HomeActions, onOpenFilters: () -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            ValidityFilter.entries.forEachIndexed { i, v ->
                SegmentedButton(
                    selected = query.validity == v,
                    onClick = { actions.setValidity(v) },
                    shape = SegmentedButtonDefaults.itemShape(i, ValidityFilter.entries.size),
                    icon = {},
                    label = { Text(LocalStrings.current.validity(v), maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        val active = listOf(query.withoutMembership, query.singleItemPrice, !query.includeOnlineShop, query.favoritesOnly).count { it }
        IconButton(onClick = onOpenFilters) {
            BadgedBox(badge = { if (active > 0) Badge { Text("$active") } }) { Icon(Icons.Filled.Tune, LocalStrings.current.filters) }
        }
        Box {
            IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, LocalStrings.current.sort) }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SortOrder.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.sortOrder(s), fontWeight = if (s == query.sort) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { actions.setSort(s); sortOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSheet(query: OfferQuery, actions: HomeActions) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        val s = LocalStrings.current
        Text(s.filters, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            s.filterWithoutMembership,
            s.filterWithoutMembershipHint,
            query.withoutMembership, actions::setWithoutMembership,
        )
        SwitchRow(
            s.filterSinglePrice,
            s.filterSinglePriceHint,
            query.singleItemPrice, actions::setSingleItemPrice,
        )
        SwitchRow(
            s.filterOnlineShop,
            s.filterOnlineShopHint,
            query.includeOnlineShop, actions::setIncludeOnlineShop,
        )
        SwitchRow(s.filterFavorites, s.filterFavoritesHint, query.favoritesOnly, actions::setFavoritesOnly)
        Spacer(Modifier.height(12.dp))
        Text(s.sort, style = MaterialTheme.typography.titleMedium)
        SortOrder.entries.forEach { o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = query.sort == o, onClick = { actions.setSort(o) })
                Text(s.sortOrder(o))
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = actions::resetFilters) { Text(s.resetAllFilters) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ResultHeader(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                LocalStrings.current.offers(state.offers.size),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            state.fetchedAt?.let {
                Text(LocalStrings.current.updated((System.currentTimeMillis() - it) / 60_000), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.failedSources.isNotEmpty() || state.error != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                val lang = LocalStrings.current.lang
                val names = state.failedSources.joinToString { Texts.sourceName(it.source, lang) }
                Text(
                    when {
                        state.error != null -> LocalStrings.current.updateFailed(state.error)
                        else -> LocalStrings.current.unreachable(names)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EmptyResults(query: OfferQuery, actions: HomeActions) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val s = LocalStrings.current
        Text(s.noOffers, style = MaterialTheme.typography.titleMedium)
        Text(
            if (query.validity == ValidityFilter.NOW) s.noOffersNowHint else s.noOffersHint,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
        )
        TextButton(onClick = actions::resetFilters) { Text(s.resetFilters) }
    }
}

@Composable
private fun LoadingState() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(LocalStrings.current.loading, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chain.entries.forEach { StoreLogo(it, width = 44.dp, height = 30.dp) }
        }
        Text(
            LocalStrings.current.loadingHint,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun ErrorState(error: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CloudOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(LocalStrings.current.loadFailed, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
        }
        Button(onClick = onRetry) { Text(LocalStrings.current.retry) }
    }
}
