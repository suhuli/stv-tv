package cx.n181.stv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private val Context.stvHealthDataStore by preferencesDataStore(name = "stv_tv_health")

/** 巡检状态，和网站 js/source-health.js 的语义保持一致。 */
enum class HealthStatus(val label: String, val rank: Int) {
    OK("可播", 0),
    UNKNOWN("未巡检", 1),
    API_ONLY("海外受限", 2),
    DOWN("异常", 3)
}

@Serializable
data class SourceHealthEntry(
    val name: String = "",
    val api_ok: Boolean = false,
    val play_ok: Boolean = false,
    val latency: Long? = null,
    val results: Int = 0,
    val error: String? = null,
    val checked_at: String? = null
) {
    val status: HealthStatus
        get() = when {
            api_ok && play_ok -> HealthStatus.OK
            api_ok -> HealthStatus.API_ONLY
            else -> HealthStatus.DOWN
        }
}

@Serializable
data class SourceHealthReport(
    val generated_at: String? = null,
    val total: Int = 0,
    val healthy: Int = 0,
    val api_only: Int = 0,
    val down: Int = 0,
    val sources: Map<String, SourceHealthEntry> = emptyMap()
)

/**
 * 读取网站每天 03:00 巡检产出的 data/source-health.json。
 *
 * 用途：搜索/换源时把「可播」且延迟低的源排前面，「异常」的源跳过；
 * 巡检节点在海外，「海外受限」只表示巡检机拉不到片，国内电视一般能正常播，所以只降权不跳过。
 */
class SourceHealthRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = HttpClients.shared
) {
    companion object {
        const val REMOTE_URL = "https://tv.181.cx/data/source-health.json"
        private const val TTL_MS = 6L * 60 * 60 * 1000      // 6 小时内不重复拉取（巡检一天一次）
        private const val RETRY_MS = 5L * 60 * 1000         // 拉取失败 5 分钟后再试
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val cachedJsonKey = stringPreferencesKey("health_json")
    private val cachedAtKey = longPreferencesKey("health_at")
    private val mutex = Mutex()

    @Volatile
    private var memory: SourceHealthReport? = null

    @Volatile
    private var loadedAt: Long = 0L

    @Volatile
    private var lastFailureAt: Long = 0L

    private val _state = MutableStateFlow<SourceHealthReport?>(null)
    val state: StateFlow<SourceHealthReport?> = _state

    /** 当前已加载的报告（不触发网络）。 */
    fun current(): SourceHealthReport? = memory

    fun status(sourceKey: String): HealthStatus =
        memory?.sources?.get(sourceKey)?.status ?: HealthStatus.UNKNOWN

    fun latency(sourceKey: String): Long =
        memory?.sources?.get(sourceKey)?.latency?.takeIf { it > 0 } ?: 99_999L

    /** 例如「可播 254ms」「海外受限」「异常」「未巡检」。 */
    fun label(sourceKey: String): String {
        val entry = memory?.sources?.get(sourceKey) ?: return HealthStatus.UNKNOWN.label
        return if (entry.status == HealthStatus.OK && entry.latency != null && entry.latency > 0) {
            "可播 ${entry.latency}ms"
        } else {
            entry.status.label
        }
    }

    /** 可播 → 未巡检 → 海外受限 → 异常；同级按延迟，再按远程配置 order。 */
    fun <T> sortByHealth(items: List<T>, key: (T) -> String, order: (T) -> Int = { 0 }): List<T> =
        items.sortedWith(
            compareBy<T> { status(key(it)).rank }
                .thenBy { latency(key(it)) }
                .thenBy { order(it) }
        )

    /**
     * 搜索时用的源列表：去掉巡检判定为「异常」的源（它们只会拖慢搜索），
     * 但保底：如果去掉后剩余不足 3 个，就不做过滤。
     */
    fun filterForSearch(sources: List<SourceConfig>): List<SourceConfig> {
        if (memory == null) return sources
        val kept = sources.filter { status(it.key) != HealthStatus.DOWN }
        return if (kept.size >= 3) kept else sources
    }

    suspend fun load(forceRefresh: Boolean = false): SourceHealthReport? {
        val now = System.currentTimeMillis()
        memory?.let { if (!forceRefresh && now - loadedAt < TTL_MS) return it }
        return mutex.withLock {
            val again = System.currentTimeMillis()
            memory?.let { if (!forceRefresh && again - loadedAt < TTL_MS) return it }
            if (memory == null) readDisk()?.let { (report, at) ->
                memory = report
                loadedAt = at
                _state.value = report
                if (!forceRefresh && again - at < TTL_MS) return report
            }
            if (!forceRefresh && again - lastFailureAt < RETRY_MS) return memory
            val remote = runCatching { fetchRemote() }.getOrNull()
            if (remote != null) {
                memory = remote.first
                loadedAt = again
                _state.value = remote.first
                persist(remote.second, again)
            } else {
                lastFailureAt = again
            }
            memory
        }
    }

    private suspend fun fetchRemote(): Pair<SourceHealthReport, String>? = withTimeoutOrNull(8_000L) {
        val request = Request.Builder()
            .url(REMOTE_URL)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        val body = httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("巡检数据返回 ${response.code}")
            response.body?.string().orEmpty()
        }
        val report = json.decodeFromString(SourceHealthReport.serializer(), body)
        if (report.sources.isEmpty()) throw IllegalStateException("巡检数据为空")
        report to body
    }

    private suspend fun readDisk(): Pair<SourceHealthReport, Long>? {
        val preferences = runCatching { context.stvHealthDataStore.data.first() }.getOrNull() ?: return null
        val raw = preferences[cachedJsonKey] ?: return null
        val at = preferences[cachedAtKey] ?: 0L
        val report = runCatching { json.decodeFromString(SourceHealthReport.serializer(), raw) }.getOrNull()
            ?: return null
        return report to at
    }

    private suspend fun persist(raw: String, at: Long) {
        runCatching {
            context.stvHealthDataStore.edit { preferences ->
                preferences[cachedJsonKey] = raw
                preferences[cachedAtKey] = at
            }
        }
    }
}
