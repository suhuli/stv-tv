package cx.n181.stv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MacCmsClient(
    private val httpClient: OkHttpClient = HttpClients.shared
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
        return response.list
            .filter { it.vod_name?.isNotBlank() == true && it.vod_id.isNotBlank() }
            .map { it.toSummary(source) }
    }

    /**
     * 详情优先走标准的 ac=detail；部分站点 videolist 不返回 vod_play_url，
     * 也有少数站点只支持 videolist，所以两者互为兜底。
     */
    suspend fun detail(
        source: SourceConfig,
        videoId: String
    ): VideoDetail {
        val base = source.api.toHttpUrl()
        val detailUrl = base.newBuilder()
            .addQueryParameter("ac", "detail")
            .addQueryParameter("ids", videoId)
            .build()
            .toString()
        val listUrl = base.newBuilder()
            .addQueryParameter("ac", "videolist")
            .addQueryParameter("ids", videoId)
            .build()
            .toString()

        var lastError: Exception? = null
        var item: MacCmsVideoItem? = null
        for (url in listOf(detailUrl, listUrl)) {
            try {
                val list = request(url, source).list
                val candidate = list.firstOrNull { it.vod_id == videoId } ?: list.firstOrNull()
                if (candidate != null) {
                    item = candidate
                    if (!candidate.vod_play_url.isNullOrBlank()) break
                }
            } catch (error: Exception) {
                lastError = error
            }
        }
        val found = item ?: throw (lastError ?: IllegalStateException("没有找到影片详情"))

        return VideoDetail(
            summary = found.toSummary(source),
            description = stripHtml(found.vod_content),
            playGroups = parsePlayGroups(
                playFrom = found.vod_play_from.orEmpty(),
                playUrl = found.vod_play_url.orEmpty()
            )
        )
    }

    private suspend fun request(url: String, source: SourceConfig): MacCmsResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClients.TV_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .get()

        source.headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val call = httpClient.newCall(requestBuilder.build())
        if (source.timeoutMs > 0) {
            call.timeout().timeout(source.timeoutMs + 2_000L, TimeUnit.MILLISECONDS)
        }
        val body = call.await().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("接口返回 ${response.code}")
            }
            response.body?.string().orEmpty()
        }
        if (body.isBlank()) {
            throw IllegalStateException("接口返回空内容")
        }
        return withContext(Dispatchers.Default) {
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                throw IllegalStateException("接口返回的不是 JSON")
            }
            json.decodeFromString(MacCmsResponse.serializer(), trimmed)
        }
    }

    private fun MacCmsVideoItem.toSummary(source: SourceConfig) = VideoSummary(
        sourceKey = source.key,
        sourceName = source.name,
        videoId = vod_id,
        title = vod_name.orEmpty().trim(),
        poster = normalizeUrl(vod_pic),
        year = vod_year?.trim()?.takeIf { it.isNotEmpty() && it != "0" },
        typeName = type_name?.trim()?.takeIf { it.isNotEmpty() },
        remarks = vod_remarks?.trim()?.takeIf { it.isNotEmpty() }
    )

    /**
     * MacCMS 播放地址格式：
     *   vod_play_from = "线路A$$$线路B"
     *   vod_play_url  = "第1集$http://a/1.m3u8#第2集$http://a/2.m3u8$$$第1集$http://b/1.m3u8"
     * 少数站点条目里没有 "$"，只有 URL；此时用序号当集名，而不是整条丢掉。
     */
    private fun parsePlayGroups(playFrom: String, playUrl: String): List<PlayGroup> {
        if (playUrl.isBlank()) return emptyList()
        val names = playFrom.split("$$$")
        val urlGroups = playUrl.split("$$$")
        val groups = mutableListOf<PlayGroup>()
        var globalEpisodeIndex = 0

        urlGroups.forEachIndexed { groupIndex, rawGroup ->
            val groupName = names.getOrNull(groupIndex)?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "线路${groupIndex + 1}"
            val episodes = rawGroup.split("#")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexedNotNull { episodePosition, raw ->
                    val parts = raw.split("$", limit = 2)
                    val name: String
                    val url: String?
                    if (parts.size == 2) {
                        name = parts[0].trim().ifEmpty { "第${episodePosition + 1}集" }
                        url = normalizeUrl(parts[1])
                    } else {
                        name = "第${episodePosition + 1}集"
                        url = normalizeUrl(parts[0])
                    }
                    if (url.isNullOrBlank() || !url.contains("://") && !url.startsWith("/")) {
                        null
                    } else {
                        Episode(index = 0, name = name, url = url)
                    }
                }
                .map { episode -> episode.copy(index = globalEpisodeIndex++) }

            if (episodes.isNotEmpty()) {
                groups.add(PlayGroup(name = groupName, episodes = episodes))
            }
        }

        // 能直接播放（m3u8/mp4）的线路排前面，需要网页解析的排后面
        return groups.sortedByDescending { it.directPlayableCount }
            .let { sorted ->
                // 重新编号，保证 index 在排序后依然连续且和 UI 一致
                var index = 0
                sorted.map { group ->
                    group.copy(episodes = group.episodes.map { it.copy(index = index++) })
                }
            }
    }
}

/** 站点返回的 vod_id / vod_year 有时是数字有时是字符串，这里一律按字符串读。 */
internal object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> ""
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@Serializable
internal data class MacCmsResponse(
    @Serializable(with = LenientStringSerializer::class) val page: String = "1",
    @Serializable(with = LenientStringSerializer::class) val pagecount: String = "1",
    @Serializable(with = LenientStringSerializer::class) val limit: String = "",
    @Serializable(with = LenientStringSerializer::class) val total: String = "0",
    val list: List<MacCmsVideoItem> = emptyList()
)

@Serializable
internal data class MacCmsVideoItem(
    @Serializable(with = LenientStringSerializer::class) val vod_id: String = "",
    @Serializable(with = LenientStringSerializer::class) val vod_name: String? = null,
    @Serializable(with = LenientStringSerializer::class) val vod_pic: String? = null,
    @Serializable(with = LenientStringSerializer::class) val vod_year: String? = null,
    @Serializable(with = LenientStringSerializer::class) val type_name: String? = null,
    @Serializable(with = LenientStringSerializer::class) val vod_remarks: String? = null,
    @Serializable(with = LenientStringSerializer::class) val vod_content: String? = null,
    @Serializable(with = LenientStringSerializer::class) val vod_play_from: String? = null,
    @Serializable(with = LenientStringSerializer::class) val vod_play_url: String? = null
)
