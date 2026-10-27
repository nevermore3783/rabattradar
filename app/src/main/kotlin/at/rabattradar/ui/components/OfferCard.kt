package at.rabattradar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.rabattradar.core.model.Offer
import at.rabattradar.ui.brand.StoreLogo
import at.rabattradar.ui.brand.brand
import at.rabattradar.ui.theme.FlyerFont
import at.rabattradar.ui.validityShort
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import java.time.LocalDate

/**
 * Product picture with the flyer-style info bar in the bottom left corner. If no image is available
 * (or it fails to load) a generated tile in the chain colours is shown instead, so every offer has a picture.
 */
@Composable
fun OfferImage(
    offer: Offer,
    modifier: Modifier = Modifier,
    imageUrl: String? = offer.imageUrl ?: offer.flyerImageUrl,
    infoBarScale: Float = 1f,
    imageHeightFraction: Float = 0.74f,
    showInfoBar: Boolean = true,
) {
    var failed by remember(imageUrl) { mutableStateOf(imageUrl == null) }
    Box(modifier.background(Color.White)) {
        if (failed) {
            PlaceholderPicture(offer, Modifier.fillMaxWidth().fillMaxHeight(imageHeightFraction).align(Alignment.TopCenter))
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = offer.title,
                contentScale = ContentScale.Fit,
                onState = { if (it is AsyncImagePainter.State.Error) failed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(imageHeightFraction)
                    .align(Alignment.TopCenter)
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp),
            )
        }
        if (showInfoBar) InfoBarTag(offer, Modifier.align(Alignment.BottomStart), scale = infoBarScale)
    }
}

@Composable
private fun PlaceholderPicture(offer: Offer, modifier: Modifier) {
    val brand = offer.chain.brand
    Box(
        modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(brand.logoBg.copy(alpha = 0.18f), brand.logoBg.copy(alpha = 0.45f)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                offer.name.take(1).uppercase(),
                fontFamily = FlyerFont, fontWeight = FontWeight.Black, fontSize = 44.sp, color = brand.price,
            )
            Text(
                offer.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium, color = Color(0xFF2B3440),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun OfferCard(
    offer: Offer,
    today: LocalDate,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            OfferImage(offer, Modifier.fillMaxWidth().aspectRatio(0.86f))
            Row(
                Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                StoreLogo(offer.chain, banner = offer.banners.firstOrNull(), width = 40.dp, height = 26.dp)
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    validityShort(offer, today)?.let { ValidityPill(it, upcoming = offer.isUpcoming(today)) }
                    if (favorite) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Merkliste", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp)) {
            offer.displayBrand?.let {
                Text(
                    it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                offer.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            val sub = listOfNotNull(offer.quantity, offer.description).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            offer.unitPrice?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            ConditionChipRow(offer.conditions, max = 3)
        }
    }
}

@Composable
fun ValidityPill(text: String, upcoming: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(
                if (upcoming) Color(0xFF1F5FBF) else Color(0xE62B3440),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        Text(text, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
