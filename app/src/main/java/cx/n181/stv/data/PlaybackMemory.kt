package cx.n181.stv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.stvMemoryDataStore by preferencesDataStore(name = "stv_tv_memory")

@Serializable
data class LastGoodEntry(
    val sourceKey: String,
    val videoId: String = "",
    val at: Long = System.currentTimeMillis()
)

/**
 * 播放记忆（和网站 localStorage 里的 lastGoodSource / watchedEpisodes 对应）：
 *
 * - 上次可播：某部片上一次真正播出画面的源。聚合搜索选源、自动换源都优先用它。
 * - 已看集数：按片名记录看过的集，详情页 / 播放器选集里打勾。
 *
 * 两者都按归一化片名（normalizeTitle）为 key，换源后依然有效。
 */
class PlaybackMemory(private val context: Context) {
    companion object {
        private const val MAX_TITLES = 300
        private const val MAX_WATCHED_TITLES = 300
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val lastGoodKey = stringPreferencesKey("last_good_source")
    private val watchedKey = stringPreferencesKey("watched_episodes")
    private val lastGoodSerializer = MapSerializer(String.serializer(), LastGoodEntry.serializer())
    private val watchedSerializer = MapSerializer(String.serializer(), SetSerializer(Int.serializer()))

    // 内存镜像：UI 侧同步读取（排序、打标）用，启动时 warmUp() 预热
    @Volatile
    private var lastGoodCache: Map<String, LastGoodEntry> = emptyMap()

    @Volatile
    private var watchedCache: Map<String, Set<Int>> = emptyMap()

    suspend fun warmUp() {
        val preferences = runCatching { context.stvMemoryDataStore.data.first() }.getOrNull() ?: return
        lastGoodCache = decodeLastGood(preferences[lastGoodKey])
        watchedCache = decodeWatched(preferences[watchedKey])
    }

    // ---------------- 上次可播 ----------------

    fun lastGoodSource(title: String?): LastGoodEntry? {
        val key = normalizeTitle(title)
        if (key.isEmpty()) return null
        return lastGoodCache[key]
    }

    suspend fun rememberGoodSource(title: String?, sourceKey: String, videoId: String) {
        val key = normalizeTitle(title)
        if (key.isEmpty() || sourceKey.isBlank()) return
        val entry = LastGoodEntry(sourceKey = sourceKey, videoId = videoId)
        lastGoodCache = lastGoodCache + (key to entry)
        runCatching {
            context.stvMemoryDataStore.edit { preferences ->
                val current = decodeLastGood(preferences[lastGoodKey])
                val merged = current + (key to entry)
                val limited = if (merged.size > MAX_TITLES) {
                    merged.entries.sortedByDescending { it.value.at }.take(MAX_TITLES).associate { it.key to it.value }
                } else merged
                preferences[lastGoodKey] = json.encodeToString(lastGoodSerializer, limited)
            }
        }
    }

    // ---------------- 已看集数 ----------------

    fun watchedEpisodes(title: String?): Set<Int> {
        val key = normalizeTitle(title)
        if (key.isEmpty()) return emptySet()
        return watchedCache[key].orEmpty()
    }

    fun isWatched(title: String?, episodeIndex: Int): Boolean = episodeIndex in watchedEpisodes(title)

    suspend fun markWatched(title: String?, episodeIndex: Int) {
        val key = normalizeTitle(title)
        if (key.isEmpty()) return
        val currentSet = watchedCache[key].orEmpty()
        if (episodeIndex in currentSet) return
        watchedCache = watchedCache + (key to (currentSet + episodeIndex))
        runCatching {
            context.stvMemoryDataStore.edit { preferences ->
                val current = decodeWatched(preferences[watchedKey])
                val merged = current + (key to (current[key].orEmpty() + episodeIndex))
                val limited = if (merged.size > MAX_WATCHED_TITLES) {
                    // 没有时间戳，简单丢掉最早写入的
                    merged.entries.drop(merged.size - MAX_WATCHED_TITLES).associate { it.key to it.value }
                } else merged
                preferences[watchedKey] = json.encodeToString(watchedSerializer, limited)
            }
        }
    }

    suspend fun clearAll() {
        lastGoodCache = emptyMap()
        watchedCache = emptyMap()
        runCatching {
            context.stvMemoryDataStore.edit { preferences ->
                preferences.remove(lastGoodKey)
                preferences.remove(watchedKey)
            }
        }
    }

    private fun decodeLastGood(raw: String?): Map<String, LastGoodEntry> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(lastGoodSerializer, raw) }.getOrDefault(emptyMap())
    }

    private fun decodeWatched(raw: String?): Map<String, Set<Int>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(watchedSerializer, raw) }.getOrDefault(emptyMap())
    }
}

/**
 * 同一部片在多个源的搜索结果聚合成一组（网站首页的「同名聚合」）。
 * 搜索页产出，详情页 / 播放器通过 SearchGroupCache 按片名取回，用来展示「其它源」和自动换源。
 */
data class SearchGroup(
    val normalizedTitle: String,
    val items: List<VideoSummary>,
    val recommended: VideoSummary,
    val recommendReason: RecommendReason
) {
    val title: String get() = recommended.title
    val sourceCount: Int get() = items.map { it.sourceKey }.distinct().size
}

enum class RecommendReason(val label: String) {
    LAST_GOOD("上次可播"),
    HEALTHY("推荐"),
    NONE("")
}

/** 进程内缓存最近一次搜索的聚合结果（按归一化片名），不落盘。 */
class SearchGroupCache {
    @Volatile
    private var groups: Map<String, SearchGroup> = emptyMap()

    fun put(all: List<SearchGroup>) {
        groups = all.associateBy { it.normalizedTitle }
    }

    fun get(title: String?): SearchGroup? {
        val key = normalizeTitle(title)
        if (key.isEmpty()) return null
        return groups[key]
    }

    /** 同一部片在其它源的候选（排除指定源），按传入顺序。 */
    fun alternatives(title: String?, excludeSourceKey: String?): List<VideoSummary> =
        get(title)?.items?.filter { it.sourceKey != excludeSourceKey }.orEmpty()

    /** 不知道片名（详情都拉不下来）时，按 源:ID 反查所属组，再给出其它源。 */
    fun alternativesBySource(sourceKey: String, videoId: String): List<VideoSummary> {
        val group = groups.values.firstOrNull { g -> g.items.any { it.sourceKey == sourceKey && it.videoId == videoId } }
            ?: return emptyList()
        return group.items.filter { it.sourceKey != sourceKey }
    }
}

/**
 * 把一批搜索结果按片名聚合，并为每组挑出推荐源：
 * 上次可播 > 巡检可播（延迟低优先）> 远程配置 order。
 */
fun groupSearchResults(
    keyword: String,
    results: List<VideoSummary>,
    sourceOrder: Map<String, Int>,
    health: SourceHealthRepository,
    memory: PlaybackMemory
): List<SearchGroup> {
    if (results.isEmpty()) return emptyList()
    val ranked = MediaSearchRepository.rank(keyword, results, sourceOrder)
    val grouped = LinkedHashMap<String, MutableList<VideoSummary>>()
    ranked.forEach { item ->
        val key = normalizeTitle(item.title).ifEmpty { item.key }
        grouped.getOrPut(key) { mutableListOf() }.add(item)
    }
    return grouped.map { (key, items) ->
        val lastGood = memory.lastGoodSource(items.first().title)
        val byHealth = health.sortByHealth(items, key = { it.sourceKey }, order = { sourceOrder[it.sourceKey] ?: Int.MAX_VALUE })
        val lastGoodItem = lastGood?.let { entry ->
            byHealth.firstOrNull { it.sourceKey == entry.sourceKey && it.videoId == entry.videoId }
                ?: byHealth.firstOrNull { it.sourceKey == entry.sourceKey }
        }
        val ordered = if (lastGoodItem != null) listOf(lastGoodItem) + byHealth.filter { it !== lastGoodItem } else byHealth
        val recommended = ordered.first()
        val reason = when {
            lastGoodItem != null -> RecommendReason.LAST_GOOD
            health.status(recommended.sourceKey) == HealthStatus.OK -> RecommendReason.HEALTHY
            else -> RecommendReason.NONE
        }
        SearchGroup(normalizedTitle = key, items = ordered, recommended = recommended, recommendReason = reason)
    }
}
