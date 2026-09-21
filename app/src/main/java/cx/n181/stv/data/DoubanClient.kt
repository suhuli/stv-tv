package cx.n181.stv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class DoubanSubject(
    val id: String = "",
    val title: String = "",
    val rate: String = "",
    val cover: String = "",
    val url: String = "",
    val isNew: Boolean = false
)

class DoubanClient(
    private val proxyBaseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
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
    private val cacheTtlMs = 10L * 60 * 1000

    suspend fun recommend(
        type: String,
        tag: String,
        page: Int = 0,
        pageSize: Int = 24
    ): List<DoubanSubject> = withContext(Dispatchers.IO) {
        val cacheKey = "$type:$tag:$page:$pageSize"
        val cached = synchronized(cache) { cache[cacheKey] }
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
            return@withContext cached.items
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
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV)")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("豆瓣接口返回 ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val result = json.decodeFromString(DoubanResponse.serializer(), body)
            synchronized(cache) {
                cache[cacheKey] = CacheEntry(result.subjects, System.currentTimeMillis())
            }
            result.subjects
        }
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
