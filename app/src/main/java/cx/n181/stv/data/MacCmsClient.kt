package cx.n181.stv.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MacCmsClient(
    private val baseHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun search(
        source: SourceConfig,
        keyword: String,
        page: Int = 1
    ): List<VideoSummary> {
        val url = source.api.toHttpUrl().newBuilder()
            .addQueryParameter("ac", "videolist")
            .addQueryParameter("wd", keyword)
            .addQueryParameter("pg", page.toString())
            .build()

        val response = request(url.toString(), source)
        return response.list.map { item ->
            VideoSummary(
                sourceKey = source.key,
                sourceName = source.name,
                videoId = item.vod_id.toString(),
                title = item.vod_name.orEmpty(),
                poster = item.vod_pic,
                year = item.vod_year,
                typeName = item.type_name,
                remarks = item.vod_remarks
            )
        }
    }

    suspend fun detail(
        source: SourceConfig,
        videoId: String
    ): VideoDetail {
        val url = source.api.toHttpUrl().newBuilder()
            .addQueryParameter("ac", "videolist")
            .addQueryParameter("ids", videoId)
            .build()

        val response = request(url.toString(), source)
        val item = response.list.firstOrNull()
            ?: throw IllegalStateException("没有找到影片详情")

        val summary = VideoSummary(
            sourceKey = source.key,
            sourceName = source.name,
            videoId = item.vod_id.toString(),
            title = item.vod_name.orEmpty(),
            poster = item.vod_pic,
            year = item.vod_year,
            typeName = item.type_name,
            remarks = item.vod_remarks
        )

        return VideoDetail(
            summary = summary,
            description = item.vod_content,
            playGroups = parsePlayGroups(
                playFrom = item.vod_play_from.orEmpty(),
                playUrl = item.vod_play_url.orEmpty()
            )
        )
    }

    private suspend fun request(url: String, source: SourceConfig): MacCmsResponse = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
            )
            .header("Accept", "application/json")
            .get()

        source.headers.forEach { (key, value) ->
            if (key.equals("User-Agent", ignoreCase = true)) {
                requestBuilder.header("User-Agent", value)
            } else if (key.equals("Accept", ignoreCase = true)) {
                requestBuilder.header("Accept", value)
            } else {
                requestBuilder.addHeader(key, value)
            }
        }

        baseHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("接口返回 ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                throw IllegalStateException("接口返回空内容")
            }
            return@withContext json.decodeFromString(MacCmsResponse.serializer(), body)
        }
    }

    private fun parsePlayGroups(playFrom: String, playUrl: String): List<PlayGroup> {
        val names = playFrom.split("$$$")
        val urls = playUrl.split("$$$")
        val groups = mutableListOf<PlayGroup>()
        var globalEpisodeIndex = 0

        for (index in names.indices) {
            val name = names.getOrNull(index)?.trim().takeUnless { it.isNullOrEmpty() } ?: "默认"
            val rawEpisodes = urls.getOrNull(index).orEmpty()
            val parsedEpisodes = rawEpisodes.split("#")
                .mapNotNull { raw ->
                    val parts = raw.split("$", limit = 2)
                    val episodeName = parts.getOrNull(0)?.trim().orEmpty()
                    val episodeUrl = parts.getOrNull(1)?.trim().orEmpty()
                        .let { if (it.startsWith("//")) "https:$it" else it }
                    if (episodeName.isBlank() || episodeUrl.isBlank()) {
                        null
                    } else {
                        Episode(
                            index = 0,
                            name = episodeName,
                            url = episodeUrl
                        )
                    }
                }
            val episodes = parsedEpisodes.mapIndexed { episodeIndex, episode ->
                episode.copy(index = globalEpisodeIndex++)
            }

            if (episodes.isNotEmpty()) {
                groups.add(PlayGroup(name = name, episodes = episodes))
            }
        }

        return groups
    }
}

@Serializable
internal data class MacCmsResponse(
    val page: Int = 1,
    val pagecount: Int = 1,
    val limit: String? = null,
    val total: Int = 0,
    val list: List<MacCmsVideoItem> = emptyList()
)

@Serializable
internal data class MacCmsVideoItem(
    val vod_id: Long = 0L,
    val vod_name: String? = null,
    val vod_pic: String? = null,
    val vod_year: String? = null,
    val type_name: String? = null,
    val vod_remarks: String? = null,
    val vod_content: String? = null,
    val vod_play_from: String? = null,
    val vod_play_url: String? = null
)
