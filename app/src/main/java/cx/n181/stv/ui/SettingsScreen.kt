package cx.n181.stv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.n181.stv.BuildConfig
import cx.n181.stv.StvApp
import cx.n181.stv.data.AppConfigRepository
import cx.n181.stv.data.PlayerEngine
import cx.n181.stv.data.SourceConfig
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as StvApp).container }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    val configState by container.appConfigRepository.state.collectAsState()
    val disabledKeys by container.settingsRepository.disabledSourceKeys.collectAsState(initial = emptySet())
    var selectedEngine by remember { mutableStateOf<PlayerEngine?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        selectedEngine = container.settingsRepository.loadPlayerEngine()
        if (configState == null) container.appConfigRepository.load()
        runCatching { firstFocus.requestFocus() }
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2_500L)
            message = null
        }
    }

    val sources: List<SourceConfig> = configState?.config?.sources.orEmpty()
    val enabledCount = sources.count { it.enabled && it.key !in disabledKeys }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "设置",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                message?.let {
                    Text(text = it, color = TvFocusColor, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item(key = "engine") {
                    SectionTitle("播放内核", "默认使用的播放器；播放失败时会自动依次尝试其它内核")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusGroup()) {
                        PlayerEngine.entries.forEachIndexed { index, engine ->
                            TvChip(
                                onClick = {
                                    selectedEngine = engine
                                    scope.launch {
                                        container.settingsRepository.savePlayerEngine(engine)
                                        message = "默认内核已切换为 ${engine.label}"
                                    }
                                },
                                selected = selectedEngine == engine,
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                            ) {
                                Text(engine.label, fontSize = 15.sp)
                            }
                        }
                    }
                }

                item(key = "config") {
                    SectionTitle("配置", configDescription(configState))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusGroup()) {
                        TvOutlinedButton(
                            onClick = {
                                if (refreshing) return@TvOutlinedButton
                                scope.launch {
                                    refreshing = true
                                    val loaded = container.appConfigRepository.loaded(forceRefresh = true)
                                    refreshing = false
                                    message = when (loaded.origin) {
                                        AppConfigRepository.Origin.REMOTE -> "已更新远程配置（${loaded.config.sources.size} 个源）"
                                        AppConfigRepository.Origin.CACHED -> "远程不可用，使用上次缓存"
                                        AppConfigRepository.Origin.BUILTIN -> "远程不可用，使用内置配置"
                                    }
                                }
                            },
                            enabled = !refreshing
                        ) {
                            Text(if (refreshing) "正在刷新..." else "刷新远程配置")
                        }
                        TvOutlinedButton(
                            onClick = {
                                scope.launch {
                                    container.settingsRepository.resetDisabledSources()
                                    message = "已恢复全部资源源"
                                }
                            }
                        ) {
                            Text("恢复全部源")
                        }
                    }
                }

                item(key = "data") {
                    SectionTitle("数据", "观看记录和搜索记录只保存在本机")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusGroup()) {
                        TvOutlinedButton(
                            onClick = {
                                scope.launch {
                                    container.historyStore.clearHistory()
                                    message = "观看记录已清空"
                                }
                            }
                        ) { Text("清空观看记录") }
                        TvOutlinedButton(
                            onClick = {
                                scope.launch {
                                    container.historyStore.clearSearchHistory()
                                    message = "搜索记录已清空"
                                }
                            }
                        ) { Text("清空搜索记录") }
                    }
                }

                item(key = "sources-title") {
                    SectionTitle(
                        "资源源（$enabledCount / ${sources.size} 启用）",
                        "关闭长期失败或很慢的源可以明显加快搜索；按 确定 键切换"
                    )
                }

                if (sources.isEmpty()) {
                    item(key = "sources-empty") {
                        Text(
                            text = "正在加载配置...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp
                        )
                    }
                }

                items(sources, key = { it.key }) { source ->
                    val userDisabled = source.key in disabledKeys
                    val effectiveEnabled = source.enabled && !userDisabled
                    SourceRow(
                        source = source,
                        enabled = effectiveEnabled,
                        lockedOff = !source.enabled,
                        onToggle = {
                            if (!source.enabled) return@SourceRow
                            scope.launch {
                                container.settingsRepository.setSourceEnabled(source.key, userDisabled)
                            }
                        }
                    )
                }

                item(key = "about") {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("关于", "私人TV Android TV 客户端  v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    Text(
                        text = "遥控器快捷键：播放中按 确定 播放/暂停，左右快退/快进，上下呼出控制条，菜单键显示/隐藏控制条，返回键先关闭控制条再退出。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (hint.isNotBlank()) {
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceConfig,
    enabled: Boolean,
    lockedOff: Boolean,
    onToggle: () -> Unit
) {
    TvOutlinedButton(
        onClick = onToggle,
        selected = enabled,
        enabled = !lockedOff,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${source.order}.",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(36.dp)
        )
        Text(
            text = source.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(180.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = runCatching { java.net.URI(source.api).host }.getOrNull() ?: source.api,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = when {
                lockedOff -> "配置已停用"
                enabled -> "已启用"
                else -> "已关闭"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                lockedOff -> Color.White.copy(alpha = 0.4f)
                enabled -> TvFocusColor
                else -> MaterialTheme.colorScheme.secondary
            }
        )
    }
}

private fun configDescription(loaded: AppConfigRepository.Loaded?): String {
    if (loaded == null) return "正在加载..."
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(loaded.loadedAt))
    return "来源：${loaded.origin.label}  ·  ${loaded.config.sources.size} 个源  ·  加载于 $time  ·  ${AppConfigRepository.REMOTE_CONFIG_URL}"
}
