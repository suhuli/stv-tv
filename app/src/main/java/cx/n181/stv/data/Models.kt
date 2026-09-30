package cx.n181.stv.data

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val schemaVersion: Int = 1,
    val appName: String = "私人TV",
    val announcement: String? = null,
    val proxyUrl: String? = null,
    val search: SearchConfig = SearchConfig(),
    val player: PlayerConfig = PlayerConfig(),
    val sources: List<SourceConfig> = emptyList()
)

@Serializable
data class SearchConfig(
    val concurrency: Int = 6,
    val timeoutMs: Long = 10_000L,
    val defaultMaxPages: Int = 1
)

@Serializable
data class PlayerConfig(
    val minBufferSeconds: Int = 30,
    val maxBufferSeconds: Int = 90,
    val playbackStartBufferSeconds: Int = 2,
    val rebufferSeconds: Int = 5,
    val autoNext: Boolean = true,
    val seekStepSeconds: Int = 10
)

@Serializable
data class SourceConfig(
    val key: String,
    val name: String,
    val api: String,
    val enabled: Boolean = true,
    val order: Int = 0,
    val maxPages: Int = 1,
    val timeoutMs: Long = 10_000L,
    val headers: Map<String, String> = emptyMap()
)

data class VideoSummary(
    val sourceKey: String,
    val sourceName: String,
    val videoId: String,
    val title: String,
    val poster: String? = null,
    val year: String? = null,
    val typeName: String? = null,
    val remarks: String? = null
) {
    val key: String get() = "$sourceKey:$videoId"
}

data class VideoDetail(
    val summary: VideoSummary,
    val description: String? = null,
    val playGroups: List<PlayGroup> = emptyList()
) {
    val allEpisodes: List<Episode> get() = playGroups.flatMap { it.episodes }
    fun episodeAt(index: Int): Episode? = allEpisodes.firstOrNull { it.index == index }
    fun groupOf(episodeIndex: Int): Int =
        playGroups.indexOfFirst { group -> group.episodes.any { it.index == episodeIndex } }.coerceAtLeast(0)
}

data class PlayGroup(
    val name: String,
    val episodes: List<Episode>
) {
    val directPlayableCount: Int get() = episodes.count { looksDirectlyPlayable(it.url) }
}

data class Episode(
    val index: Int,
    val name: String,
    val url: String
)

@Serializable
data class HistoryItem(
    val sourceKey: String,
    val sourceName: String,
    val videoId: String,
    val title: String,
    val poster: String? = null,
    val episodeIndex: Int,
    val episodeName: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

enum class PlayerEngine(val label: String) {
    EXO("ExoPlayer"),
    IJK("IJKPlayer"),
    SYSTEM("系统播放器")
}

/** 搜索时每个源的状态，用于进度展示。 */
sealed class SourceStatus {
    data object Pending : SourceStatus()
    data class Done(val count: Int, val elapsedMs: Long) : SourceStatus()
    data class Failed(val reason: String, val elapsedMs: Long) : SourceStatus()
}

/** 搜索结果按片名聚合：同一部片在多个源的收录合成一组，一张卡。 */
data class SearchGroup(
    val normalizedTitle: String,
    val items: List<VideoSummary>
) {
    val primary: VideoSummary get() = items.first()
    val sourceCount: Int get() = items.map { it.sourceKey }.distinct().size
    /** 海报优先取有图的那条 */
    val poster: String? get() = items.firstOrNull { !it.poster.isNullOrBlank() }?.poster
    /** 备注（更新至 X 集 / 全 N 集）取第一条非空的 */
    val remarks: String? get() = items.firstOrNull { !it.remarks.isNullOrBlank() }?.remarks
}
