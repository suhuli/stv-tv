package cx.n181.stv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TvFocusColor = Color(0xFF4DE1FF)

private fun Modifier.tvFocusBorder(focused: Boolean, shape: Shape): Modifier {
    return border(
        width = if (focused) 3.dp else 0.dp,
        color = if (focused) TvFocusColor.copy(alpha = 0.92f) else Color.Transparent,
        shape = shape
    )
}

@Composable
private fun Modifier.tvFocusScale(focused: Boolean, scale: Float): Modifier {
    return if (focused) scale(scale) else this
}

@Composable
private fun Modifier.tvFocusShadow(focused: Boolean, shape: Shape): Modifier {
    return if (focused) {
        shadow(elevation = 12.dp, shape = shape, ambientColor = TvFocusColor, spotColor = TvFocusColor)
    } else {
        this
    }
}

@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium

    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused, 1.06f)
            .tvFocusShadow(focused, shape)
            .tvFocusBorder(focused, shape),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun TvOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused, 1.06f)
            .tvFocusShadow(focused, shape)
            .tvFocusBorder(focused, shape),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surface,
            contentColor = if (focused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun TvChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)

    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused, 1.05f)
            .tvFocusShadow(focused, shape)
            .tvFocusBorder(focused, shape),
        enabled = enabled,
        shape = shape,
        color = when {
            selected -> MaterialTheme.colorScheme.primary
            focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused, 1.04f)
            .tvFocusShadow(focused, shape)
            .tvFocusBorder(focused, shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        content()
    }
}

@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    iconSize: Dp = 24.dp
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused, 1.06f)
            .tvFocusShadow(focused, shape)
            .tvFocusBorder(focused, shape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FocusPreview() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = {}) { Text("主要") }
                TvOutlinedButton(onClick = {}) { Text("次级") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvChip(onClick = {}, selected = true) { Text("已选") }
                TvChip(onClick = {}) { Text("未选") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvCard(onClick = {}, modifier = Modifier.width(180.dp).height(90.dp)) {}
            }
        }
    }
}
