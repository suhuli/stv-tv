package cx.n181.stv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.stvSettingsDataStore by preferencesDataStore(name = "stv_tv_settings")

class SettingsRepository(private val context: Context) {
    private val playerEngineKey = stringPreferencesKey("player_engine")

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
}
