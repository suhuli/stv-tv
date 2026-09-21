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
    val concurrency: Int = 4,
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
)

data class VideoDetail(
    val summary: VideoSummary,
    val description: String? = null,
    val playGroups: List<PlayGroup> = emptyList()
)

data class PlayGroup(
    val name: String,
    val episodes: List<Episode>
)

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
)

enum class PlayerEngine {
    EXO,
    IJK,
    SYSTEM
}
