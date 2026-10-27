package at.rabattradar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import at.rabattradar.ui.detail.DetailScreen
import at.rabattradar.ui.home.HomeScreen
import at.rabattradar.ui.home.HomeViewModel
import at.rabattradar.ui.settings.SettingsScreen
import at.rabattradar.ui.settings.SettingsViewModel
import at.rabattradar.ui.i18n.AppLanguage
import at.rabattradar.ui.i18n.LocalStrings
import at.rabattradar.ui.i18n.resolve
import at.rabattradar.ui.i18n.stringsFor
import at.rabattradar.ui.theme.RabattRadarTheme
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as RabattRadarApp).settings.settings
        setContent {
            val languageFlow = remember { settings.map { it.language } }
            val language by languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.SYSTEM)
            CompositionLocalProvider(LocalStrings provides stringsFor(language.resolve())) {
                RabattRadarTheme { AppNavigation() }
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    val home: HomeViewModel = viewModel()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = home,
                onOpenOffer = { id -> nav.navigate("offer/${URLEncoder.encode(id, "UTF-8")}") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("offer/{id}") { entry ->
            val id = URLDecoder.decode(entry.arguments?.getString("id").orEmpty(), "UTF-8")
            val offerFlow = remember(id) { home.offer(id) }
            val offer by offerFlow.collectAsStateWithLifecycle(initialValue = null)
            val state by home.state.collectAsStateWithLifecycle()
            DetailScreen(
                offer = offer,
                today = state.today,
                favorite = id in state.favorites,
                onBack = { nav.popBackStack() },
                onToggleFavorite = { home.toggleFavorite(id) },
            )
        }
        composable("settings") {
            val vm: SettingsViewModel = viewModel()
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
