package cx.n181.stv

import android.app.Application
import cx.n181.stv.data.AppConfigRepository
import cx.n181.stv.data.DoubanClient
import cx.n181.stv.data.HistoryStore
import cx.n181.stv.data.MacCmsClient
import cx.n181.stv.data.MediaSearchRepository
import cx.n181.stv.data.SettingsRepository
import cx.n181.stv.data.SuggestClient
import okhttp3.OkHttpClient

class StvApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val okHttpClient = OkHttpClient()
    val macCmsClient = MacCmsClient()
    val appConfigRepository = AppConfigRepository()
    val historyStore = HistoryStore(application)
    val settingsRepository = SettingsRepository(application)
    val doubanClient = DoubanClient("https://tv.181.cx/proxy/")
    val suggestClient = SuggestClient()
    val mediaSearchRepository = MediaSearchRepository(macCmsClient)
}
