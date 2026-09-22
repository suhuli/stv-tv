package cx.n181.stv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cx.n181.stv.AppContainer
import cx.n181.stv.data.MediaSearchRepository
import cx.n181.stv.data.SourceConfig
import cx.n181.stv.data.SourceStatus
import cx.n181.stv.data.VideoSummary
import cx.n181.stv.data.activeSources
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 搜索页状态放进 ViewModel（作用域绑定到导航栈条目）：
 * 搜索 → 进详情 → 返回，结果、进度、输入都还在，不用重新搜。
 * 旧实现全部是 remember{}，离开页面就全丢了。
 */
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    var query by mutableStateOf("")
    var letters by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var sources by mutableStateOf<List<SourceConfig>>(emptyList())
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var isSearching by mutableStateOf(false)
    var lastKeyword by mutableStateOf("")
    var initialized by mutableStateOf(false)

    val results = mutableStateListOf<VideoSummary>()
    val sourceStatuses = mutableStateMapOf<String, SourceStatus>()

    private val rawResults = mutableListOf<VideoSummary>()
    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var handledInitialQuery = false

    val finishedSourceCount: Int
        get() = sourceStatuses.values.count { it !is SourceStatus.Pending }

    init {
        viewModelScope.launch {
            refreshSources()
            searchHistory = container.historyStore.loadSearchHistory()
            initialized = true
        }
    }

    suspend fun refreshSources() {
        val config = container.appConfigRepository.load()
        val disabled = container.settingsRepository.disabledSourceKeys.first()
        sources = config.activeSources(disabled)
    }

    /** 从首页带关键字进来时只自动搜一次，返回再进来不重复搜。 */
    fun handleInitialQuery(initialQuery: String) {
        if (handledInitialQuery || initialQuery.isBlank()) return
        handledInitialQuery = true
        query = initialQuery
        search(initialQuery)
    }

    fun search(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return

        // 允许在搜索进行中直接发起新搜索：取消旧任务（旧实现直接 return，历史词点了没反应）
        searchJob?.cancel()
        rawResults.clear()
        results.clear()
        sourceStatuses.clear()
        query = clean
        lastKeyword = clean
        isSearching = true

        searchJob = viewModelScope.launch {
            try {
                if (sources.isEmpty()) refreshSources()
                val activeSources = sources
                activeSources.forEach { sourceStatuses[it.key] = SourceStatus.Pending }
                val order = activeSources.withIndex().associate { it.value.key to it.index }

                container.historyStore.saveSearch(clean)
                searchHistory = container.historyStore.loadSearchHistory()

                val config = container.appConfigRepository.current()
                container.mediaSearchRepository.search(
                    keyword = clean,
                    sources = activeSources,
                    maxConcurrent = config.search.concurrency
                ) { source, sourceResults, error, elapsedMs ->
                    if (lastKeyword != clean) return@search
                    sourceStatuses[source.key] = when {
                        error != null -> SourceStatus.Failed(error, elapsedMs)
                        else -> SourceStatus.Done(sourceResults.size, elapsedMs)
                    }
                    if (sourceResults.isNotEmpty()) {
                        rawResults.addAll(sourceResults)
                        val ranked = MediaSearchRepository.rank(clean, rawResults, order)
                        results.clear()
                        results.addAll(ranked)
                    }
                }
            } finally {
                if (lastKeyword == clean) isSearching = false
            }
        }
    }

    fun appendLetter(letter: String) {
        letters += letter
        query = letters
        scheduleSuggest()
    }

    fun deleteLetter() {
        if (letters.isEmpty()) {
            if (query.isNotEmpty()) query = query.dropLast(1)
            return
        }
        letters = letters.dropLast(1)
        query = letters
        scheduleSuggest()
    }

    fun clearInput() {
        letters = ""
        query = ""
        suggestions = emptyList()
        suggestJob?.cancel()
    }

    fun onQueryTyped(value: String) {
        query = value
        // 用户在输入框手动输入时，首拼缓冲区同步成当前内容，避免"删除"键把已输入的中文清掉
        letters = value.filter { it.isLetter() && it.code < 128 }.uppercase()
        scheduleSuggest()
    }

    private fun scheduleSuggest() {
        suggestJob?.cancel()
        val key = query.trim()
        if (key.isEmpty()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(220)
            suggestions = container.suggestClient.suggest(key)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            container.historyStore.clearSearchHistory()
            searchHistory = emptyList()
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        suggestJob?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(container) as T
        }
    }
}
