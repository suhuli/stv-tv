package cx.n181.stv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SuggestClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun suggest(keyword: String, limit: Int = 12): List<String> = withContext(Dispatchers.IO) {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty()) return@withContext emptyList()

        val url = "https://suggest.video.iqiyi.com/?if=mobile&platform=11&wid=38&key=" +
            URLEncoder.encode(cleanKeyword, "UTF-8") + "&needvip=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV)")
            .header("Referer", "https://www.iqiyi.com/")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SuggestResponse.serializer(), body)
                parsed.data.mapNotNull { item ->
                    item.name?.trim()?.takeUnless { it.isEmpty() || it.equals("null", true) }
                }.distinct().take(limit)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Serializable
private data class SuggestResponse(
    val data: List<SuggestItem> = emptyList()
)

@Serializable
private data class SuggestItem(
    val name: String? = null
)
