package cx.n181.stv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

val TvFocusColor = Color(0xFF4DE1FF)

/**
 * 统一的电视焦点效果：放大 + 描边 + 阴影 + 抬高 zIndex。
 *
 * 之前直接用 Modifier.scale()，放大后会被相邻卡片盖住（没有 zIndex），
 * 也没有过渡动画，遥控器快速移动时会闪。
 */
fun Modifier.tvFocusFrame(
    focused: Boolean,
    shape: Shape,
    scale: Float = 1.06f,
    borderWidth: Dp = 3.dp,
    borderColor: Color = TvFocusColor
): Modifier = composed {
    val animatedScale by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "tvFocusScale"
    )
    this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .then(
            if (focused) Modifier.shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = borderColor,
                spotColor = borderColor
            ) else Modifier
        )
        .border(
            width = if (focused) borderWidth else 0.dp,
            color = if (focused) borderColor.copy(alpha = 0.95f) else Color.Transparent,
            shape = shape
        )
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
            .tvFocusFrame(focused, shape),
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
    selected: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else -> MaterialTheme.colorScheme.surface
            },
            contentColor = when {
                selected || focused -> Color.White
                else -> MaterialTheme.colorScheme.onSurface
            },
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
            .tvFocusFrame(focused, shape, scale = 1.05f, borderWidth = 2.dp),
        enabled = enabled,
        shape = shape,
        color = when {
            selected -> MaterialTheme.colorScheme.primary
            focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            focused -> Color.White
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
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
            .tvFocusFrame(focused, shape, scale = 1.04f),
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
    iconSize: Dp = 26.dp
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (focused) TvFocusColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
