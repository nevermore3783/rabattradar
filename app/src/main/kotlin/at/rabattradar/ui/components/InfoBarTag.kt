package at.rabattradar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.infoBar
import at.rabattradar.ui.brand.brand
import at.rabattradar.ui.i18n.LocalStrings
import at.rabattradar.ui.theme.FlyerFont
import kotlin.math.roundToInt

private val ReferenceGrey = Color(0xFF8C9199)
private val LoyaltyYellow = Color(0xFFFFD200)
private val LoyaltyInk = Color(0xFF1B2A6B)

/**
 * Flyer-style price tag: reference price strip, deal headline, condition strip and the big price with
 * superscript cents - the same information hierarchy as the printed BILLA/SPAR/PENNY flyers.
 * [scale] 1f is the card size; the detail screen uses a larger one.
 */
@Composable
fun InfoBarTag(offer: Offer, modifier: Modifier = Modifier, scale: Float = 1f) {
    val bar = offer.infoBar(LocalStrings.current.lang)
    val brand = offer.chain.brand
    fun s(v: Float): TextUnit = (v * scale).sp
    fun d(v: Float): Dp = (v * scale).dp

    // Lidl Plus and SPAR App prices get the yellow "member" look; BILLA/PENNY jö prices keep the chain colours.
    val memberLook = bar.membership && offer.chain in setOf(Chain.LIDL, Chain.SPAR)
    val headlineBg = if (memberLook) LoyaltyYellow else brand.headlineBg
    val headlineFg = if (memberLook) LoyaltyInk else brand.headlineFg

    Column(
        modifier
            .width(IntrinsicSize.Max)
            .shadow(d(3f), RoundedCornerShape(topEnd = d(6f)))
            .clip(RoundedCornerShape(topEnd = d(6f))),
    ) {
        bar.reference?.let {
            Strip(it, ReferenceGrey, Color.White, s(10.5f), d(5f), d(1.5f))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(headlineBg)
                .alpha(if (bar.uncertain) 0.8f else 1f)
                .padding(horizontal = d(6f), vertical = d(1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                bar.headline,
                color = headlineFg,
                style = TextStyle(
                    fontFamily = FlyerFont, fontWeight = FontWeight.Black, fontSize = s(17f),
                    lineHeight = s(18f), letterSpacing = 0.01.em,
                ),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        bar.conditionLine?.let {
            Strip(it, brand.conditionBg, brand.conditionFg, s(10.5f), d(5f), d(1.5f))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.94f))
                .padding(horizontal = d(5f)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
        ) {
            val cents = (bar.price * 100).roundToInt()
            Text(
                "${cents / 100}",
                color = brand.price,
                style = TextStyle(fontFamily = FlyerFont, fontWeight = FontWeight.Black, fontSize = s(32f), lineHeight = s(32f), letterSpacing = (-0.02).em),
            )
            Text(
                "%02d".format(cents % 100),
                color = brand.price,
                style = TextStyle(fontFamily = FlyerFont, fontWeight = FontWeight.Black, fontSize = s(17f), lineHeight = s(19f)),
                modifier = Modifier.padding(top = d(2f), start = d(1f)),
            )
            // Small print beside the price, like "zzgl. 0,25 Pfand" on the shelf label.
            bar.depositLine?.let { deposit ->
                Column(Modifier.padding(start = d(4f), top = d(5f))) {
                    Text(deposit, color = SmallPrint, style = smallPrint(s(8.5f)), maxLines = 1)
                    bar.withDepositLine?.let { Text(it, color = brand.price, style = smallPrint(s(10f)), maxLines = 1) }
                }
            }
        }
        bar.packageLine?.let {
            Strip(it, PackageInk, Color.White, s(10f), d(5f), d(1.5f))
        }
    }
}

private val SmallPrint = Color(0xFF4A515C)
private val PackageInk = Color(0xFF2B3440)

private fun smallPrint(size: TextUnit) = TextStyle(fontFamily = FlyerFont, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size)

@Composable
private fun Strip(text: String, bg: Color, fg: Color, size: TextUnit, hPad: Dp, vPad: Dp, strike: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = fg,
            style = TextStyle(
                fontFamily = FlyerFont, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size,
                textDecoration = if (strike) TextDecoration.LineThrough else null,
            ),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
