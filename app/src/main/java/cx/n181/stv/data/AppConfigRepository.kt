package cx.n181.stv.data

import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AppConfigRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun load(): AppConfig {
        return try {
            fetchRemote()
        } catch (_: Exception) {
            DefaultConfig.build()
        }
    }

    private suspend fun fetchRemote(): AppConfig = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://tv.181.cx/app-config.json")
            .header("Cache-Control", "no-cache")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("配置接口返回 ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return@withContext json.decodeFromString(AppConfig.serializer(), body)
        }
    }
}
