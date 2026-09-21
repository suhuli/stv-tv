package cx.n181.stv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cx.n181.stv.data.SourceConfig
import cx.n181.stv.data.VideoDetail
import cx.n181.stv.StvApp
import cx.n181.stv.data.PlayGroup

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

    var sourceConfig by remember { mutableStateOf<SourceConfig?>(null) }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedGroupIndex by remember { mutableStateOf(0) }

    LaunchedEffect(sourceKey, videoId) {
        isLoading = true
        errorMessage = null
        try {
            val config = container.appConfigRepository.load()
            val source = config.sources.firstOrNull { it.key == sourceKey }
                ?: throw IllegalStateException("资源不存在")

            sourceConfig = source
            detail = container.macCmsClient.detail(source, videoId)
            selectedGroupIndex = preferredGroupIndex(detail?.playGroups.orEmpty())
        } catch (error: Exception) {
            errorMessage = error.message ?: "加载失败"
        } finally {
            isLoading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 26.dp)
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
                    text = detail?.summary?.title ?: "加载中",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (isLoading) {
                Text(
                    text = "正在加载详情...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 16.sp
                )
            } else {
                val currentDetail = detail
                val currentSource = sourceConfig
                if (currentDetail == null || currentSource == null) {
                    Text(
                        text = "没有可用内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier.width(280.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 280.dp, height = 380.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = currentDetail.summary.poster,
                                    contentDescription = currentDetail.summary.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(38.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentDetail.summary.title,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = listOfNotNull(
                                    currentDetail.summary.year,
                                    currentDetail.summary.typeName,
                                    currentSource.name
                                ).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )

                            currentDetail.description?.let { description ->
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = description,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    maxLines = 6
                                )
                            }

                            Spacer(modifier = Modifier.height(26.dp))

                            val groups = currentDetail.playGroups
                            val currentGroup = groups.getOrNull(selectedGroupIndex)
                            if (currentGroup == null) {
                                Text(
                                    text = "没有解析到可播放的剧集",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(groups) { group ->
                                        TvOutlinedButton(
                                            onClick = {
                                                selectedGroupIndex = groups.indexOf(group)
                                            }
                                        ) {
                                            Text(group.name)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(currentGroup.episodes) { episode ->
                                        TvButton(
                                            onClick = {
                                                onPlay(episode.index, 0L)
                                            }
                                        ) {
                                            Text(episode.name)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun preferredGroupIndex(groups: List<PlayGroup>): Int {
    val directPlayableScore = groups.withIndex().maxByOrNull { (_, group) ->
        group.episodes.count { episode ->
            episode.url.contains(".m3u8", true) ||
                episode.url.contains(".mp4", true) ||
                episode.url.contains(".mkv", true) ||
                episode.url.contains(".flv", true) ||
                episode.url.contains(".mpd", true)
        }
    }?.index ?: 0

    val bestGroup = groups.getOrNull(directPlayableScore)
    return if (bestGroup != null && bestGroup.episodes.isNotEmpty()) directPlayableScore else 0
}
