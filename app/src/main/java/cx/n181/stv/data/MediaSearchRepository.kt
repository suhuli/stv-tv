package cx.n181.stv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

class MediaSearchRepository(
    private val macCmsClient: MacCmsClient
) {
    suspend fun search(
        keyword: String,
        sources: List<SourceConfig>,
        maxConcurrent: Int = 4,
        onSourceResult: ((SourceConfig, List<VideoSummary>, String?) -> Unit)? = null
    ): List<VideoSummary> {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty() || sources.isEmpty()) return emptyList()
        val semaphore = Semaphore(maxConcurrent.coerceIn(1, 8))

        return coroutineScope {
            sources.map { source ->
                async {
                    semaphore.withPermit {
                        try {
                            val results = withTimeout(source.timeoutMs) {
                                macCmsClient.search(source, cleanKeyword)
                            }
                            onSourceResult?.invoke(source, results, null)
                            results
                        } catch (error: Exception) {
                            onSourceResult?.invoke(
                                source,
                                emptyList(),
                                error.message ?: "请求失败"
                            )
                            emptyList()
                        }
                    }
                }
            }.awaitAll().flatten()
        }
    }
}
