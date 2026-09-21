package cx.n181.stv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF23ADE5),
    secondary = Color(0xFFFF5F8F),
    background = Color(0xFF0B0B0F),
    surface = Color(0xFF15151C),
    surfaceVariant = Color(0xFF20202A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB9BAC3)
)

@Composable
fun PrivateTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
