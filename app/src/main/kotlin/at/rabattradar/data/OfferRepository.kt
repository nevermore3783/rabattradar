package at.rabattradar.data

import android.content.Context
import androidx.core.content.edit
import at.rabattradar.core.Aggregator
import at.rabattradar.core.Snapshot
import at.rabattradar.core.model.Chain
import at.rabattradar.core.net.OkHttp
import at.rabattradar.core.source.FetchContext
import at.rabattradar.core.source.MarktguruSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

data class OffersState(
    val snapshot: Snapshot? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val loadedFromCache: Boolean = false,
)

/**
 * Scrapes on the phone itself (residential IP, no server needed) and caches the merged snapshot
 * as JSON so the app opens instantly and works offline.
 */
class OfferRepository(
    context: Context,
    private val settings: SettingsRepository,
    httpClient: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "snapshot.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("marktguru", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val keyStore = object : MarktguruSource.KeyStore {
        override fun load(): MarktguruSource.Keys? {
            val api = prefs.getString("api", null) ?: return null
            val client = prefs.getString("client", null) ?: return null
            return MarktguruSource.Keys(api, client)
        }

        override fun save(keys: MarktguruSource.Keys) {
            prefs.edit { putString("api", keys.apiKey).putString("client", keys.clientKey) }
        }
    }

    private val aggregator = Aggregator.default(OkHttp(httpClient), keyStore)

    private val _state = MutableStateFlow(OffersState())
    val state: StateFlow<OffersState> = _state.asStateFlow()

    init {
        scope.launch {
            val cached = withContext(Dispatchers.IO) {
                runCatching { if (file.exists()) json.decodeFromString(Snapshot.serializer(), file.readText()) else null }.getOrNull()
            }
            if (cached != null) _state.update { it.copy(snapshot = cached, loadedFromCache = true) }
            refreshIfStale()
        }
    }

    suspend fun refreshIfStale() {
        val snapshot = _state.value.snapshot
        val s = settings.settings.first()
        val stale = snapshot == null ||
            System.currentTimeMillis() - snapshot.fetchedAt > MAX_AGE_MS ||
            snapshot.zipCode != s.zipCode
        if (stale) refresh()
    }

    fun refreshAsync() {
        scope.launch { refresh() }
    }

    suspend fun refresh() = mutex.withLock {
        _state.update { it.copy(refreshing = true, error = null) }
        try {
            val s = settings.settings.first()
            val snapshot = aggregator.fetch(FetchContext(zipCode = s.zipCode), chains = Chain.entries.toSet())
            if (snapshot.offers.isEmpty()) {
                val reasons = snapshot.statuses.filter { !it.ok }.joinToString("; ") { "${it.source.displayName}: ${it.error}" }
                _state.update { it.copy(refreshing = false, error = "Keine Angebote geladen. $reasons") }
                return@withLock
            }
            withContext(Dispatchers.IO) { file.writeText(json.encodeToString(Snapshot.serializer(), snapshot)) }
            _state.update { OffersState(snapshot = snapshot) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(refreshing = false, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    companion object {
        const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
    }
}
