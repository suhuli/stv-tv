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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cx.n181.stv.StvApp
import cx.n181.stv.data.SourceStatus
import cx.n181.stv.data.VideoSummary

private val pinyinLetters = ('A'..'Z').map { it.toString() }
private val digitKeys = ('0'..'9').map { it.toString() }

@Composable
fun SearchScreen(
    initialQuery: String = "",
    onOpenDetail: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as StvApp).container }
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(container))
    val searchButtonFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }

    LaunchedEffect(viewModel.initialized) {
        if (!viewModel.initialized) return@LaunchedEffect
        if (initialQuery.isNotBlank()) {
            viewModel.handleInitialQuery(initialQuery)
        } else if (viewModel.results.isEmpty()) {
            runCatching { firstKeyFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF111318), Color(0xFF0A0C10))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusGroup()
            ) {
                TvIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
                Spacer(Modifier.width(12.dp))

                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.onQueryTyped(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入片名（支持首拼，如 LLDQ）", fontSize = 16.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(viewModel.query) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TvFocusColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = TvFocusColor
                    )
                )

                Spacer(Modifier.width(12.dp))
                TvButton(
                    onClick = { viewModel.search(viewModel.query) },
                    modifier = Modifier.focusRequester(searchButtonFocus)
                ) {
                    Text("搜索", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (viewModel.suggestions.isNotEmpty()) {
                Text(
                    text = "候选片名",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(viewModel.suggestions) { suggestion ->
                        TvChip(onClick = { viewModel.search(suggestion) }) {
                            Text(suggestion, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else if (viewModel.searchHistory.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "最近搜索",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    TvChip(onClick = { viewModel.clearSearchHistory() }) {
                        Text("清空", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(viewModel.searchHistory) { keyword ->
                        TvChip(onClick = { viewModel.search(keyword) }) {
                            Text(keyword, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 左侧：遥控器键盘
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .focusGroup()
                ) {
                    Text(
                        text = "首拼键盘",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(pinyinLetters) { letter ->
                            KeyboardKey(
                                label = letter,
                                modifier = if (letter == "A") Modifier.focusRequester(firstKeyFocus) else Modifier,
                                onClick = { viewModel.appendLetter(letter) }
                            )
                        }
                        items(digitKeys) { digit ->
                            KeyboardKey(label = digit, onClick = { viewModel.appendLetter(digit) })
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionKey("删除", Modifier.weight(1f)) { viewModel.deleteLetter() }
                        ActionKey("清空", Modifier.weight(1f)) { viewModel.clearInput() }
                        ActionKey("搜索", Modifier.weight(1f), highlight = true) {
                            viewModel.search(viewModel.query)
                        }
                    }
                }

                // 右侧：结果
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    SearchStatusBar(viewModel)
                    Spacer(Modifier.height(10.dp))

                    if (viewModel.results.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = when {
                                    viewModel.isSearching -> "正在搜索，结果会逐步显示..."
                                    viewModel.lastKeyword.isNotEmpty() -> "没有找到「${viewModel.lastKeyword}」相关内容\n可以试试换个关键字，或在设置里检查资源源"
                                    else -> "用左侧键盘输入片名首拼，或直接输入片名后搜索"
                                },
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 168.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 24.dp),
                            modifier = Modifier.focusGroup()
                        ) {
                            items(viewModel.results, key = { it.key }) { item ->
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
private fun SearchStatusBar(viewModel: SearchViewModel) {
    val total = viewModel.sourceStatuses.size
    val finished = viewModel.finishedSourceCount
    val failed = viewModel.sourceStatuses.values.count { it is SourceStatus.Failed }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    viewModel.isSearching -> "正在搜索  $finished / $total 个源  ·  已找到 ${viewModel.results.size} 条"
                    total > 0 -> "搜索完成  ${viewModel.results.size} 条结果  ·  $total 个源" +
                        if (failed > 0) "（$failed 个源失败）" else ""
                    else -> "搜索结果"
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 14.sp
            )
        }
        if (viewModel.isSearching && total > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { finished.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = TvFocusColor,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape, scale = 1.1f, borderWidth = 2.dp)
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionKey(
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape, scale = 1.05f, borderWidth = 2.dp)
            .clip(shape)
            .background(
                when {
                    highlight -> Color(0xFF23ADE5).copy(alpha = if (focused) 1f else 0.75f)
                    focused -> Color.White.copy(alpha = 0.18f)
                    else -> Color.White.copy(alpha = 0.07f)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused || highlight) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = 15.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
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
            .aspectRatio(0.68f)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusFrame(focused, shape, scale = 1.05f)
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
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
        )
        if (!item.remarks.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = item.remarks,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(item.year, item.typeName).joinToString(" · "),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1
            )
            Text(
                text = item.sourceName,
                color = TvFocusColor.copy(alpha = 0.9f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}
