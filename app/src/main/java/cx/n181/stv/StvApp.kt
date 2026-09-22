package cx.n181.stv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import cx.n181.stv.data.AppConfigRepository
import cx.n181.stv.data.DoubanClient
import cx.n181.stv.data.HistoryStore
import cx.n181.stv.data.HttpClients
import cx.n181.stv.data.MacCmsClient
import cx.n181.stv.data.MediaSearchRepository
import cx.n181.stv.data.SettingsRepository
import cx.n181.stv.data.SuggestClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StvApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 启动时预热配置：后面进搜索/详情/播放页直接命中缓存，不再各自等网络
        container.appScope.launch {
            runCatching { container.appConfigRepository.load() }
        }
    }

    /**
     * Coil 复用全局 OkHttpClient；海报站点常常校验 UA，共享客户端里已经带了 TV UA。
     * 同时限制内存缓存比例，避免低内存盒子被海报图挤出 OOM。
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { HttpClients.shared }
            .crossfade(true)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}

class AppContainer(application: Application) {
    /** 应用级协程作用域：用于页面销毁后仍需完成的操作（例如退出播放时保存进度）。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val macCmsClient = MacCmsClient()
    val appConfigRepository = AppConfigRepository(application)
    val historyStore = HistoryStore(application)
    val settingsRepository = SettingsRepository(application)
    val doubanClient = DoubanClient("https://tv.181.cx/proxy/")
    val suggestClient = SuggestClient()
    val mediaSearchRepository = MediaSearchRepository(macCmsClient)
}
