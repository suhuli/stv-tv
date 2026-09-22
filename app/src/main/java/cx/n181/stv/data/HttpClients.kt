package cx.n181.stv.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 全局共享的 OkHttpClient。
 *
 * 之前每个 Client 各自 new 一个 OkHttpClient，会产生多套连接池 / 线程池，
 * 在电视盒子这种低内存设备上浪费明显。这里统一成一个，按需 newBuilder() 派生。
 */
object HttpClients {
    const val TV_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 6
            })
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("User-Agent") == null) {
                    chain.proceed(request.newBuilder().header("User-Agent", TV_USER_AGENT).build())
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }
}

/**
 * 把 OkHttp 的异步调用桥接成可取消的挂起函数。
 *
 * 原实现用的是 execute() 阻塞调用，withTimeout 取消协程时底层线程依然被阻塞，
 * 直到 OkHttp 自己超时才释放；这里在协程取消时直接 call.cancel()。
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, _, _ -> response.close() }
        }
    })
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
}
