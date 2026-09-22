package cx.n181.stv.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SuggestClient(
    private val httpClient: OkHttpClient = HttpClients.shared.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun suggest(keyword: String, limit: Int = 12): List<String> {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty()) return emptyList()

        val url = "https://suggest.video.iqiyi.com/?if=mobile&platform=11&wid=38&key=" +
            URLEncoder.encode(cleanKeyword, "UTF-8") + "&needvip=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClients.TV_USER_AGENT)
            .header("Referer", "https://www.iqiyi.com/")
            .get()
            .build()

        return try {
            val body = httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string().orEmpty()
            }
            val parsed = json.decodeFromString(SuggestResponse.serializer(), body)
            parsed.data.mapNotNull { item ->
                item.name?.trim()?.takeUnless { it.isEmpty() || it.equals("null", true) }
            }.distinct().take(limit)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
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
