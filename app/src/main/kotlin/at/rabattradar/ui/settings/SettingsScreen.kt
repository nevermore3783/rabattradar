package at.rabattradar.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import at.rabattradar.RabattRadarApp
import at.rabattradar.core.i18n.Texts
import at.rabattradar.core.model.Chain
import at.rabattradar.data.OffersState
import at.rabattradar.data.Settings
import at.rabattradar.ui.brand.StoreLogo
import at.rabattradar.ui.i18n.AppLanguage
import at.rabattradar.ui.i18n.LocalStrings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as RabattRadarApp
    val settings: StateFlow<Settings> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val offers: StateFlow<OffersState> = container.offers.state

    fun setZip(zip: String) = viewModelScope.launch {
        container.settings.setZip(zip)
        container.offers.refresh()
    }

    fun setChain(chain: Chain, enabled: Boolean) = viewModelScope.launch { container.settings.setChainEnabled(chain, enabled) }
    fun setOnlineShop(enabled: Boolean) = viewModelScope.launch { container.settings.setIncludeOnlineShop(enabled) }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch { container.settings.setLanguage(language) }
    fun refresh() = container.offers.refreshAsync()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    var zip by remember(settings.zipCode) { mutableStateOf(settings.zipCode) }
    val str = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str.settingsTitle) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, str.back) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Group(str.language) {
                listOf(AppLanguage.SYSTEM to str.languageSystem, AppLanguage.DE to "Deutsch", AppLanguage.EN to "English").forEach { (lang, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = settings.language == lang, role = Role.RadioButton, onClick = { viewModel.setLanguage(lang) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.language == lang, onClick = null)
                        Spacer(Modifier.size(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
            }

            Group(str.region) {
                Text(str.regionHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = zip,
                        onValueChange = { zip = it.filter(Char::isDigit).take(4) },
                        label = { Text(str.postalCode) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { viewModel.setZip(zip) }, enabled = zip.length == 4 && zip != settings.zipCode) { Text(str.apply) }
                }
            }

            Group(str.stores) {
                Chain.entries.forEach { chain ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        StoreLogo(chain, width = 52.dp, height = 34.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chain.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(str.storeSources(chain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = chain in settings.chains, onCheckedChange = { viewModel.setChain(chain, it) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(str.onlineShopOffers, style = MaterialTheme.typography.titleSmall)
                        Text(str.onlineShopOffersHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.includeOnlineShop, onCheckedChange = { viewModel.setOnlineShop(it) })
                }
            }

            Group(str.dataSources) {
                val snapshot = offers.snapshot
                if (snapshot == null) {
                    Text(str.noDataYet, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val locale = if (str.lang.name == "DE") Locale.GERMANY else Locale.UK
                    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(snapshot.fetchedAt))
                    Text(
                        str.asOf(time, snapshot.zipCode, snapshot.offers.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    snapshot.statuses.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                if (s.ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                                tint = if (s.ok) Color(0xFF1E8E3E) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(str.sourceLine(Texts.sourceName(s.source, str.lang), s.count, s.millis / 1000.0), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    s.error ?: Texts.sourceDescription(s.source, str.lang),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = viewModel::refresh, enabled = !offers.refreshing) {
                    Text(if (offers.refreshing) str.refreshing else str.refreshNow)
                }
            }

            Group(str.howItWorks) {
                Text(str.howItWorksText, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(str.legal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
