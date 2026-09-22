package cx.n181.stv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cx.n181.stv.StvApp
import cx.n181.stv.data.DoubanSubject
import cx.n181.stv.data.HistoryItem
import cx.n181.stv.data.formatClock
import kotlinx.coroutines.launch

private val movieTags = listOf(
    "热门", "最新", "经典", "豆瓣高分", "冷门佳片", "华语", "欧美",
    "韩国", "日本", "动作", "喜剧", "爱情", "科幻", "悬疑", "恐怖", "治愈"
)

private val tvTags = listOf(
    "热门", "美剧", "英剧", "韩剧", "日剧", "国产剧", "港剧",
    "日本动画", "综艺", "纪录片"
)

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearchTitle: (String) -> Unit,
    onOpenDetail: (String, String) -> Unit,
    onResume: (HistoryItem) -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as StvApp).container }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val homePillFocus = remember { FocusRequester() }

    // 观看记录用 Flow 监听：从播放器返回时自动刷新，不需要手动重新加载
    val history by container.historyStore.history.collectAsState(initial = emptyList())
    val configState by container.appConfigRepository.state.collectAsState()

    var movieTag by rememberSaveable { mutableStateOf("热门") }
    var movieItems by remember { mutableStateOf<List<DoubanSubject>>(emptyList()) }
    var movieLoading by remember { mutableStateOf(true) }
    var movieError by remember { mutableStateOf<String?>(null) }

    var tvTag by rememberSaveable { mutableStateOf("热门") }
    var tvItems by remember { mutableStateOf<List<DoubanSubject>>(emptyList()) }
    var tvLoading by remember { mutableStateOf(true) }
    var tvError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(configState?.config?.proxyUrl) {
        configState?.config?.proxyUrl?.let { container.doubanClient.proxyBaseUrl = it }
    }
    val hasAnnouncement = !configState?.config?.announcement.isNullOrBlank()
    val sectionOffset = if (hasAnnouncement) 1 else 0

    LaunchedEffect(Unit) {
        runCatching { homePillFocus.requestFocus() }
    }

    LaunchedEffect(movieTag) {
        movieLoading = true
        movieError = null
        try {
            movieItems = container.doubanClient.recommend(type = "movie", tag = movieTag, page = 0, pageSize = 24)
        } catch (error: Exception) {
            movieItems = emptyList()
            movieError = error.message ?: "加载失败"
        } finally {
            movieLoading = false
        }
    }

    LaunchedEffect(tvTag) {
        tvLoading = true
        tvError = null
        try {
            tvItems = container.doubanClient.recommend(type = "tv", tag = tvTag, page = 0, pageSize = 24)
        } catch (error: Exception) {
            tvItems = emptyList()
            tvError = error.message ?: "加载失败"
        } finally {
            tvLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF111318), Color(0xFF0A0C10))))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = configState?.config?.appName ?: "私人TV",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "PRIVATE VIEWING SYSTEM",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                            letterSpacing = 4.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavPill("首页", active = true, modifier = Modifier.focusRequester(homePillFocus)) {
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                        NavPill("电影", active = false) {
                            scope.launch { listState.animateScrollToItem(3 + sectionOffset) }
                        }
                        NavPill("剧集", active = false) {
                            scope.launch { listState.animateScrollToItem(6 + sectionOffset) }
                        }
                        NavPill("搜索", active = false) { onOpenSearch() }
                        NavPill("设置", active = false) { onOpenSettings() }
                    }
                }
            }

            configState?.config?.announcement?.takeIf { it.isNotBlank() }?.let { announcement ->
                item(key = "announcement") {
                    Surface(
                        color = Color(0xFF23ADE5).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = announcement,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item(key = "history-header") { SectionHeader("继续观看", if (history.isEmpty()) "" else "共 ${history.size} 条") }

            item(key = "history-row") {
                if (history.isEmpty()) {
                    EmptyHint("暂无观看记录，去搜索一部片子开始吧")
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(start = 6.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        modifier = Modifier.focusGroup()
                    ) {
                        items(history, key = { "${it.sourceKey}:${it.videoId}" }) { item ->
                            HistoryCard(item) { onResume(item) }
                        }
                    }
                }
            }

            item(key = "movie-header") { SectionHeader("电影", "豆瓣 · $movieTag") }

            item(key = "movie-tags") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(movieTags) { tag ->
                        TagChip(tag, movieTag == tag) { movieTag = tag }
                    }
                }
            }

            item(key = "movie-row") {
                RecommendRow(
                    loading = movieLoading,
                    error = movieError,
                    items = movieItems,
                    imageUrl = { container.doubanClient.proxyImageUrl(it.cover) },
                    onClick = { onSearchTitle(it.title) }
                )
            }

            item(key = "tv-header") { SectionHeader("剧集", "豆瓣 · $tvTag") }

            item(key = "tv-tags") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(tvTags) { tag ->
                        TagChip(tag, tvTag == tag) { tvTag = tag }
                    }
                }
            }

            item(key = "tv-row") {
                RecommendRow(
                    loading = tvLoading,
                    error = tvError,
                    items = tvItems,
                    imageUrl = { container.doubanClient.proxyImageUrl(it.cover) },
                    onClick = { onSearchTitle(it.title) }
                )
            }

            item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RecommendRow(
    loading: Boolean,
    error: String?,
    items: List<DoubanSubject>,
    imageUrl: (DoubanSubject) -> String,
    onClick: (DoubanSubject) -> Unit
) {
    when {
        loading -> EmptyHint("正在加载推荐...")
        error != null -> EmptyHint("推荐加载失败：$error")
        items.isEmpty() -> EmptyHint("暂无推荐")
        else -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            modifier = Modifier.focusGroup()
        ) {
            items(items, key = { it.id.ifBlank { it.title } }) { item ->
                PosterCard(item, imageUrl(item)) { onClick(item) }
            }
        }
    }
}

@Composable
private fun NavPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)

    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape, scale = 1.06f, borderWidth = 2.dp),
        shape = shape,
        color = when {
            active -> Color.White
            focused -> Color.White.copy(alpha = 0.2f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        contentColor = when {
            active -> Color(0xFF111318)
            focused -> Color.White
            else -> Color.White.copy(alpha = 0.75f)
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            fontSize = 16.sp,
            fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = hint,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape, scale = 1.05f, borderWidth = 2.dp),
        shape = shape,
        color = when {
            selected -> Color.White
            focused -> Color.White.copy(alpha = 0.18f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        contentColor = when {
            selected -> Color(0xFF111318)
            focused -> Color.White
            else -> Color.White.copy(alpha = 0.65f)
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.35f),
        fontSize = 15.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .width(360.dp)
            .height(202.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape, scale = 1.04f)
            .clip(shape)
            .background(Color(0xFF1A1D24))
            .clickable(onClick = onClick)
    ) {
        if (item.poster != null) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(item.episodeName.ifBlank { "第 ${item.episodeIndex + 1} 集" })
                    if (item.positionMs > 0) {
                        append(" · 看到 ")
                        append(formatClock(item.positionMs))
                    }
                    append(" · ")
                    append(item.sourceName)
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = TvFocusColor,
                trackColor = Color.White.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun PosterCard(
    subject: DoubanSubject,
    imageUrl: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .width(172.dp)
            .height(258.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape, scale = 1.06f)
            .clip(shape)
            .background(Color(0xFF1A1D24))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = subject.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
        )
        if (subject.rate.isNotBlank()) {
            Surface(
                color = Color(0xFFFFB400).copy(alpha = 0.92f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = subject.rate,
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = subject.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subject.isNewFlag) {
                Text(
                    text = "新上线",
                    color = TvFocusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
