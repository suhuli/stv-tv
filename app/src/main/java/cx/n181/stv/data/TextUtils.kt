package cx.n181.stv.data

import java.util.Locale

private val titleNoiseRegex = Regex("[\\s\\p{Punct}　·•:：（）()【】\\[\\]《》〈〉<>「」『』—\\-_~～!！?？,，.。、/\\\\|'\"“”‘’*]+")
private val htmlTagRegex = Regex("<[^>]+>")
private val htmlBreakRegex = Regex("(?i)<\\s*(br|/p|/div|/li)\\s*/?>")
private val multiSpaceRegex = Regex("[ \\t\\x0B\\f\\r]+")
private val multiNewlineRegex = Regex("\\n{3,}")

/** 片名归一化：去空白 / 标点 / 大小写差异，用来跨源比对同一部片。 */
fun normalizeTitle(title: String?): String {
    if (title.isNullOrBlank()) return ""
    return title
        .replace(titleNoiseRegex, "")
        .lowercase(Locale.ROOT)
        .trim()
}

/** MacCMS 的 vod_content 经常带 HTML 标签和实体，电视上直接显示会很难看。 */
fun stripHtml(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val text = raw
        .replace(htmlBreakRegex, "\n")
        .replace(htmlTagRegex, "")
        .replace("&nbsp;", " ")
        .replace("&ensp;", " ")
        .replace("&emsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&hellip;", "…")
        .replace(multiSpaceRegex, " ")
        .replace(multiNewlineRegex, "\n\n")
        .trim()
    return text.ifBlank { null }
}

/** 统一资源 URL：补协议、去空白。 */
fun normalizeUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("rtmp://", ignoreCase = true) || value.startsWith("rtsp://", ignoreCase = true) -> value
        else -> value
    }
}

/** 毫秒 → 00:00 / 0:00:00 */
fun formatClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** 判断是否像可以直接播放的地址（而不是需要网页解析的跳转页）。 */
private val DIRECT_MEDIA_EXTENSION = Regex("""\.(m3u8|mp4|mkv|flv|mpd|ts|mov|webm|avi|m4v|m3u)(\?|#|/|$)""")

/**
 * 判断一个地址是否是播放器能直接播放的媒体直链（而不是需要浏览器解析的网页）。
 * 只看路径部分，避免域名里恰好包含 ".ts" 之类字样造成误判。
 */
fun looksDirectlyPlayable(url: String): Boolean {
    val lower = url.trim().lowercase(Locale.ROOT)
    if (lower.startsWith("rtmp://") || lower.startsWith("rtsp://")) return true
    val schemeEnd = lower.indexOf("://")
    val pathStart = if (schemeEnd >= 0) lower.indexOf('/', schemeEnd + 3) else 0
    val path = if (pathStart >= 0) lower.substring(pathStart) else ""
    return DIRECT_MEDIA_EXTENSION.containsMatchIn(path) || path.contains(".m3u8")
}
