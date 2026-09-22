package cx.n181.stv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.stvSettingsDataStore by preferencesDataStore(name = "stv_tv_settings")

class SettingsRepository(private val context: Context) {
    private val playerEngineKey = stringPreferencesKey("player_engine")
    private val disabledSourcesKey = stringSetPreferencesKey("disabled_sources")

    suspend fun loadPlayerEngine(): PlayerEngine {
        val raw = context.stvSettingsDataStore.data.first()[playerEngineKey]
        return runCatching {
            PlayerEngine.valueOf(raw ?: PlayerEngine.EXO.name)
        }.getOrDefault(PlayerEngine.EXO)
    }

    suspend fun savePlayerEngine(engine: PlayerEngine) {
        context.stvSettingsDataStore.edit { settings ->
            settings[playerEngineKey] = engine.name
        }
    }

    /** 用户手动停用的源 key 集合（远程配置里 enabled=true 的源也可以被用户本地关掉）。 */
    val disabledSourceKeys: Flow<Set<String>> = context.stvSettingsDataStore.data.map { preferences ->
        preferences[disabledSourcesKey] ?: emptySet()
    }

    suspend fun loadDisabledSourceKeys(): Set<String> = disabledSourceKeys.first()

    suspend fun setSourceEnabled(key: String, enabled: Boolean) {
        context.stvSettingsDataStore.edit { settings ->
            val current = settings[disabledSourcesKey] ?: emptySet()
            settings[disabledSourcesKey] = if (enabled) current - key else current + key
        }
    }

    suspend fun resetDisabledSources() {
        context.stvSettingsDataStore.edit { settings ->
            settings.remove(disabledSourcesKey)
        }
    }
}

/** 配置里启用 且 用户没有手动关闭 的源，按 order 排序。搜索/详情/播放统一用这个。 */
fun AppConfig.activeSources(disabledKeys: Set<String>): List<SourceConfig> =
    sources.filter { it.enabled && it.key !in disabledKeys }.sortedBy { it.order }
