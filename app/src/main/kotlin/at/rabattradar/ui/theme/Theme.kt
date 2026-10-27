package at.rabattradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import at.rabattradar.R

/** Condensed heavy face used for flyer-style price tags. */
val FlyerFont = FontFamily(
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
    Font(R.font.barlow_condensed_black, FontWeight.Black),
)

private val Light = lightColorScheme(
    primary = Color(0xFFC8102E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD9),
    onPrimaryContainer = Color(0xFF410006),
    secondary = Color(0xFF2B3440),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8EF),
    onSecondaryContainer = Color(0xFF151C25),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFE3A6),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF545B66),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF2F3F6),
    surfaceContainerHigh = Color(0xFFECEEF2),
    surfaceContainerHighest = Color(0xFFE5E8EC),
    outline = Color(0xFFC5CAD3),
    outlineVariant = Color(0xFFE1E4EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A8A),
    onPrimary = Color(0xFF5F0010),
    primaryContainer = Color(0xFF8C0A20),
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFFBFC8D6),
    onSecondary = Color(0xFF29313C),
    secondaryContainer = Color(0xFF3A4350),
    onSecondaryContainer = Color(0xFFDDE3EC),
    tertiary = Color(0xFFF5BF48),
    tertiaryContainer = Color(0xFF5F4100),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE3E3E6),
    surface = Color(0xFF1A1C20),
    onSurface = Color(0xFFE3E3E6),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFC1C6CF),
    surfaceContainerLowest = Color(0xFF0E1013),
    surfaceContainerLow = Color(0xFF181A1E),
    surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF26292E),
    surfaceContainerHighest = Color(0xFF303339),
    outline = Color(0xFF5B616B),
    outlineVariant = Color(0xFF3A3E45),
)

private val base = Typography()

private val AppTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val PriceStyle = TextStyle(fontFamily = FlyerFont, fontWeight = FontWeight.Black, fontSize = 28.sp)

@Composable
fun RabattRadarTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
