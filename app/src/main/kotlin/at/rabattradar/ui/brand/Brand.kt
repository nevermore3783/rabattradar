package at.rabattradar.ui.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.rabattradar.core.model.Chain
import at.rabattradar.core.source.MarktguruSource
import at.rabattradar.ui.theme.FlyerFont
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/** Colours of the flyer-style price tag, loosely following each chain's own flyers. */
data class ChainBrand(
    /** Deal headline block ("2+1 GRATIS", "-45%"). */
    val headlineBg: Color,
    val headlineFg: Color,
    /** Condition strip ("BEI 3 TAFELN JE"). */
    val conditionBg: Color,
    val conditionFg: Color,
    val price: Color,
    /** Fallback logo when the official one cannot be loaded. */
    val logoBg: Color,
    val logoFg: Color,
    val shortName: String,
)

val Chain.brand: ChainBrand
    get() = when (this) {
        Chain.BILLA -> ChainBrand(
            headlineBg = Color(0xFFFFE500), headlineFg = Color(0xFFE2001A),
            conditionBg = Color(0xFFE2001A), conditionFg = Color.White,
            price = Color(0xFFE2001A), logoBg = Color(0xFFFFE500), logoFg = Color(0xFFE2001A), shortName = "BILLA",
        )
        Chain.SPAR -> ChainBrand(
            headlineBg = Color(0xFFE2001A), headlineFg = Color.White,
            conditionBg = Color(0xFF00843D), conditionFg = Color.White,
            price = Color(0xFFE2001A), logoBg = Color(0xFFE2001A), logoFg = Color.White, shortName = "SPAR",
        )
        Chain.HOFER -> ChainBrand(
            headlineBg = Color(0xFFFFB000), headlineFg = Color(0xFF0A1E5B),
            conditionBg = Color(0xFF0A1E5B), conditionFg = Color.White,
            price = Color(0xFF0A1E5B), logoBg = Color(0xFF0A1E5B), logoFg = Color.White, shortName = "HOFER",
        )
        Chain.LIDL -> ChainBrand(
            headlineBg = Color(0xFFFFF000), headlineFg = Color(0xFFE60A14),
            conditionBg = Color(0xFF0050AA), conditionFg = Color.White,
            price = Color(0xFF0050AA), logoBg = Color(0xFF0050AA), logoFg = Color(0xFFFFF000), shortName = "LIDL",
        )
        Chain.PENNY -> ChainBrand(
            headlineBg = Color(0xFFCD1414), headlineFg = Color(0xFFFFED00),
            conditionBg = Color(0xFF8E0B0B), conditionFg = Color.White,
            price = Color(0xFFCD1414), logoBg = Color(0xFFCD1414), logoFg = Color.White, shortName = "PENNY",
        )
    }

val Chain.defaultBanner: String
    get() = when (this) {
        Chain.BILLA -> "BILLA"
        Chain.SPAR -> "SPAR"
        Chain.HOFER -> "HOFER"
        Chain.LIDL -> "Lidl"
        Chain.PENNY -> "PENNY"
    }

/**
 * Official store logo (served by marktguru's CDN, cached by Coil) on a white tile.
 * Falls back to a drawn word mark in the chain's colours while loading or offline.
 */
@Composable
fun StoreLogo(
    chain: Chain,
    modifier: Modifier = Modifier,
    banner: String? = null,
    width: Dp = 44.dp,
    height: Dp = 30.dp,
) {
    val url = MarktguruSource.logoUrl(banner ?: chain.defaultBanner) ?: MarktguruSource.logoUrl(chain.defaultBanner)
    var loaded by remember(url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(width, height)
            .clip(shape)
            .background(if (loaded) Color.White else chain.brand.logoBg)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                chain.brand.shortName,
                color = chain.brand.logoFg,
                fontFamily = FlyerFont,
                fontWeight = FontWeight.Black,
                fontSize = (height.value * 0.42f).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        AsyncImage(
            model = url,
            contentDescription = banner ?: chain.displayName,
            contentScale = ContentScale.Fit,
            onState = { loaded = it is AsyncImagePainter.State.Success },
            modifier = Modifier.padding(2.dp),
        )
    }
}
