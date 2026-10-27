package at.rabattradar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.rabattradar.core.model.Condition
import at.rabattradar.core.model.ConditionType
import at.rabattradar.ui.i18n.LocalStrings

data class ConditionStyle(val icon: ImageVector, val bg: Color, val fg: Color)

/** Icon and colour per condition type (titles: [at.rabattradar.ui.i18n.UiStrings.conditionTitle]). */
fun ConditionType.style(): ConditionStyle = when (this) {
    ConditionType.LOYALTY_APP -> ConditionStyle(Icons.Filled.PhoneAndroid, Color(0xFFFFF3B0), Color(0xFF3D3200))
    ConditionType.LOYALTY_CARD -> ConditionStyle(Icons.Filled.CreditCard, Color(0xFFFFE2B8), Color(0xFF4A2800))
    ConditionType.APP_COUPON -> ConditionStyle(Icons.Filled.ConfirmationNumber, Color(0xFFD5F2DF), Color(0xFF00391A))
    ConditionType.MULTI_BUY -> ConditionStyle(Icons.Filled.LibraryAdd, Color(0xFFFFD9DC), Color(0xFF5C0010))
    ConditionType.MIN_QUANTITY -> ConditionStyle(Icons.Filled.Layers, Color(0xFFFFD9DC), Color(0xFF5C0010))
    ConditionType.WEEKDAYS -> ConditionStyle(Icons.Filled.Event, Color(0xFFDCE6FF), Color(0xFF0F2A66))
    ConditionType.ONLINE_ONLY -> ConditionStyle(Icons.Filled.Language, Color(0xFFE6E0F8), Color(0xFF2D1F66))
    ConditionType.ONLINE_SHOP -> ConditionStyle(Icons.Filled.ShoppingCart, Color(0xFFE9EBEF), Color(0xFF2B3440))
    ConditionType.CLEARANCE -> ConditionStyle(Icons.Filled.Sell, Color(0xFFFFE0CC), Color(0xFF552000))
    ConditionType.WEIGHT_PRICE -> ConditionStyle(Icons.Filled.Scale, Color(0xFFE9EBEF), Color(0xFF2B3440))
    ConditionType.DEPOSIT -> ConditionStyle(Icons.Filled.Recycling, Color(0xFFE0F2EE), Color(0xFF003D33))
    ConditionType.VARIANTS -> ConditionStyle(Icons.Filled.Category, Color(0xFFE9EBEF), Color(0xFF2B3440))
    ConditionType.NO_DATES -> ConditionStyle(Icons.Filled.EventBusy, Color(0xFFE9EBEF), Color(0xFF2B3440))
    ConditionType.NOTE -> ConditionStyle(Icons.Filled.Info, Color(0xFFE9EBEF), Color(0xFF2B3440))
}

/** Conditions that matter when deciding whether the price applies to you. */
private val important = setOf(
    ConditionType.LOYALTY_APP, ConditionType.LOYALTY_CARD, ConditionType.APP_COUPON, ConditionType.MULTI_BUY,
    ConditionType.MIN_QUANTITY, ConditionType.WEEKDAYS, ConditionType.ONLINE_ONLY, ConditionType.CLEARANCE,
    ConditionType.WEIGHT_PRICE, ConditionType.DEPOSIT, ConditionType.ONLINE_SHOP, ConditionType.NO_DATES,
)

@Composable
fun ConditionChip(condition: Condition, modifier: Modifier = Modifier) {
    val style = condition.type.style()
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .background(if (condition.inferred) Color.Transparent else style.bg, shape)
            .then(if (condition.inferred) Modifier.border(1.dp, style.fg.copy(alpha = 0.5f), shape) else Modifier)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(style.icon, contentDescription = null, tint = if (condition.inferred) MaterialTheme.colorScheme.onSurfaceVariant else style.fg, modifier = Modifier.size(12.dp))
        Text(
            condition.label(LocalStrings.current.lang),
            color = if (condition.inferred) MaterialTheme.colorScheme.onSurfaceVariant else style.fg,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionChipRow(conditions: List<Condition>, modifier: Modifier = Modifier, max: Int = 4) {
    val shown = conditions.filter { it.type in important }.take(max)
    if (shown.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { ConditionChip(it) }
    }
}
