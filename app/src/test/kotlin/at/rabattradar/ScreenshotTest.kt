package at.rabattradar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.rabattradar.core.Snapshot
import at.rabattradar.core.i18n.Lang
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.DepositKind
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.PriceTier
import at.rabattradar.core.model.TierKind
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.net.OkHttp
import at.rabattradar.core.parse.Conditions
import at.rabattradar.core.parse.Deposits
import at.rabattradar.core.model.bundle
import at.rabattradar.core.search.OfferQuery
import at.rabattradar.core.search.SortOrder
import at.rabattradar.core.search.ValidityFilter
import at.rabattradar.core.source.MarktguruSource
import at.rabattradar.ui.brand.defaultBanner
import at.rabattradar.ui.components.OfferCard
import at.rabattradar.ui.detail.DetailScreen
import at.rabattradar.ui.home.HomeActions
import at.rabattradar.ui.home.HomeContent
import at.rabattradar.ui.home.HomeUiState
import at.rabattradar.ui.i18n.LocalStrings
import at.rabattradar.ui.i18n.stringsFor
import at.rabattradar.ui.theme.RabattRadarTheme
import coil.Coil
import coil.ImageLoader
import coil.test.FakeImageLoaderEngine
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Renders the UI on the JVM (no emulator needed):
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 *
 * `infoBarVariants` uses built-in sample offers. The `showcase*` tests render real data from a CLI snapshot
 * (and download the pictures), e.g.
 *
 *   ./gradlew :cli:run --args="--out /tmp/snapshot.json"
 *   ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*' -Drabattradar.snapshot=/tmp/snapshot.json
 *
 * Images are written to app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 27)
    private val out = "build/outputs/roborazzi"

    private fun installImages(images: Map<String, Drawable>) {
        val engine = FakeImageLoaderEngine.Builder().apply {
            images.forEach { (url, drawable) -> intercept(url, drawable) }
            default(ColorDrawable(0x00000000))
        }.build()
        Coil.setImageLoader(ImageLoader.Builder(ApplicationProvider.getApplicationContext()).components { add(engine) }.build())
    }

    @Composable
    private fun Themed(lang: Lang = Lang.DE, dark: Boolean = false, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalStrings provides stringsFor(lang)) {
            RabattRadarTheme(dark = dark, content = content)
        }
    }

    @Test
    fun infoBarVariantsEnglish() = infoBarVariants(Lang.EN, "info_bar_variants_en.png")

    @Test
    fun infoBarVariants() = infoBarVariants(Lang.DE, "info_bar_variants.png")

    private fun infoBarVariants(lang: Lang, file: String) {
        installImages(emptyMap())
        compose.setContent {
            Themed(lang) {
                Column(
                    Modifier.background(MaterialTheme.colorScheme.background).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    samples.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { OfferCard(it, today, favorite = false, onClick = {}, modifier = Modifier.width(190.dp)) }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("$out/$file")
    }

    // --- Real data ---------------------------------------------------------------------------------------

    private fun snapshot(): Snapshot? {
        val path = System.getProperty("rabattradar.snapshot") ?: return null
        return Json { ignoreUnknownKeys = true }.decodeFromString(Snapshot.serializer(), File(path).readText())
    }

    private fun download(urls: Collection<String>): Map<String, Drawable> {
        val client = OkHttp.defaultClient()
        val res = ApplicationProvider.getApplicationContext<android.app.Application>().resources
        return urls.distinct().mapNotNull { url ->
            runCatching {
                val bytes = client.newCall(Request.Builder().url(url).header("User-Agent", OkHttp.USER_AGENT).build()).execute().use { it.body!!.bytes() }
                val bmp: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                url to BitmapDrawable(res, bmp)
            }.getOrNull()
        }.toMap()
    }

    private fun logoUrls() = Chain.entries.mapNotNull { MarktguruSource.logoUrl(it.defaultBanner) } +
        listOf("BILLA PLUS", "INTERSPAR", "EUROSPAR").mapNotNull { MarktguruSource.logoUrl(it) }

    /** A varied first screen: one strong offer per chain and condition type. */
    private fun pick(s: Snapshot): List<Offer> {
        val active = s.offers.filter { it.isActiveOn(today) || it.isUpcoming(today) }
        fun first(p: (Offer) -> Boolean) = active.firstOrNull(p)
        return listOfNotNull(
            first { it.chain == Chain.BILLA && it.name.contains("Milka") && it.has(ConditionType.MULTI_BUY) && it.validTo != null },
            first { it.chain == Chain.LIDL && it.has(ConditionType.LOYALTY_APP) && it.tiers.isNotEmpty() && it.sources.contains(SourceId.LIDL_WEB) },
            first { it.chain == Chain.PENNY && it.has(ConditionType.LOYALTY_CARD) },
            first { it.chain == Chain.SPAR && it.has(ConditionType.APP_COUPON) },
            first { it.chain == Chain.HOFER && it.has(ConditionType.WEEKDAYS) && (it.discountPercent ?: 0) >= 30 },
            first { it.chain == Chain.SPAR && it.conditions.any { c -> c.inferred } },
            first { it.chain == Chain.LIDL && it.has(ConditionType.MIN_QUANTITY) },
            first { it.chain == Chain.PENNY && it.has(ConditionType.MIN_QUANTITY) && !it.has(ConditionType.LOYALTY_CARD) },
            first { it.chain == Chain.BILLA && it.has(ConditionType.DEPOSIT) && it.validTo != null },
            first { it.chain == Chain.HOFER && (it.discountPercent ?: 0) >= 40 && !it.has(ConditionType.WEEKDAYS) },
        ).distinctBy { it.id }
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

    @Test
    fun showcaseHome() = showcaseHome(Lang.DE, "showcase_home.png")

    @Test
    fun showcaseHomeEnglish() = showcaseHome(Lang.EN, "showcase_home_en.png")

    private fun showcaseHome(lang: Lang, file: String) {
        val s = snapshot()
        assumeTrue("set -Drabattradar.snapshot=<file> to render real data", s != null)
        val offers = pick(s!!)
        installImages(download(offers.mapNotNull { it.imageUrl } + logoUrls()))
        val counts = s.offers.filter { it.isActiveOn(today) }.groupingBy { it.chain }.eachCount()
        compose.setContent {
            Themed(lang) {
                HomeContent(
                    state = HomeUiState(
                        loading = false, offers = offers, chainCounts = counts, fetchedAt = System.currentTimeMillis() - 12 * 60_000,
                        today = today,
                    ),
                    query = OfferQuery(),
                    actions = NoActions,
                    onOpenOffer = {},
                    onOpenSettings = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("$out/$file")
    }

    @Test
    fun showcaseSearch() {
        val s = snapshot()
        assumeTrue(s != null)
        val index = at.rabattradar.core.search.OfferIndex(s!!.offers)
        val q = OfferQuery(text = "bier", validity = ValidityFilter.ALL, sort = SortOrder.PRICE)
        val hits = index.search(q, today).take(8)
        installImages(download(hits.mapNotNull { it.imageUrl } + logoUrls()))
        val counts = index.search(q, today).groupingBy { it.chain }.eachCount()
        compose.setContent {
            RabattRadarTheme(dark = false) {
                HomeContent(
                    state = HomeUiState(loading = false, offers = hits, chainCounts = counts, fetchedAt = System.currentTimeMillis(), today = today),
                    query = q, actions = NoActions, onOpenOffer = {}, onOpenSettings = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("$out/showcase_search_bier.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h2600dp-xxhdpi")
    fun showcaseDetail() {
        val s = snapshot()
        assumeTrue(s != null)
        val offer = pick(s!!).first()
        installImages(download(listOfNotNull(offer.imageUrl, offer.flyerImageUrl) + logoUrls()))
        compose.setContent {
            RabattRadarTheme(dark = false) {
                DetailScreen(offer = offer, today = today, favorite = true, onBack = {}, onToggleFavorite = {})
            }
        }
        compose.onRoot().captureRoboImage("$out/showcase_detail.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h2600dp-xxhdpi")
    fun showcaseDetailDepositPackage() = showcaseDetailDeposit(Lang.DE, "showcase_detail_deposit.png")

    @Test
    @Config(qualifiers = "w412dp-h2600dp-xxhdpi")
    fun showcaseDetailDepositEnglish() = showcaseDetailDeposit(Lang.EN, "showcase_detail_deposit_en.png")

    private fun showcaseDetailDeposit(lang: Lang, file: String) {
        val s = snapshot()
        assumeTrue(s != null)
        val offer = s!!.offers.filter { it.deposit?.amount != null && it.bundle() != null && !it.deposit!!.inferred }
            .maxByOrNull { it.bundle()!!.quantity } ?: return
        installImages(download(listOfNotNull(offer.imageUrl, offer.flyerImageUrl) + logoUrls()))
        compose.setContent {
            Themed(lang) {
                DetailScreen(offer = offer, today = today, favorite = false, onBack = {}, onToggleFavorite = {})
            }
        }
        compose.onRoot().captureRoboImage("$out/$file")
    }

    @Test
    @Config(qualifiers = "w412dp-h2600dp-xxhdpi")
    fun showcaseDetailBillaFlyer() {
        val s = snapshot()
        assumeTrue(s != null)
        val offer = s!!.offers.firstOrNull { SourceId.BILLA_FLYER in it.sources && it.brand == "Coca-Cola" && it.price == 1.24 }
            ?: s.offers.first { SourceId.BILLA_FLYER in it.sources }
        installImages(download(listOfNotNull(offer.imageUrl, offer.flyerImageUrl) + logoUrls()))
        compose.setContent {
            Themed(Lang.DE, dark = true) {
                DetailScreen(offer = offer, today = today, favorite = false, onBack = {}, onToggleFavorite = {})
            }
        }
        compose.onRoot().captureRoboImage("$out/showcase_detail_billa_flyer.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h2600dp-xxhdpi")
    fun showcaseDetailLidlPlus() {
        val s = snapshot()
        assumeTrue(s != null)
        val offer = pick(s!!).first { it.chain == Chain.LIDL }
        installImages(download(listOfNotNull(offer.imageUrl, offer.flyerImageUrl) + logoUrls()))
        compose.setContent {
            RabattRadarTheme(dark = true) {
                DetailScreen(offer = offer, today = today, favorite = false, onBack = {}, onToggleFavorite = {})
            }
        }
        compose.onRoot().captureRoboImage("$out/showcase_detail_lidl_dark.png")
    }

    // --- Samples -----------------------------------------------------------------------------------------

    private val samples = listOf(
        Offer(
            id = "1", chain = Chain.BILLA, banners = listOf("BILLA", "BILLA PLUS"), name = "Milka Alpenmilch", brand = "Milka",
            quantity = "270 g", price = 3.32, regularPrice = 4.99, discountPercent = 33,
            tiers = listOf(PriceTier(TierKind.SINGLE, 4.99, unit = "Tafel")),
            conditions = listOf(
                Conditions.multiBuyCondition(2, 1).copy(unit = "Tafel"),
                Conditions.minQuantity("Bei 3 Tafeln je", perSet = true)!!,
            ),
            validFrom = today.minusDays(5), validTo = today.plusDays(1), sources = listOf(SourceId.BILLA_SHOP, SourceId.MARKTGURU),
        ),
        Offer(
            id = "2", chain = Chain.LIDL, banners = listOf("Lidl"), name = "Frischkäse", brand = "Philadelphia", quantity = "175 g",
            price = 1.24, regularPrice = 1.89, discountPercent = 34, tiers = listOf(PriceTier(TierKind.WITHOUT, 1.69, program = "Lidl Plus")),
            conditions = listOf(Conditions.lidlPlusCondition(1.24)), validFrom = today, validTo = today.plusDays(1),
        ),
        Offer(
            id = "3", chain = Chain.PENNY, banners = listOf("PENNY"), name = "Frischkäse Knoblauch", quantity = "150 g",
            price = 1.49, regularPrice = 2.59, tiers = listOf(PriceTier(TierKind.SINGLE, 2.59, unit = "Becher"), PriceTier(TierKind.WITHOUT, 1.72, program = "jö")),
            conditions = listOf(Conditions.joeCondition(Chain.PENNY, 1.49), Conditions.minQuantity("bei 3 Becher")!!),
            validFrom = today.minusDays(5), validTo = today.plusDays(1),
        ),
        Offer(
            id = "4", chain = Chain.SPAR, banners = listOf("SPAR", "EUROSPAR"), name = "Bio-Roggenbrot", description = "500 g",
            price = 2.15, regularPrice = 2.37, tiers = listOf(PriceTier(TierKind.WITHOUT, 2.37, program = "voucher")), discountPercent = 9,
            conditions = listOf(Conditions.sparAppCondition(2.15)), validFrom = today.minusDays(6), validTo = today.plusDays(8),
        ),
        Offer(
            id = "5", chain = Chain.HOFER, banners = listOf("HOFER"), name = "Heidelbeeren", description = "Klasse I, 300 g",
            price = 1.99, regularPrice = 3.99, discountPercent = 50,
            conditions = listOf(Conditions.weekdays(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31))!!),
            validFrom = LocalDate.of(2026, 10, 30), validTo = LocalDate.of(2026, 10, 31),
        ),
        Offer(
            id = "6", chain = Chain.SPAR, banners = listOf("SPAR"), name = "Eistee", brand = "Spar", description = "Pfirsich oder Zitrone 0,75 l",
            price = 0.66, regularPrice = 0.99, discountPercent = 33, conditions = listOf(Conditions.inferMultiBuy(0.66, 0.99)!!),
            validFrom = today.minusDays(6), validTo = today.plusDays(8),
        ),
        Offer(
            id = "7", chain = Chain.PENNY, banners = listOf("PENNY"), name = "Wieselburger Gold", quantity = "0,5 l Dose",
            price = 0.79, regularPrice = 1.45, discountPercent = 46, tiers = listOf(PriceTier(TierKind.SINGLE, 1.45, unit = "Dose")),
            conditions = listOf(Conditions.minQuantity("ab 24 Dosen")!!),
            validFrom = today.minusDays(5), validTo = today.plusDays(1),
        ).withDeposit(Deposits.detect("0,5 l Dose Bier", DepositKind.SINGLE_USE)),
        Offer(
            id = "8", chain = Chain.LIDL, banners = listOf("Lidl"), name = "Monte Maxi", quantity = "4x 100 g", price = 1.39,
            regularPrice = 2.79, discountPercent = 50, tiers = listOf(PriceTier(TierKind.SINGLE, 2.79)),
            conditions = listOf(Conditions.minQuantity("Bei 2 Stk. je")!!), validFrom = today.plusDays(6), validTo = today.plusDays(8),
        ),
    )
}
