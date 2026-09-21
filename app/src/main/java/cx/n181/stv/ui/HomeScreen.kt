package cx.n181.stv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cx.n181.stv.data.DoubanSubject
import cx.n181.stv.data.HistoryItem
import cx.n181.stv.StvApp
import kotlinx.coroutines.launch

private val FocusColor = Color(0xFF4DE1FF)

private val movieTags = listOf(
    "热门", "最新", "经典", "豆瓣高分", "冷门佳片", "华语", "欧美",
    "韩国", "日本", "动作", "喜剧", "日综", "爱情", "科幻", "悬疑", "恐怖", "治愈"
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
    onOpenDetail: (String, String) -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as StvApp).container }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var history by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }

    var movieTag by remember { mutableStateOf("热门") }
    var movieItems by remember { mutableStateOf<List<DoubanSubject>>(emptyList()) }
    var movieLoading by remember { mutableStateOf(true) }
    var movieError by remember { mutableStateOf<String?>(null) }

    var tvTag by remember { mutableStateOf("热门") }
    var tvItems by remember { mutableStateOf<List<DoubanSubject>>(emptyList()) }
    var tvLoading by remember { mutableStateOf(true) }
    var tvError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        history = container.historyStore.loadHistory()
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "私人TV",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "PRIVATE VIEWING SYSTEM",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 10.sp,
                            letterSpacing = 4.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavPill("首页", active = true) {
                            scope.launch { listState.scrollToItem(0) }
                        }
                        NavPill("电影", active = false) {
                            scope.launch { listState.scrollToItem(3) }
                        }
                        NavPill("剧集", active = false) {
                            scope.launch { listState.scrollToItem(6) }
                        }
                        NavPill("搜索", active = false) { onOpenSearch() }
                        NavPill("设置", active = false) { onOpenSettings() }
                    }
                }
            }

            item { SectionHeader("继续观看", "最近播放") }

            item {
                if (history.isEmpty()) {
                    EmptyHint("暂无观看记录")
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(end = 20.dp)
                    ) {
                        items(history) { item ->
                            HistoryCard(item) { onOpenDetail(item.sourceKey, item.videoId) }
                        }
                    }
                }
            }

            item { SectionHeader("电影", "标签：$movieTag") }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(movieTags) { tag ->
                        TagChip(tag, movieTag == tag) { movieTag = tag }
                    }
                }
            }

            item {
                if (movieLoading) {
                    EmptyHint("正在加载电影推荐...")
                } else if (movieError != null) {
                    EmptyHint("加载失败：$movieError")
                } else if (movieItems.isEmpty()) {
                    EmptyHint("暂无推荐")
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(end = 20.dp)
                    ) {
                        items(movieItems) { item ->
                            PosterCard(item, container.doubanClient.proxyImageUrl(item.cover)) {
                                onSearchTitle(item.title)
                            }
                        }
                    }
                }
            }

            item { SectionHeader("剧集", "标签：$tvTag") }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tvTags) { tag ->
                        TagChip(tag, tvTag == tag) { tvTag = tag }
                    }
                }
            }

            item {
                if (tvLoading) {
                    EmptyHint("正在加载剧集推荐...")
                } else if (tvError != null) {
                    EmptyHint("加载失败：$tvError")
                } else if (tvItems.isEmpty()) {
                    EmptyHint("暂无推荐")
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(end = 20.dp)
                    ) {
                        items(tvItems) { item ->
                            PosterCard(item, container.doubanClient.proxyImageUrl(item.cover)) {
                                onSearchTitle(item.title)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.06f else 1f),
        shape = shape,
        color = when {
            active -> Color.White
            focused -> Color.White.copy(alpha = 0.18f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        contentColor = when {
            active -> Color(0xFF111318)
            else -> Color.White.copy(alpha = 0.75f)
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
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
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = hint,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp
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
            .scale(if (focused) 1.05f else 1f),
        shape = shape,
        color = when {
            selected -> Color.White
            focused -> Color.White.copy(alpha = 0.15f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        contentColor = when {
            selected -> Color(0xFF111318)
            else -> Color.White.copy(alpha = 0.65f)
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.35f),
        fontSize = 14.sp
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
            .width(400.dp)
            .height(225.dp)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.03f else 1f)
            .clip(shape)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) FocusColor else Color.Transparent,
                shape = shape
            )
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
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "第 ${item.episodeIndex + 1} 集 · ${item.sourceName}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
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
            .width(180.dp)
            .height(270.dp)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.05f else 1f)
            .clip(shape)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) FocusColor else Color.Transparent,
                shape = shape
            )
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
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = subject.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subject.rate.ifBlank { "--" },
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
                if (subject.isNew) {
                    Text(
                        text = "新",
                        color = Color(0xFF4DE1FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
