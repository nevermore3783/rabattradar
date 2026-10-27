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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import at.rabattradar.RabattRadarApp
import at.rabattradar.core.model.Chain
import at.rabattradar.data.OffersState
import at.rabattradar.data.Settings
import at.rabattradar.ui.brand.StoreLogo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

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
    fun refresh() = container.offers.refreshAsync()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    var zip by remember(settings.zipCode) { mutableStateOf(settings.zipCode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Group("Region") {
                Text(
                    "Flugblätter unterscheiden sich je nach Bundesland (z. B. BILLA Wien). Die Postleitzahl bestimmt, welche Flugblatt-Angebote geladen werden.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = zip,
                        onValueChange = { zip = it.filter(Char::isDigit).take(4) },
                        label = { Text("Postleitzahl") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { viewModel.setZip(zip) }, enabled = zip.length == 4 && zip != settings.zipCode) { Text("Übernehmen") }
                }
            }

            Group("Märkte") {
                Chain.entries.forEach { chain ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        StoreLogo(chain, width = 52.dp, height = 34.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chain.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(chainSources(chain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = chain in settings.chains, onCheckedChange = { viewModel.setChain(chain, it) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Online-Shop-Aktionen", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Aktionen, die nur im BILLA/INTERSPAR Online-Shop gelistet sind (ohne Flugblatt-Bestätigung).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.includeOnlineShop, onCheckedChange = viewModel::setOnlineShop)
                }
            }

            Group("Datenquellen") {
                val snapshot = offers.snapshot
                if (snapshot == null) {
                    Text("Noch keine Daten geladen.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Stand: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snapshot.fetchedAt))} · PLZ ${snapshot.zipCode} · ${snapshot.offers.size} Angebote",
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
                                Text("${s.source.displayName} – ${s.count} Angebote (${s.millis / 1000.0} s)", style = MaterialTheme.typography.titleSmall)
                                Text(s.error ?: s.source.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(onClick = viewModel::refresh, enabled = !offers.refreshing) {
                    Text(if (offers.refreshing) "Wird aktualisiert …" else "Jetzt aktualisieren")
                }
            }

            Group("So funktioniert's") {
                Text(
                    "RabattRadar lädt die Angebote direkt am Handy – ohne eigenen Server:\n\n" +
                        "• HOFER und SPAR: Flugblatt-Daten von marktguru.at (hofer.at und spar.at blockieren automatische Abfragen).\n" +
                        "• Lidl: Filial-Angebote von lidl.at inkl. Lidl Plus Preisen und exakten Aktionstagen, ergänzt um Flugblatt-Daten.\n" +
                        "• PENNY: Wochenangebote von penny.at inkl. jö/App-Preisen und Mengenrabatten.\n" +
                        "• BILLA: Aktionen aus dem BILLA Online-Shop (Mengenrabatte wie „bei 3 Tafeln je“) kombiniert mit dem Flugblatt (Zeitraum, BILLA PLUS).\n\n" +
                        "Bedingungen wie jö, Lidl Plus, SPAR-App-Gutschein, 2+1 gratis, Mindestmengen und Wochenend-Aktionen werden aus den Daten gelesen. " +
                        "Mit „?“ markierte Bedingungen sind vermutet (z. B. ein Preis von genau 2/3 des Normalpreises deutet auf „2+1 gratis“ hin).",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Keine Verbindung zu den genannten Händlern. Logos und Marken gehören den jeweiligen Inhabern. Angaben ohne Gewähr.\n" +
                        "Schrift „Barlow Condensed“ unter SIL Open Font License 1.1.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun chainSources(chain: Chain): String = when (chain) {
    Chain.BILLA -> "Flugblatt (BILLA & BILLA PLUS) + Online-Shop"
    Chain.SPAR -> "Flugblatt (SPAR, EUROSPAR, INTERSPAR) + Online-Shop"
    Chain.HOFER -> "Flugblatt"
    Chain.LIDL -> "lidl.at Filialangebote + Flugblatt"
    Chain.PENNY -> "penny.at Wochenangebote + Flugblatt"
}.let { "Quellen: $it" }

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
