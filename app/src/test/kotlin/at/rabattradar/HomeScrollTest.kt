package at.rabattradar

import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Offer
import at.rabattradar.core.search.OfferQuery
import at.rabattradar.core.search.SortOrder
import at.rabattradar.core.search.ValidityFilter
import at.rabattradar.ui.home.HomeActions
import at.rabattradar.ui.home.HomeContent
import at.rabattradar.ui.home.HomeUiState
import at.rabattradar.ui.theme.RabattRadarTheme
import coil.Coil
import coil.ImageLoader
import coil.test.FakeImageLoaderEngine
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The offer list keeps its scroll position when coming back from an offer, and jumps to the top on a new search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class HomeScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private val offers = (0 until 60).map { i ->
        Offer(id = "o$i", chain = Chain.entries[i % Chain.entries.size], name = "Produkt Nummer $i", price = 1.0 + i, regularPrice = 2.0 + i)
    }

    private object NoActions : HomeActions {
        override fun setText(text: String) = Unit
        override fun toggleChain(chain: Chain) = Unit
        override fun clearChains() = Unit
        override fun setValidity(v: ValidityFilter) = Unit
        override fun setSort(s: SortOrder) = Unit
        override fun setWithoutMembership(v: Boolean) = Unit
        override fun setSingleItemPrice(v: Boolean) = Unit
        override fun setIncludeOnlineShop(v: Boolean) = Unit
        override fun setFavoritesOnly(v: Boolean) = Unit
        override fun resetFilters() = Unit
        override fun refresh() = Unit
    }

    @Before
    fun images() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorDrawable(0)).build()
        Coil.setImageLoader(ImageLoader.Builder(ApplicationProvider.getApplicationContext()).components { add(engine) }.build())
    }

    @Test
    fun `position survives opening an offer and is reset by a new query`() {
        lateinit var nav: NavHostController
        var query by mutableStateOf(OfferQuery())
        val state = HomeUiState(loading = false, offers = offers, fetchedAt = 0L, today = LocalDate.of(2026, 10, 27))
        compose.setContent {
            nav = rememberNavController()
            RabattRadarTheme(dark = false) {
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeContent(state, query, NoActions, onOpenOffer = { nav.navigate("offer/$it") }, onOpenSettings = {})
                    }
                    composable("offer/{id}") { Text("Detail") }
                }
            }
        }

        // Header item + 40 offers down.
        // The offer grid, not the horizontal chain chips.
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(41)
        compose.onNodeWithText("Produkt Nummer 40").performClick()
        compose.onNodeWithText("Detail").assertExists()

        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Produkt Nummer 40").assertExists()
        compose.onNodeWithText("Produkt Nummer 0").assertDoesNotExist()

        compose.runOnIdle { query = OfferQuery(text = "produkt") }
        compose.onNodeWithText("Produkt Nummer 0").assertExists()
    }
}
