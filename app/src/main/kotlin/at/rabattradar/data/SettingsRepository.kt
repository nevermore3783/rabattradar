package at.rabattradar.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.rabattradar.core.model.Chain
import at.rabattradar.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    /** Postal code for regional flyers (BILLA Wien vs. Bundesländer etc.). */
    val zipCode: String = "1010",
    val chains: Set<Chain> = Chain.entries.toSet(),
    val includeOnlineShop: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
)

class SettingsRepository(private val context: Context) {
    private val zip = stringPreferencesKey("zip")
    private val disabledChains = stringSetPreferencesKey("disabled_chains")
    private val onlineShop = booleanPreferencesKey("online_shop")
    private val favoriteIds = stringSetPreferencesKey("favorites")
    private val languageKey = stringPreferencesKey("language")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val disabled = p[disabledChains].orEmpty()
        Settings(
            zipCode = p[zip] ?: "1010",
            chains = Chain.entries.filter { it.name !in disabled }.toSet(),
            includeOnlineShop = p[onlineShop] ?: true,
            language = p[languageKey]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM,
        )
    }.distinctUntilChanged()

    val favorites: Flow<Set<String>> = context.dataStore.data.map { it[favoriteIds].orEmpty() }.distinctUntilChanged()

    suspend fun setZip(value: String) = context.dataStore.edit { it[zip] = value }

    suspend fun setChainEnabled(chain: Chain, enabled: Boolean) = context.dataStore.edit {
        val current = it[disabledChains].orEmpty()
        it[disabledChains] = if (enabled) current - chain.name else current + chain.name
    }

    suspend fun setIncludeOnlineShop(value: Boolean) = context.dataStore.edit { it[onlineShop] = value }

    suspend fun setLanguage(value: AppLanguage) = context.dataStore.edit { it[languageKey] = value.name }

    suspend fun toggleFavorite(id: String) = context.dataStore.edit {
        val current = it[favoriteIds].orEmpty()
        it[favoriteIds] = if (id in current) current - id else current + id
    }
}
