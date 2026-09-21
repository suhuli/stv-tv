package cx.n181.stv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.stvDataStore by preferencesDataStore(name = "stv_tv_data")

class HistoryStore(private val context: Context) {
    private val historyKey = stringPreferencesKey("watch_history")
    private val searchHistoryKey = stringPreferencesKey("search_history")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadHistory(): List<HistoryItem> {
        val raw = context.stvDataStore.data.first()[historyKey] ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(HistoryItem.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun saveHistory(item: HistoryItem) {
        val current = loadHistory()
        val merged = listOf(item) + current.filterNot {
            it.sourceKey == item.sourceKey && it.videoId == item.videoId
        }
        val limited = merged.sortedByDescending { it.updatedAt }.take(60)

        context.stvDataStore.edit { preferences ->
            preferences[historyKey] = json.encodeToString(
                ListSerializer(HistoryItem.serializer()),
                limited
            )
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
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun saveSearch(keyword: String) {
        if (keyword.isBlank()) return
        val current = loadSearchHistory().filterNot { it == keyword }
        val merged = (listOf(keyword) + current).take(8)

        context.stvDataStore.edit { preferences ->
            preferences[searchHistoryKey] = json.encodeToString(
                ListSerializer(String.serializer()),
                merged
            )
        }
    }
}
