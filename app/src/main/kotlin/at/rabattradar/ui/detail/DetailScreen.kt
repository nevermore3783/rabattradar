package at.rabattradar.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import at.rabattradar.core.model.ConditionType
import at.rabattradar.core.model.Bundle
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.bundle
import at.rabattradar.core.i18n.Texts
import at.rabattradar.ui.brand.StoreLogo
import at.rabattradar.ui.brand.brand
import at.rabattradar.ui.components.OfferImage
import at.rabattradar.ui.components.ValidityPill
import at.rabattradar.ui.components.style
import at.rabattradar.ui.remainingText
import at.rabattradar.ui.i18n.LocalStrings
import at.rabattradar.ui.i18n.UiStrings
import at.rabattradar.ui.theme.FlyerFont
import at.rabattradar.ui.validityLong
import at.rabattradar.ui.validityShort
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    offer: Offer?,
    today: LocalDate,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current
    val str = LocalStrings.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(offer?.chain?.displayName ?: "", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, str.back) } },
                actions = {
                    if (offer != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (favorite) str.removeFromWatchlist else str.addToWatchlist,
                                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            val text = shareText(offer, today, str)
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
                            )
                        }) { Icon(Icons.Filled.Share, str.share) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (offer == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(str.offerGone, style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().background(Color.White), contentAlignment = Alignment.Center) {
                OfferImage(
                    offer,
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1f),
                    infoBarScale = 1.55f,
                    imageHeightFraction = 0.8f,
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StoreRow(offer, today)
                Titles(offer)
                PriceBlock(offer)
                Conditions(offer)
                Validity(offer, today)
                FlyerCrop(offer)
                Sources(offer)
                val url = offer.url ?: offer.chain.website
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (offer.url != null) str.viewAtStore else str.storeOffers(offer.chain))
                }
                Text(
                    str.disclaimer,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StoreRow(offer: Offer, today: LocalDate) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StoreLogo(offer.chain, banner = offer.banners.firstOrNull(), width = 64.dp, height = 42.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(LocalStrings.current.validAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                offer.banners.ifEmpty { listOf(offer.chain.displayName) }.joinToString(", "),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        validityShort(offer, today, LocalStrings.current)?.let { ValidityPill(it, upcoming = offer.isUpcoming(today)) }
    }
}

@Composable
private fun Titles(offer: Offer) {
    Column {
        offer.displayBrand?.let {
            Text(it.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(offer.name, style = MaterialTheme.typography.headlineSmall)
        (offer.details + listOfNotNull(offer.category)).forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PriceBlock(offer: Offer) {
    val str = LocalStrings.current
    val color = offer.chain.brand.price
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    str.money(offer.price),
                    fontFamily = FlyerFont, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 40.sp, maxLines = 1, softWrap = false,
                    color = if (MaterialTheme.colorScheme.background.luminanceIsDark()) MaterialTheme.colorScheme.primary else color,
                )
                Spacer(Modifier.size(10.dp))
                // The text column takes the flexible width, so the discount badge never gets squeezed.
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    offer.regularPrice?.let {
                        Text(
                            str.money(it),
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DepositSmallPrint(offer)
                }
                Spacer(Modifier.size(8.dp))
                offer.discountPercent?.let {
                    Text(
                        "-$it%",
                        maxLines = 1, softWrap = false,
                        color = Color.White, fontFamily = FlyerFont, fontWeight = FontWeight.Black, fontSize = 22.sp,
                        modifier = Modifier.background(Color(0xFFE2001A), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
            }
            val headlineNeeds = offer.conditions.filter {
                it.type in setOf(ConditionType.LOYALTY_APP, ConditionType.LOYALTY_CARD, ConditionType.APP_COUPON, ConditionType.MULTI_BUY, ConditionType.MIN_QUANTITY) && !it.inferred
            }
            if (headlineNeeds.isNotEmpty()) {
                Text(
                    str.priceOnlyWith + headlineNeeds.joinToString(" + ") { it.label(str.lang) },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            offer.bundle(str.lang)?.let { PackagePrice(it, color) }
            offer.unitPrice?.let {
                Text("${str.unitPrice}: $it €", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (offer.tiers.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                offer.tiers.forEach { tier ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(tier.label(str.lang), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(str.money(tier.price), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** "+ 0,25 € Pfand = 1,04 €" in small print next to the price. */
@Composable
private fun DepositSmallPrint(offer: Offer) {
    val d = offer.deposit ?: return
    val str = LocalStrings.current
    val mark = if (d.inferred) " ${str.inferred}" else ""
    val text = (d.amount?.let { str.depositSmall(it, offer.price + it) } ?: str.returnableDeposit) + mark
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** What a multi-buy really costs: "Paketpreis 3 Tafeln 9,96 €". */
@Composable
private fun PackagePrice(bundle: Bundle, color: Color) {
    val str = LocalStrings.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str.packagePrice, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        bundle.label + if (bundle.inferred) " ${str.inferred}" else "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    str.money(bundle.price),
                    fontFamily = FlyerFont, fontWeight = FontWeight.Black, fontSize = 26.sp,
                    color = if (MaterialTheme.colorScheme.background.luminanceIsDark()) MaterialTheme.colorScheme.primary else color,
                )
            }
            bundle.deposit?.let {
                Text(
                    str.packageDeposit(it, bundle.total),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                str.packageHint(bundle.quantity),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Color.luminanceIsDark(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

@Composable
private fun Conditions(offer: Offer) {
    val str = LocalStrings.current
    Section(str.conditionsTitle) {
        if (offer.conditions.isEmpty()) {
            Text(
                str.noConditions,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        offer.conditions.forEach { c ->
            val style = c.type.style()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(36.dp).background(style.bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(style.icon, null, tint = style.fg, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        c.label(str.lang) + if (c.inferred) "  ${str.inferred}" else "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(str.conditionTitle(c.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    c.detail(str.lang)?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Validity(offer: Offer, today: LocalDate) {
    val str = LocalStrings.current
    Section(str.periodTitle) {
        Text(validityLong(offer, str), style = MaterialTheme.typography.titleSmall)
        remainingText(offer, today, str)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (offer.validTo == null) {
            Text(
                str.noEndDate,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlyerCrop(offer: Offer) {
    val crop = offer.flyerImageUrl ?: return
    if (crop == offer.imageUrl) return
    Section(LocalStrings.current.flyerExcerpt) {
        OfferImage(
            offer,
            Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            imageUrl = crop,
            infoBarScale = 1.2f,
            imageHeightFraction = 0.82f,
        )
    }
}

@Composable
private fun Sources(offer: Offer) {
    val lang = LocalStrings.current.lang
    Section(LocalStrings.current.sourcesTitle) {
        offer.sources.forEach { s ->
            Text("• ${Texts.sourceName(s, lang)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(Texts.sourceDescription(s, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

private fun shareText(offer: Offer, today: LocalDate, s: UiStrings): String {
    val bundle = offer.bundle(s.lang)
    val conds = offer.conditions.filter { !it.inferred && it.type != ConditionType.NOTE }.joinToString(", ") { it.label(s.lang) }
    val text = s.shareText(
        chain = offer.chain.displayName, title = offer.title, price = offer.price, regular = offer.regularPrice,
        deposit = offer.deposit?.amount, pkg = bundle?.let { "${it.label}: ${s.money(it.total)}" },
        conditions = conds, validity = validityShort(offer, today, s),
    )
    return offer.url?.let { "$text\n$it" } ?: text
}
