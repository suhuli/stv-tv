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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val Context.stvConfigDataStore by preferencesDataStore(name = "stv_tv_config")

/**
 * 远程配置仓库。
 *
 * 旧实现每次 load() 都会发起一次网络请求（搜索页、详情页、播放页每次进入都请求，
 * 播放页甚至一次播放请求两次）。服务器不可用时每次都要等到超时才回退，
 * 导致每个页面都"卡一下"。
 *
 * 现在：内存缓存 + 磁盘缓存（上次成功的远程配置）+ 内置兜底，
 * 并用 Mutex 做单飞（并发调用只发一次请求）。
 */
class AppConfigRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = HttpClients.shared.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    enum class Origin(val label: String) {
        REMOTE("远程配置"),
        CACHED("本地缓存"),
        BUILTIN("内置配置")
    }

    data class Loaded(
        val config: AppConfig,
        val origin: Origin,
        val loadedAt: Long
    )

    companion object {
        const val REMOTE_CONFIG_URL = "https://tv.181.cx/app-config.json"
        private const val REMOTE_TTL_MS = 30L * 60 * 1000      // 远程配置 30 分钟内不重复拉取
        private const val FALLBACK_RETRY_MS = 2L * 60 * 1000   // 拉取失败后 2 分钟再试
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val cachedJsonKey = stringPreferencesKey("remote_config_json")
    private val cachedAtKey = longPreferencesKey("remote_config_at")
    private val mutex = Mutex()

    @Volatile
    private var memory: Loaded? = null

    private val _state = MutableStateFlow<Loaded?>(null)
    val state: StateFlow<Loaded?> = _state

    /** 当前已加载的配置（不触发网络），没有则返回内置配置。 */
    fun current(): AppConfig = memory?.config ?: DefaultConfig.build()

    suspend fun load(forceRefresh: Boolean = false): AppConfig = loaded(forceRefresh).config

    suspend fun loaded(forceRefresh: Boolean = false): Loaded {
        memory?.let { if (!forceRefresh && isFresh(it)) return it }
        return mutex.withLock {
            memory?.let { if (!forceRefresh && isFresh(it)) return it }
            val now = System.currentTimeMillis()
            val remote = runCatching { fetchRemote() }.getOrNull()
            val result = when {
                remote != null -> {
                    persistToDisk(remote.second, now)
                    Loaded(normalize(remote.first), Origin.REMOTE, now)
                }
                else -> {
                    val disk = readDisk()
                    if (disk != null) Loaded(normalize(disk), Origin.CACHED, now)
                    else Loaded(DefaultConfig.build(), Origin.BUILTIN, now)
                }
            }
            memory = result
            _state.value = result
            result
        }
    }

    private fun isFresh(loaded: Loaded): Boolean {
        val age = System.currentTimeMillis() - loaded.loadedAt
        return when (loaded.origin) {
            Origin.REMOTE -> age < REMOTE_TTL_MS
            else -> age < FALLBACK_RETRY_MS
        }
    }

    /** 远程配置里如果没有源列表，就补上内置源，避免出现"一个源都没有"的空壳。 */
    private fun normalize(config: AppConfig): AppConfig {
        val sources = if (config.sources.isEmpty()) DefaultConfig.build().sources else config.sources
        return config.copy(
            sources = sources
                .filter { it.api.isNotBlank() && it.key.isNotBlank() }
                .distinctBy { it.key }
                .sortedBy { it.order }
        )
    }

    private suspend fun fetchRemote(): Pair<AppConfig, String>? = withTimeoutOrNull(9_000L) {
        val request = Request.Builder()
            .url(REMOTE_CONFIG_URL)
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("配置接口返回 ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val parsed = json.decodeFromString(AppConfig.serializer(), body)
            parsed to body
        }
    }

    private suspend fun readDisk(): AppConfig? {
        val raw = runCatching { context.stvConfigDataStore.data.first()[cachedJsonKey] }.getOrNull()
            ?: return null
        return runCatching { json.decodeFromString(AppConfig.serializer(), raw) }.getOrNull()
    }

    private suspend fun persistToDisk(raw: String, at: Long) {
        runCatching {
            context.stvConfigDataStore.edit { preferences ->
                preferences[cachedJsonKey] = raw
                preferences[cachedAtKey] = at
            }
        }
    }
}
