package cx.n181.stv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MediaSearchRepository(
    private val macCmsClient: MacCmsClient
) {
    /**
     * 多源并发搜索。每个源完成时通过 onSourceResult 回调（主线程），
     * UI 可以边搜边显示；返回值是全部源合并后的结果。
     */
    suspend fun search(
        keyword: String,
        sources: List<SourceConfig>,
        maxConcurrent: Int = 6,
        onSourceResult: ((SourceConfig, List<VideoSummary>, String?, Long) -> Unit)? = null
    ): List<VideoSummary> {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty() || sources.isEmpty()) return emptyList()
        val semaphore = Semaphore(maxConcurrent.coerceIn(1, 12))

        return coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val startedAt = System.currentTimeMillis()
                        try {
                            val results = withTimeout(source.timeoutMs.coerceAtLeast(3_000L)) {
                                macCmsClient.search(source, cleanKeyword)
                            }
                            val elapsed = System.currentTimeMillis() - startedAt
                            onSourceResult?.let { callback ->
                                withContext(Dispatchers.Main) { callback(source, results, null, elapsed) }
                            }
                            results
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException &&
                                error !is kotlinx.coroutines.TimeoutCancellationException
                            ) {
                                throw error
                            }
                            val elapsed = System.currentTimeMillis() - startedAt
                            val reason = when (error) {
                                is kotlinx.coroutines.TimeoutCancellationException -> "超时"
                                is java.net.UnknownHostException -> "域名无法解析"
                                is java.net.SocketTimeoutException -> "连接超时"
                                is javax.net.ssl.SSLException -> "证书错误"
                                is kotlinx.serialization.SerializationException -> "返回格式错误"
                                else -> error.message?.take(40) ?: "请求失败"
                            }
                            onSourceResult?.let { callback ->
                                withContext(Dispatchers.Main) { callback(source, emptyList(), reason, elapsed) }
                            }
                            emptyList()
                        }
                    }
                }
            }.awaitAll().flatten()
        }
    }

    companion object {
        /**
         * 把已排好序的结果按归一化片名聚合；组的顺序 = 组内第一条在 ranked 里的位置，
         * 组内按 巡检可播（延迟低优先）> 未巡检 > 海外受限 > 异常，再按源 order。
         */
        fun group(
            ranked: List<VideoSummary>,
            sourceOrder: Map<String, Int>,
            health: SourceHealthRepository?
        ): List<SearchGroup> {
            val grouped = LinkedHashMap<String, MutableList<VideoSummary>>()
            ranked.forEach { item ->
                val key = normalizeTitle(item.title).ifEmpty { item.key }
                grouped.getOrPut(key) { mutableListOf() }.add(item)
            }
            return grouped.map { (key, items) ->
                val sorted = if (health != null) {
                    health.sortByHealth(items, key = { it.sourceKey }, order = { sourceOrder[it.sourceKey] ?: Int.MAX_VALUE })
                } else {
                    items.sortedBy { sourceOrder[it.sourceKey] ?: Int.MAX_VALUE }
                }
                SearchGroup(normalizedTitle = key, items = sorted)
            }
        }

        /**
         * 结果排序：完全匹配 > 前缀匹配 > 包含 > 其它；同级按源的 order 排；并按 源:ID 去重。
         * 原实现按"哪个源先返回"排，每次搜索顺序都不一样，电视上很难找。
         */
        fun rank(keyword: String, results: List<VideoSummary>, sourceOrder: Map<String, Int>): List<VideoSummary> {
            val target = normalizeTitle(keyword)
            return results
                .distinctBy { it.key }
                .sortedWith(
                    compareBy<VideoSummary> { item ->
                        val title = normalizeTitle(item.title)
                        when {
                            target.isEmpty() -> 3
                            title == target -> 0
                            title.startsWith(target) -> 1
                            title.contains(target) -> 2
                            else -> 3
                        }
                    }.thenBy { sourceOrder[it.sourceKey] ?: Int.MAX_VALUE }
                        .thenBy { it.title.length }
                )
        }
    }
}
