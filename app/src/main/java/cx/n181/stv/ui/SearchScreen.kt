package cx.n181.stv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cx.n181.stv.data.SourceConfig
import cx.n181.stv.data.VideoSummary
import cx.n181.stv.StvApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FocusColor = Color(0xFF4DE1FF)
private val pinyinLetters = ('A'..'Z').map { it.toString() }

@Composable
fun SearchScreen(
    initialQuery: String = "",
    onOpenDetail: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as StvApp).container }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var letters by remember { mutableStateOf("") }
    var query by remember { mutableStateOf(initialQuery) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var sources by remember { mutableStateOf<List<SourceConfig>>(emptyList()) }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<VideoSummary>() }
    val sourceStatuses = remember { mutableStateMapOf<String, String>() }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var initialized by remember { mutableStateOf(false) }

    fun performSearch(keyword: String) {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty() || isSearching) return

        searchJob?.cancel()
        results.clear()
        sourceStatuses.clear()
        isSearching = true
        query = cleanKeyword

        searchJob = scope.launch {
            val activeSources = sources
            container.historyStore.saveSearch(cleanKeyword)
            searchHistory = container.historyStore.loadSearchHistory()
            container.mediaSearchRepository.search(
                keyword = cleanKeyword,
                sources = activeSources,
                maxConcurrent = 4
            ) { source, sourceResults, error ->
                results.addAll(sourceResults)
                sourceStatuses[source.key] = when {
                    error != null -> "失败"
                    sourceResults.isEmpty() -> "无结果"
                    else -> "完成"
                }
            }
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        val config = container.appConfigRepository.load()
        sources = config.sources.filter { it.enabled }.sortedBy { it.order }
        searchHistory = container.historyStore.loadSearchHistory()
        if (initialQuery.isNotBlank()) {
            performSearch(initialQuery)
        }
        initialized = true
    }

    LaunchedEffect(letters) {
        if (!initialized || letters.length < 1) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(220)
        suggestions = container.suggestClient.suggest(letters)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF111318), Color(0xFF0A0C10))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
                Spacer(Modifier.width(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("输入中文 / 英文，或选下方候选") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )

                Spacer(Modifier.width(12.dp))
                TvButton(onClick = { performSearch(query) }) {
                    Text("搜索")
                }
            }

            Spacer(Modifier.height(12.dp))

            if (searchHistory.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchHistory) { keyword ->
                        HistoryChip(keyword) { performSearch(keyword) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (suggestions.isNotEmpty()) {
                Text(
                    text = "首拼候选",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { suggestion ->
                        HistoryChip(suggestion) { performSearch(suggestion) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "首拼键盘",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pinyinLetters) { letter ->
                            KeyboardKey(
                                label = letter,
                                onClick = {
                                    letters += letter
                                    query = letters
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionKey("删除", Modifier.weight(1f)) {
                            if (letters.isNotEmpty()) letters = letters.dropLast(1)
                            query = letters
                        }
                        ActionKey("清空", Modifier.weight(1f)) {
                            letters = ""
                            suggestions = emptyList()
                            query = ""
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = if (isSearching) {
                            "正在搜索 ${sourceStatuses.values.count { it != "搜索中" }} / ${sourceStatuses.size} 个源"
                        } else {
                            "搜索结果 ${results.size}"
                        },
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    if (results.isEmpty()) {
                        Text(
                            text = if (isSearching) "正在搜索，结果会逐步显示..." else "暂无结果",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 14.sp
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(results) { item ->
                                SearchResultCard(item) {
                                    onOpenDetail(item.sourceKey, item.videoId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.05f else 1f),
        shape = shape,
        color = when {
            focused -> Color.White.copy(alpha = 0.15f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        contentColor = Color.White.copy(alpha = 0.7f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.1f else 1f)
            .clip(shape)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) FocusColor else Color.Transparent,
                shape = shape
            )
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.07f)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.05f else 1f)
            .clip(shape)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) FocusColor else Color.Transparent,
                shape = shape
            )
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.07f)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SearchResultCard(
    item: VideoSummary,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.04f else 1f)
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
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = listOfNotNull(item.year, item.sourceName).joinToString(" · "),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}
