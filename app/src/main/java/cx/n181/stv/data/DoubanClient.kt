package cx.n181.stv.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

@Serializable
data class DoubanSubject(
    val id: String = "",
    val title: String = "",
    val rate: String = "",
    val cover: String = "",
    val url: String = "",
    @Serializable(with = LenientStringSerializer::class) val isNew: String = "false"
) {
    val isNewFlag: Boolean get() = isNew.equals("true", ignoreCase = true)
}

class DoubanClient(
    private val defaultProxyBaseUrl: String,
    private val httpClient: OkHttpClient = HttpClients.shared
) {
    private data class CacheEntry(
        val items: List<DoubanSubject>,
        val timestamp: Long
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val cache = mutableMapOf<String, CacheEntry>()
    private val cacheTtlMs = 30L * 60 * 1000

    /** 远程配置可以通过 proxyUrl 覆盖代理地址；为空时用内置地址。 */
    @Volatile
    var proxyBaseUrl: String = defaultProxyBaseUrl
        set(value) {
            field = value.ifBlank { defaultProxyBaseUrl }
        }

    suspend fun recommend(
        type: String,
        tag: String,
        page: Int = 0,
        pageSize: Int = 24
    ): List<DoubanSubject> {
        val cacheKey = "$type:$tag:$page:$pageSize"
        val cached = synchronized(cache) { cache[cacheKey] }
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
            return cached.items
        }

        val doubanUrl = buildString {
            append("https://movie.douban.com/j/search_subjects?type=")
            append(type)
            append("&tag=")
            append(URLEncoder.encode(tag, "UTF-8"))
            append("&sort=recommend&page_limit=")
            append(pageSize)
            append("&page_start=")
            append(page * pageSize)
        }

        val request = Request.Builder()
            .url(proxyUrl(doubanUrl))
            .header("User-Agent", HttpClients.TV_USER_AGENT)
            .get()
            .build()

        val body = httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("豆瓣接口返回 ${response.code}")
            }
            response.body?.string().orEmpty()
        }
        val result = json.decodeFromString(DoubanResponse.serializer(), body)
        synchronized(cache) {
            cache[cacheKey] = CacheEntry(result.subjects, System.currentTimeMillis())
        }
        return result.subjects
    }

    fun proxyImageUrl(url: String): String {
        if (url.isBlank()) return url
        return proxyUrl(url)
    }

    private fun proxyUrl(targetUrl: String): String {
        return proxyBaseUrl.trimEnd('/') + "/" + URLEncoder.encode(targetUrl, "UTF-8")
    }
}

@Serializable
private data class DoubanResponse(
    val subjects: List<DoubanSubject> = emptyList()
)
