package cx.n181.stv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cx.n181.stv.StvApp
import cx.n181.stv.data.HistoryItem
import cx.n181.stv.data.PlayGroup
import cx.n181.stv.data.SourceConfig
import cx.n181.stv.data.VideoDetail
import cx.n181.stv.data.activeSources
import cx.n181.stv.data.formatClock
import kotlinx.coroutines.flow.first

@Composable
fun DetailScreen(
    sourceKey: String,
    videoId: String,
    onPlay: (Int, Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as StvApp).container
    }
    val primaryActionFocus = remember { FocusRequester() }

    var sourceConfig by remember { mutableStateOf<SourceConfig?>(null) }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var history by remember { mutableStateOf<HistoryItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedGroupIndex by remember { mutableStateOf(0) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(sourceKey, videoId, reloadTick) {
        isLoading = true
        errorMessage = null
        try {
            val config = container.appConfigRepository.load()
            val disabled = container.settingsRepository.disabledSourceKeys.first()
            val source = config.activeSources(disabled).firstOrNull { it.key == sourceKey }
                ?: config.sources.firstOrNull { it.key == sourceKey }
                ?: throw IllegalStateException("资源源「$sourceKey」不存在或已停用")

            sourceConfig = source
            val loaded = container.macCmsClient.detail(source, videoId)
            detail = loaded
            history = container.historyStore.find(sourceKey, videoId)
                ?: container.historyStore.findByTitle(loaded.summary.title)
                    ?.takeIf { it.sourceKey == sourceKey }
            selectedGroupIndex = history?.let { loaded.groupOf(it.episodeIndex) }
                ?: preferredGroupIndex(loaded.playGroups)
        } catch (error: Exception) {
            errorMessage = error.message ?: "加载失败"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(isLoading, detail) {
        if (!isLoading && detail != null) {
            runCatching { primaryActionFocus.requestFocus() }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = detail?.summary?.title ?: "影片详情",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            when {
                isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = TvFocusColor
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "正在加载详情...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }

                errorMessage != null -> {
                    Column {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvButton(
                                onClick = { reloadTick += 1 },
                                modifier = Modifier.focusRequester(primaryActionFocus)
                            ) { Text("重试") }
                            TvOutlinedButton(onClick = onBack) { Text("返回") }
                        }
                    }
                }

                else -> {
                    val currentDetail = detail
                    val currentSource = sourceConfig
                    if (currentDetail == null || currentSource == null) {
                        Text(
                            text = "没有可用内容",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    } else {
                        DetailContent(
                            detail = currentDetail,
                            source = currentSource,
                            history = history,
                            selectedGroupIndex = selectedGroupIndex,
                            onSelectGroup = { selectedGroupIndex = it },
                            primaryActionFocus = primaryActionFocus,
                            onPlay = onPlay
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: VideoDetail,
    source: SourceConfig,
    history: HistoryItem?,
    selectedGroupIndex: Int,
    onSelectGroup: (Int) -> Unit,
    primaryActionFocus: FocusRequester,
    onPlay: (Int, Long) -> Unit
) {
    val groups = detail.playGroups
    val currentGroup = groups.getOrNull(selectedGroupIndex)
    val allEpisodes = detail.allEpisodes
    val resumeEpisode = history?.let { detail.episodeAt(it.episodeIndex) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(currentGroup, resumeEpisode) {
        val group = currentGroup ?: return@LaunchedEffect
        val position = group.episodes.indexOfFirst { it.index == resumeEpisode?.index }
        if (position > 0) runCatching { gridState.scrollToItem(position) }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左：海报 + 简介
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .size(width = 300.dp, height = 400.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = detail.summary.poster,
                    contentDescription = detail.summary.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = listOfNotNull(
                    detail.summary.year,
                    detail.summary.typeName,
                    detail.summary.remarks
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "来源：${source.name}",
                color = TvFocusColor.copy(alpha = 0.85f),
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(36.dp))

        // 右：操作 + 线路 + 选集
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            detail.description?.let { description ->
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (allEpisodes.isEmpty()) {
                Text(
                    text = "这个源没有解析到可播放的剧集，请返回换一个源试试",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 16.sp
                )
                return@Column
            }

            // 主操作
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusGroup()
            ) {
                val resumePosition = history?.positionMs ?: 0L
                if (resumeEpisode != null && resumePosition > 3_000L) {
                    TvButton(
                        onClick = { onPlay(resumeEpisode.index, resumePosition) },
                        modifier = Modifier.focusRequester(primaryActionFocus)
                    ) {
                        Text("继续播放  ${resumeEpisode.name}  ${formatClock(resumePosition)}", fontSize = 16.sp)
                    }
                    TvOutlinedButton(onClick = { onPlay(allEpisodes.first().index, 0L) }) {
                        Text("从头播放", fontSize = 16.sp)
                    }
                } else {
                    TvButton(
                        onClick = { onPlay(resumeEpisode?.index ?: allEpisodes.first().index, 0L) },
                        modifier = Modifier.focusRequester(primaryActionFocus)
                    ) {
                        Text(
                            text = if (resumeEpisode != null) "播放  ${resumeEpisode.name}" else "立即播放",
                            fontSize = 16.sp
                        )
                    }
                }
                Text(
                    text = "共 ${allEpisodes.size} 集",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 线路
            if (groups.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(groups.size) { index ->
                        val group = groups[index]
                        TvChip(
                            onClick = { onSelectGroup(index) },
                            selected = index == selectedGroupIndex
                        ) {
                            Text(
                                text = "${group.name} · ${group.episodes.size}",
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 选集：网格 + 可滚动，几十上百集也能快速定位
            if (currentGroup != null) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusGroup()
                ) {
                    items(currentGroup.episodes, key = { it.index }) { episode ->
                        val isLastWatched = episode.index == resumeEpisode?.index
                        TvOutlinedButton(
                            onClick = {
                                val resumeAt = if (isLastWatched) history?.positionMs ?: 0L else 0L
                                onPlay(episode.index, resumeAt)
                            },
                            selected = isLastWatched,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = episode.name,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun preferredGroupIndex(groups: List<PlayGroup>): Int {
    if (groups.isEmpty()) return 0
    val best = groups.withIndex().maxByOrNull { (_, group) -> group.directPlayableCount }
    return best?.takeIf { it.value.episodes.isNotEmpty() }?.index ?: 0
}
