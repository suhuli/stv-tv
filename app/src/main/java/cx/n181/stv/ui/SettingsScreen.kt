package cx.n181.stv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.n181.stv.StvApp
import cx.n181.stv.data.PlayerEngine
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as StvApp).container }
    val scope = rememberCoroutineScope()
    var sourceText by remember { mutableStateOf("加载中...") }
    var selectedEngine by remember { mutableStateOf<PlayerEngine?>(null) }

    LaunchedEffect(Unit) {
        selectedEngine = container.settingsRepository.loadPlayerEngine()
        val config = container.appConfigRepository.load()
        sourceText = buildString {
            appendLine("已启用 ${config.sources.count { it.enabled }} / ${config.sources.size} 个源")
            config.sources.filter { it.enabled }.forEach { source ->
                appendLine("${source.order}. ${source.name}")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
                Text(
                    text = "设置",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "播放内核",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerEngine.entries.forEach { engine ->
                    TvChip(
                        onClick = {
                            selectedEngine = engine
                            scope.launch {
                                container.settingsRepository.savePlayerEngine(engine)
                            }
                        },
                        enabled = selectedEngine != engine,
                        selected = selectedEngine == engine
                    ) {
                        Text(
                            when (engine) {
                                PlayerEngine.EXO -> "ExoPlayer"
                                PlayerEngine.IJK -> "IJKPlayer"
                                PlayerEngine.SYSTEM -> "系统播放器"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "当前资源源",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = sourceText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
