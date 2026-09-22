package cx.n181.stv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.stvDataStore by preferencesDataStore(name = "stv_tv_data")

class HistoryStore(private val context: Context) {
    private val historyKey = stringPreferencesKey("watch_history")
    private val searchHistoryKey = stringPreferencesKey("search_history")
    private val json = Json { ignoreUnknownKeys = true }
    private val historySerializer = ListSerializer(HistoryItem.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())

    companion object {
        private const val MAX_HISTORY = 60
        private const val MAX_SEARCH_HISTORY = 10
    }

    /** 观看记录流：首页监听它，播放完回到首页会自动刷新。 */
    val history: Flow<List<HistoryItem>> = context.stvDataStore.data.map { preferences ->
        decodeHistory(preferences[historyKey])
    }

    suspend fun loadHistory(): List<HistoryItem> = history.first()

    suspend fun find(sourceKey: String, videoId: String): HistoryItem? =
        loadHistory().firstOrNull { it.sourceKey == sourceKey && it.videoId == videoId }

    /** 按片名找最近一条记录（换源后依然能提示"上次看到第几集"）。 */
    suspend fun findByTitle(title: String): HistoryItem? {
        val target = normalizeTitle(title)
        if (target.isEmpty()) return null
        return loadHistory().firstOrNull { normalizeTitle(it.title) == target }
    }

    /**
     * 读-改-写放在同一个 edit 事务里，避免并发保存时互相覆盖
     * （旧实现是先 loadHistory 再 edit，两次保存交错会丢数据）。
     */
    suspend fun saveHistory(item: HistoryItem) {
        context.stvDataStore.edit { preferences ->
            val current = decodeHistory(preferences[historyKey])
            val merged = listOf(item) + current.filterNot {
                it.sourceKey == item.sourceKey && it.videoId == item.videoId
            }
            val limited = merged.sortedByDescending { it.updatedAt }.take(MAX_HISTORY)
            preferences[historyKey] = json.encodeToString(historySerializer, limited)
        }
    }

    suspend fun removeHistory(sourceKey: String, videoId: String) {
        context.stvDataStore.edit { preferences ->
            val current = decodeHistory(preferences[historyKey])
            val remaining = current.filterNot { it.sourceKey == sourceKey && it.videoId == videoId }
            preferences[historyKey] = json.encodeToString(historySerializer, remaining)
        }
    }

    suspend fun clearHistory() {
        context.stvDataStore.edit { preferences ->
            preferences.remove(historyKey)
        }
    }

    suspend fun loadSearchHistory(): List<String> {
        val raw = context.stvDataStore.data.first()[searchHistoryKey] ?: return emptyList()
        return runCatching {
            json.decodeFromString(stringListSerializer, raw)
        }.getOrDefault(emptyList())
    }

    suspend fun saveSearch(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        context.stvDataStore.edit { preferences ->
            val current = preferences[searchHistoryKey]?.let { raw ->
                runCatching { json.decodeFromString(stringListSerializer, raw) }.getOrDefault(emptyList())
            }.orEmpty()
            val merged = (listOf(clean) + current.filterNot { it == clean }).take(MAX_SEARCH_HISTORY)
            preferences[searchHistoryKey] = json.encodeToString(stringListSerializer, merged)
        }
    }

    suspend fun clearSearchHistory() {
        context.stvDataStore.edit { preferences ->
            preferences.remove(searchHistoryKey)
        }
    }

    private fun decodeHistory(raw: String?): List<HistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(historySerializer, raw)
        }.getOrDefault(emptyList())
    }
}
