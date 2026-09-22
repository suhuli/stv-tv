package cx.n181.stv.ui

import android.content.Context
import android.content.ContextWrapper
import android.media.MediaPlayer
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import cx.n181.stv.StvApp
import cx.n181.stv.data.Episode
import cx.n181.stv.data.HistoryItem
import cx.n181.stv.data.HttpClients
import cx.n181.stv.data.PlayerConfig
import cx.n181.stv.data.PlayerEngine
import cx.n181.stv.data.SourceConfig
import cx.n181.stv.data.VideoDetail
import cx.n181.stv.data.VideoSummary
import cx.n181.stv.data.activeSources
import cx.n181.stv.data.formatClock
import cx.n181.stv.data.normalizeTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

private const val CONTROLS_HIDE_DELAY_MS = 5_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
private const val SPEED_OPTIONS_LABEL = "倍速"
private val speedOptions = listOf(1.0f, 1.25f, 1.5f, 2.0f)

@Composable
@OptIn(UnstableApi::class)
fun PlayerScreen(
    sourceKey: String,
    videoId: String,
    episodeIndex: Int,
    startPositionMs: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as StvApp).container }
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val altFocus = remember { FocusRequester() }

    // ---- 播放目标 ----
    var activeSourceKey by remember { mutableStateOf(sourceKey) }
    var activeVideoId by remember { mutableStateOf(videoId) }
    var desiredEpisodeIndex by remember { mutableStateOf(episodeIndex) }
    var currentEpisodeIndex by remember { mutableStateOf(episodeIndex) }
    var pendingStartPosition by remember { mutableStateOf(startPositionMs) }

    // ---- 数据 ----
    var sourceConfig by remember { mutableStateOf<SourceConfig?>(null) }
    var allSources by remember { mutableStateOf<List<SourceConfig>>(emptyList()) }
    var playerConfig by remember { mutableStateOf(PlayerConfig()) }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var detailLoading by remember { mutableStateOf(true) }

    // ---- 引擎 ----
    var preferredEngine by remember { mutableStateOf<PlayerEngine?>(null) }
    var sessionEngine by remember { mutableStateOf<PlayerEngine?>(null) }
    var attemptedEngines by remember { mutableStateOf<Set<PlayerEngine>>(emptySet()) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var videoEngine by remember { mutableStateOf<VideoEngine?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    var detailReloadTick by remember { mutableStateOf(0) }

    // ---- 播放状态 ----
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var wasPlayingBeforeStop by remember { mutableStateOf(false) }

    // ---- UI 状态 ----
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var altResults by remember { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var altLoading by remember { mutableStateOf(false) }
    var altMessage by remember { mutableStateOf<String?>(null) }

    val episodes = detail?.allEpisodes.orEmpty()
    val currentEpisode = detail?.episodeAt(currentEpisodeIndex)
    val hasPrevious = episodes.any { it.index == currentEpisodeIndex - 1 }
    val hasNext = episodes.any { it.index == currentEpisodeIndex + 1 }
    val seekStepMs = playerConfig.seekStepSeconds.coerceAtLeast(5) * 1_000L

    // ------------------------------------------------------------------
    // 工具函数
    // ------------------------------------------------------------------
    fun releaseCurrentPlayer() {
        exoPlayer?.release()
        exoPlayer = null
        videoEngine?.release()
        videoEngine = null
    }

    fun wakeControls() {
        controlsVisible = true
        interactionTick += 1
    }

    fun showInfo(message: String) {
        infoMessage = message
    }

    /** 立即把当前进度写入观看记录（切集 / 退出 / 切后台 / 定时 都会调用）。 */
    fun persistProgress(finishedEpisodeIndexOverride: Int? = null, forcePosition: Long? = null) {
        val currentDetail = detail ?: return
        val currentSource = sourceConfig ?: return
        val targetIndex = finishedEpisodeIndexOverride ?: currentEpisodeIndex
        val episode = currentDetail.episodeAt(targetIndex) ?: return
        val engine = videoEngine
        val position = forcePosition ?: run {
            if (engine == null || !engine.isReady) return
            engine.currentPosition
        }
        val duration = forcePosition?.let { durationMs } ?: (engine?.duration ?: durationMs)
        if (forcePosition == null && position < 1_000L) return

        val item = HistoryItem(
            sourceKey = currentDetail.summary.sourceKey,
            sourceName = currentSource.name,
            videoId = currentDetail.summary.videoId,
            title = currentDetail.summary.title,
            poster = currentDetail.summary.poster,
            episodeIndex = targetIndex,
            episodeName = episode.name,
            positionMs = position.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
            updatedAt = System.currentTimeMillis()
        )
        container.appScope.launch { container.historyStore.saveHistory(item) }
    }

    fun playEpisode(index: Int, position: Long = 0L) {
        if (detail?.episodeAt(index) == null) return
        if (index != currentEpisodeIndex) persistProgress()
        errorMessage = null
        altResults = emptyList()
        altMessage = null
        attemptedEngines = emptySet()
        pendingStartPosition = position
        positionMs = position
        durationMs = 0L
        isBuffering = true
        if (index == currentEpisodeIndex) reloadTick += 1 else currentEpisodeIndex = index
        wakeControls()
    }

    fun onEpisodeEnded() {
        val next = episodes.firstOrNull { it.index == currentEpisodeIndex + 1 }
        if (playerConfig.autoNext && next != null) {
            // 当前集记为已看完（进度 0，指向下一集）
            persistProgress(finishedEpisodeIndexOverride = next.index, forcePosition = 0L)
            playEpisode(next.index, 0L)
            showInfo("自动播放：${next.name}")
        } else {
            persistProgress(forcePosition = 0L)
            isPlaying = false
            wakeControls()
            showInfo("已全部播放完毕")
        }
    }

    fun onEngineFailed(engine: PlayerEngine, reason: String) {
        val tried = attemptedEngines + engine
        attemptedEngines = tried
        val next = PlayerEngine.entries.firstOrNull { it !in tried }
        val resumeAt = videoEngine?.takeIf { it.isReady }?.currentPosition ?: positionMs
        if (next != null) {
            showInfo("${engine.label} 播放失败，自动切换到 ${next.label}")
            pendingStartPosition = resumeAt
            isBuffering = true
            sessionEngine = next
        } else {
            errorMessage = "$reason\n已依次尝试 ExoPlayer / IJKPlayer / 系统播放器，建议换源"
            isBuffering = false
            wakeControls()
        }
    }

    fun switchEngineManually(engine: PlayerEngine) {
        if (engine == sessionEngine) return
        val resumeAt = videoEngine?.takeIf { it.isReady }?.currentPosition ?: positionMs
        scope.launch { container.settingsRepository.savePlayerEngine(engine) }
        preferredEngine = engine
        attemptedEngines = emptySet()
        errorMessage = null
        pendingStartPosition = resumeAt
        isBuffering = true
        sessionEngine = engine
        showInfo("已切换到 ${engine.label}")
    }

    fun togglePlay() {
        val engine = videoEngine ?: return
        if (engine.isPlaying) engine.pause() else engine.play()
        isPlaying = engine.isPlaying
        wakeControls()
    }

    fun seekBy(deltaMs: Long) {
        val engine = videoEngine ?: return
        if (!engine.isReady) return
        val duration = engine.duration
        val target = (engine.currentPosition + deltaMs).coerceAtLeast(0L)
        val clamped = if (duration > 0) target.coerceAtMost((duration - 1_000L).coerceAtLeast(0L)) else target
        engine.seekTo(clamped)
        positionMs = clamped
        wakeControls()
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        videoEngine?.setSpeed(speed)
        wakeControls()
    }

    fun retry() {
        errorMessage = null
        attemptedEngines = emptySet()
        pendingStartPosition = positionMs
        isBuffering = true
        if (detail == null) detailReloadTick += 1 else reloadTick += 1
    }

    fun findAlternatives() {
        val title = detail?.summary?.title ?: return
        if (altLoading) return
        scope.launch {
            altLoading = true
            altMessage = null
            altResults = emptyList()
            wakeControls()
            val target = normalizeTitle(title)
            val found = runCatching {
                container.mediaSearchRepository.search(
                    keyword = title,
                    sources = allSources.filter { it.key != activeSourceKey },
                    maxConcurrent = 8
                )
            }.getOrDefault(emptyList())
            val exact = found.filter { normalizeTitle(it.title) == target }
            val loose = found.filter { normalizeTitle(it.title).contains(target) && normalizeTitle(it.title) != target }
            val merged = (exact + loose).distinctBy { it.key }.take(12)
            altResults = merged
            altMessage = if (merged.isEmpty()) "其它源里没有找到《$title》" else null
            altLoading = false
        }
    }

    fun switchToAlternative(item: VideoSummary) {
        persistProgress()
        val resumeAt = videoEngine?.takeIf { it.isReady }?.currentPosition ?: positionMs
        altResults = emptyList()
        altMessage = null
        errorMessage = null
        attemptedEngines = emptySet()
        desiredEpisodeIndex = currentEpisodeIndex
        pendingStartPosition = resumeAt
        isBuffering = true
        detail = null
        activeSourceKey = item.sourceKey
        activeVideoId = item.videoId
        showInfo("正在切换到 ${item.sourceName}")
    }

    // ------------------------------------------------------------------
    // 数据加载
    // ------------------------------------------------------------------
    LaunchedEffect(Unit) {
        val engine = container.settingsRepository.loadPlayerEngine()
        preferredEngine = engine
        sessionEngine = engine
    }

    LaunchedEffect(activeSourceKey, activeVideoId, detailReloadTick) {
        errorMessage = null
        detailLoading = true
        releaseCurrentPlayer()
        try {
            val config = container.appConfigRepository.load()
            val disabled = container.settingsRepository.disabledSourceKeys.first()
            playerConfig = config.player
            allSources = config.activeSources(disabled)
            val source = allSources.firstOrNull { it.key == activeSourceKey }
                ?: config.sources.firstOrNull { it.key == activeSourceKey }
                ?: throw IllegalStateException("资源源「$activeSourceKey」不存在")
            sourceConfig = source
            val loaded = container.macCmsClient.detail(source, activeVideoId)
            if (loaded.allEpisodes.isEmpty()) {
                throw IllegalStateException("这个源没有可播放的剧集")
            }
            val target = loaded.episodeAt(desiredEpisodeIndex)?.index ?: loaded.allEpisodes.first().index
            if (target != desiredEpisodeIndex) pendingStartPosition = 0L
            currentEpisodeIndex = target
            attemptedEngines = emptySet()
            detail = loaded
        } catch (error: Exception) {
            errorMessage = error.message ?: "加载失败"
            isBuffering = false
        } finally {
            detailLoading = false
        }
    }

    // ------------------------------------------------------------------
    // 创建播放器
    // ------------------------------------------------------------------
    val surfaceKey = if (sessionEngine == PlayerEngine.EXO) null else surfaceHolder
    LaunchedEffect(detail, currentEpisodeIndex, sessionEngine, surfaceKey, reloadTick) {
        val currentDetail = detail ?: return@LaunchedEffect
        val engine = sessionEngine ?: return@LaunchedEffect
        val episode = currentDetail.episodeAt(currentEpisodeIndex)
        if (episode == null) {
            errorMessage = "没有找到可播放的剧集"
            return@LaunchedEffect
        }
        val holder = surfaceHolder
        if (engine != PlayerEngine.EXO && holder == null) {
            // SurfaceView 还没就绪，等 surfaceCreated 触发下一次
            return@LaunchedEffect
        }

        releaseCurrentPlayer()
        // 关键：先把续播位置取出来。旧实现在 prepareAsync 之后立刻把 startPosition 清零，
        // IJK / 系统播放器的 onPrepared 回调是异步的，读到的永远是 0，导致续播失效。
        val resumeAt = pendingStartPosition.coerceAtLeast(0L)
        pendingStartPosition = 0L
        isBuffering = true

        val source = allSources.firstOrNull { it.key == currentDetail.summary.sourceKey } ?: sourceConfig
        val requestHeaders = buildMap {
            put("Accept", "*/*")
            put("User-Agent", HttpClients.TV_USER_AGENT)
            if (source != null) {
                runCatching {
                    val host = source.api.toHttpUrl()
                    put("Referer", "${host.scheme}://${host.host}/")
                }
                putAll(source.headers)
            }
        }
        val userAgent = requestHeaders["User-Agent"] ?: HttpClients.TV_USER_AGENT

        try {
            when (engine) {
                PlayerEngine.EXO -> {
                    val httpFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent(userAgent)
                        .setDefaultRequestProperties(requestHeaders)
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(10_000)
                        .setReadTimeoutMs(25_000)

                    val loadControl = DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            playerConfig.minBufferSeconds * 1_000,
                            playerConfig.maxBufferSeconds * 1_000,
                            playerConfig.playbackStartBufferSeconds * 1_000,
                            playerConfig.rebufferSeconds * 1_000
                        )
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build()

                    val renderersFactory = DefaultRenderersFactory(context)
                        .setEnableDecoderFallback(true)

                    val newPlayer = ExoPlayer.Builder(context, renderersFactory)
                        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                        .setLoadControl(loadControl)
                        .setSeekBackIncrementMs(seekStepMs)
                        .setSeekForwardIncrementMs(seekStepMs)
                        .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
                        .build()

                    val engineWrapper = ExoVideoEngine(newPlayer)
                    newPlayer.setMediaItem(
                        MediaItem.Builder()
                            .setUri(episode.url)
                            .setMimeType(guessMimeType(episode.url))
                            .setMediaMetadata(
                                MediaMetadata.Builder().setTitle(currentDetail.summary.title).build()
                            )
                            .build()
                    )
                    newPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            isBuffering = playbackState == Player.STATE_BUFFERING
                            if (playbackState == Player.STATE_READY) engineWrapper.markReady()
                            if (playbackState == Player.STATE_ENDED) onEpisodeEnded()
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            onEngineFailed(PlayerEngine.EXO, "ExoPlayer 无法播放（${error.errorCodeName}）")
                        }
                    })

                    newPlayer.setPlaybackSpeed(playbackSpeed)
                    if (resumeAt > 0) newPlayer.seekTo(resumeAt)
                    newPlayer.prepare()
                    newPlayer.playWhenReady = true
                    exoPlayer = newPlayer
                    videoEngine = engineWrapper
                }

                PlayerEngine.IJK -> {
                    val newPlayer = IjkMediaPlayer()
                    runCatching { IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT) }
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "connect_timeout", 10_000_000L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "read_timeout", 25_000_000L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", userAgent)
                    newPlayer.setOption(
                        IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                        "protocol_whitelist",
                        "async,cache,crypto,file,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data,httpproxy"
                    )
                    requestHeaders["Referer"]?.let {
                        newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "referer", it)
                    }

                    val engineWrapper = IjkVideoEngine(newPlayer, playbackSpeed)
                    newPlayer.setDataSource(episode.url, requestHeaders)
                    newPlayer.setDisplay(holder)
                    newPlayer.setScreenOnWhilePlaying(true)
                    newPlayer.setOnPreparedListener { player ->
                        engineWrapper.markReady()
                        if (resumeAt > 0) player.seekTo(resumeAt)
                        player.start()
                        isPlaying = true
                        isBuffering = false
                    }
                    newPlayer.setOnInfoListener { _, what, _ ->
                        when (what) {
                            IMediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                            IMediaPlayer.MEDIA_INFO_BUFFERING_END,
                            IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
                        }
                        true
                    }
                    newPlayer.setOnCompletionListener { onEpisodeEnded() }
                    newPlayer.setOnErrorListener { _, what, extra ->
                        if (what != -38) onEngineFailed(PlayerEngine.IJK, "IJKPlayer 播放错误（$what/$extra）")
                        true
                    }
                    newPlayer.prepareAsync()
                    videoEngine = engineWrapper
                }

                PlayerEngine.SYSTEM -> {
                    val newPlayer = MediaPlayer()
                    newPlayer.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    val engineWrapper = SystemVideoEngine(newPlayer, playbackSpeed)
                    newPlayer.setDataSource(context, android.net.Uri.parse(episode.url), requestHeaders)
                    newPlayer.setDisplay(holder)
                    newPlayer.setScreenOnWhilePlaying(true)
                    newPlayer.setOnPreparedListener { player ->
                        engineWrapper.markReady()
                        if (resumeAt > 0) player.seekTo(resumeAt.toInt())
                        player.start()
                        isPlaying = true
                        isBuffering = false
                    }
                    newPlayer.setOnInfoListener { _, what, _ ->
                        when (what) {
                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                            MediaPlayer.MEDIA_INFO_BUFFERING_END,
                            MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
                        }
                        true
                    }
                    newPlayer.setOnCompletionListener { onEpisodeEnded() }
                    newPlayer.setOnErrorListener { _, what, extra ->
                        if (what != -38) onEngineFailed(PlayerEngine.SYSTEM, "系统播放器错误（$what/$extra）")
                        true
                    }
                    newPlayer.prepareAsync()
                    videoEngine = engineWrapper
                }
            }
            errorMessage = null
        } catch (error: Exception) {
            releaseCurrentPlayer()
            onEngineFailed(engine, "${engine.label} 初始化失败：${error.message ?: "未知错误"}")
        }
    }

    // ------------------------------------------------------------------
    // 周期任务：进度轮询 / 定时保存 / 自动隐藏 / 提示消失
    // ------------------------------------------------------------------
    LaunchedEffect(videoEngine) {
        while (isActive) {
            val engine = videoEngine
            if (engine != null && engine.isReady) {
                positionMs = engine.currentPosition
                durationMs = engine.duration
                isPlaying = engine.isPlaying
            }
            delay(500L)
        }
    }

    LaunchedEffect(videoEngine, currentEpisodeIndex) {
        if (videoEngine == null) return@LaunchedEffect
        while (isActive) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            if (videoEngine?.isPlaying == true) persistProgress()
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isBuffering, interactionTick, errorMessage, altResults, altLoading) {
        if (controlsVisible && isPlaying && !isBuffering && errorMessage == null && altResults.isEmpty() && !altLoading) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(infoMessage) {
        if (infoMessage != null) {
            delay(3_000L)
            infoMessage = null
        }
    }

    // ------------------------------------------------------------------
    // 焦点
    // ------------------------------------------------------------------
    LaunchedEffect(controlsVisible, errorMessage, altResults.size) {
        // 给一帧时间让目标节点进入组合树
        delay(30)
        runCatching {
            when {
                altResults.isNotEmpty() -> altFocus.requestFocus()
                errorMessage != null -> retryFocus.requestFocus()
                controlsVisible -> playFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    // ------------------------------------------------------------------
    // 生命周期：常亮 / 沉浸 / 后台暂停并保存进度
    // ------------------------------------------------------------------
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlayingBeforeStop = videoEngine?.isPlaying == true
                    persistProgress()
                    videoEngine?.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (wasPlayingBeforeStop) videoEngine?.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            persistProgress()
            releaseCurrentPlayer()
        }
    }

    // ------------------------------------------------------------------
    // 返回键：先关面板 → 再隐藏控制条 → 最后退出
    // ------------------------------------------------------------------
    BackHandler(enabled = altResults.isNotEmpty() || altLoading || (controlsVisible && isPlaying && errorMessage == null)) {
        when {
            altResults.isNotEmpty() || altLoading -> {
                altResults = emptyList()
                altMessage = null
                altLoading = false
            }
            else -> controlsVisible = false
        }
    }

    // ------------------------------------------------------------------
    // 遥控器按键
    // ------------------------------------------------------------------
    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        val isDown = event.type == KeyEventType.KeyDown
        val code = native.keyCode
        val hidden = !controlsVisible

        return when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (isDown && native.repeatCount == 0) togglePlay()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (isDown) { videoEngine?.play(); isPlaying = true; wakeControls() }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (isDown) { videoEngine?.pause(); isPlaying = false; wakeControls() }
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (isDown) seekBy(seekStepMs * 3)
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (isDown) seekBy(-seekStepMs * 3)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (isDown && native.repeatCount == 0 && hasNext) playEpisode(currentEpisodeIndex + 1)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (isDown && native.repeatCount == 0 && hasPrevious) playEpisode(currentEpisodeIndex - 1)
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (isDown && native.repeatCount == 0) {
                    if (controlsVisible) controlsVisible = false else wakeControls()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (hidden) {
                    if (isDown && native.repeatCount == 0) togglePlay()
                    true
                } else {
                    if (isDown) interactionTick += 1
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (hidden) {
                    if (isDown) seekBy(-seekStepMs)
                    true
                } else {
                    if (isDown) interactionTick += 1
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (hidden) {
                    if (isDown) seekBy(seekStepMs)
                    true
                } else {
                    if (isDown) interactionTick += 1
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (hidden) {
                    if (isDown) wakeControls()
                    true
                } else {
                    if (isDown) interactionTick += 1
                    false
                }
            }
            else -> {
                if (isDown && !hidden) interactionTick += 1
                false
            }
        }
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { handleKey(it) }
    ) {
        // 视频画面
        when (sessionEngine) {
            PlayerEngine.EXO -> {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            isFocusable = false
                            isFocusableInTouchMode = false
                            keepScreenOn = true
                        }
                    },
                    update = { view -> view.player = exoPlayer },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
            PlayerEngine.IJK, PlayerEngine.SYSTEM -> {
                AndroidView(
                    factory = { viewContext ->
                        SurfaceView(viewContext).apply {
                            isFocusable = false
                            keepScreenOn = true
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    surfaceHolder = holder
                                }

                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    if (surfaceHolder !== holder) surfaceHolder = holder
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    // Surface 没了（切后台等），记下位置，回来重建时从这里续播
                                    videoEngine?.takeIf { it.isReady }?.let { pendingStartPosition = it.currentPosition }
                                    if (surfaceHolder === holder) surfaceHolder = null
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            null -> Unit
        }

        // 缓冲指示
        if ((isBuffering || detailLoading) && errorMessage == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = TvFocusColor, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (detailLoading) "正在获取播放地址..." else "缓冲中...",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp
                )
            }
        }

        // 顶部提示
        infoMessage?.let { message ->
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (controlsVisible) 80.dp else 28.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        // 控制层
        if (controlsVisible) {
            PlayerControls(
                title = detail?.summary?.title ?: "加载中",
                subtitle = buildString {
                    append(sessionEngine?.label ?: "准备中")
                    currentEpisode?.let { append("  ·  ${it.name}") }
                    sourceConfig?.let { append("  ·  ${it.name}") }
                    if (playbackSpeed != 1.0f) append("  ·  ${playbackSpeed}x")
                },
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                episodes = episodes,
                currentEpisodeIndex = currentEpisodeIndex,
                playbackSpeed = playbackSpeed,
                sessionEngine = sessionEngine,
                seekStepSeconds = (seekStepMs / 1000).toInt(),
                playFocus = playFocus,
                onBack = onBack,
                onTogglePlay = { togglePlay() },
                onSeekBack = { seekBy(-seekStepMs) },
                onSeekForward = { seekBy(seekStepMs) },
                onPrevious = { if (hasPrevious) playEpisode(currentEpisodeIndex - 1) },
                onNext = { if (hasNext) playEpisode(currentEpisodeIndex + 1) },
                onSelectEpisode = { playEpisode(it) },
                onSpeed = { setSpeed(it) },
                onEngine = { switchEngineManually(it) },
                onFindAlternatives = { findAlternatives() },
                onInteract = { interactionTick += 1 }
            )
        }

        // 错误层
        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 17.sp,
                    lineHeight = 24.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.focusGroup()) {
                    TvButton(onClick = { retry() }, modifier = Modifier.focusRequester(retryFocus)) { Text("重试") }
                    TvOutlinedButton(onClick = { findAlternatives() }) { Text("换源播放") }
                    if (sessionEngine != null) {
                        PlayerEngine.entries.filter { it != sessionEngine }.forEach { engine ->
                            TvOutlinedButton(onClick = { switchEngineManually(engine) }) { Text("试试 ${engine.label}") }
                        }
                    }
                    TvOutlinedButton(onClick = onBack) { Text("返回") }
                }
            }
        }

        // 换源层
        if (altLoading || altResults.isNotEmpty() || altMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            altLoading -> "正在其它源中查找《${detail?.summary?.title.orEmpty()}》..."
                            altMessage != null -> altMessage.orEmpty()
                            else -> "找到 ${altResults.size} 个可换源，选择后从当前进度继续播放"
                        },
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    if (altLoading) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(color = TvFocusColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(altResults, key = { it.key }) { item ->
                        TvOutlinedButton(
                            onClick = { switchToAlternative(item) },
                            modifier = if (item == altResults.firstOrNull()) Modifier.focusRequester(altFocus) else Modifier
                        ) {
                            Column {
                                Text(item.sourceName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = listOfNotNull(item.remarks, item.year).joinToString(" · ").ifBlank { item.title },
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (!altLoading) {
                        item(key = "close-alt") {
                            TvOutlinedButton(
                                onClick = { altResults = emptyList(); altMessage = null },
                                modifier = if (altResults.isEmpty()) Modifier.focusRequester(altFocus) else Modifier
                            ) { Text("关闭") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    episodes: List<Episode>,
    currentEpisodeIndex: Int,
    playbackSpeed: Float,
    sessionEngine: PlayerEngine?,
    seekStepSeconds: Int,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onEngine: (PlayerEngine) -> Unit,
    onFindAlternatives: () -> Unit,
    onInteract: () -> Unit
) {
    val episodeListState = rememberLazyListState()

    LaunchedEffect(currentEpisodeIndex, episodes.size) {
        val position = episodes.indexOfFirst { it.index == currentEpisodeIndex }
        if (position >= 0) runCatching { episodeListState.scrollToItem((position - 2).coerceAtLeast(0)) }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回"
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TvOutlinedButton(onClick = onFindAlternatives) { Text("换源") }
        }

        Spacer(Modifier.weight(1f))

        // 底栏
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            // 选集条
            if (episodes.size > 1) {
                LazyRow(
                    state = episodeListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(episodes, key = { it.index }) { episode ->
                        TvChip(
                            onClick = { onSelectEpisode(episode.index) },
                            selected = episode.index == currentEpisodeIndex
                        ) {
                            Text(episode.name, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 进度
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatClock(positionMs),
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.width(72.dp)
                )
                LinearProgressIndicator(
                    progress = {
                        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp),
                    color = TvFocusColor,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text(
                    text = if (durationMs > 0) formatClock(durationMs) else "--:--",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.width(72.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            // 主控制
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusGroup()
            ) {
                TvButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.focusRequester(playFocus)
                ) {
                    Text(if (isPlaying) "暂停" else "播放", fontSize = 16.sp)
                }
                TvOutlinedButton(onClick = onSeekBack) { Text("-${seekStepSeconds}s") }
                TvOutlinedButton(onClick = onSeekForward) { Text("+${seekStepSeconds}s") }
                TvOutlinedButton(onClick = onPrevious, enabled = hasPrevious) { Text("上一集") }
                TvOutlinedButton(onClick = onNext, enabled = hasNext) { Text("下一集") }
                TvOutlinedButton(onClick = onFindAlternatives) { Text("换源") }
            }
            Spacer(Modifier.height(8.dp))

            // 倍速 + 内核
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusGroup()
            ) {
                Text(SPEED_OPTIONS_LABEL, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                speedOptions.forEach { speed ->
                    TvChip(
                        onClick = { if (playbackSpeed != speed) onSpeed(speed) else onInteract() },
                        selected = playbackSpeed == speed
                    ) {
                        Text("${speed}x", fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.width(18.dp))
                Text("内核", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                PlayerEngine.entries.forEach { engine ->
                    TvChip(
                        onClick = { if (sessionEngine != engine) onEngine(engine) else onInteract() },
                        selected = sessionEngine == engine
                    ) {
                        Text(engine.label, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun guessMimeType(url: String): String? {
    val lower = url.lowercase()
    return when {
        lower.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
        lower.contains(".mpd") -> MimeTypes.APPLICATION_MPD
        else -> null
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ----------------------------------------------------------------------
// 引擎抽象
// ----------------------------------------------------------------------
private interface VideoEngine {
    val isReady: Boolean
    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long
    fun play()
    fun pause()
    fun setSpeed(speed: Float)
    fun seekTo(position: Long)
    fun release()
}

private class ExoVideoEngine(
    private val player: ExoPlayer
) : VideoEngine {
    private var ready = false
    override val isReady: Boolean get() = ready
    override val isPlaying: Boolean get() = runCatching { player.isPlaying }.getOrDefault(false)
    override val currentPosition: Long get() = runCatching { player.currentPosition }.getOrDefault(0L)
    override val duration: Long get() = runCatching { player.duration }.getOrDefault(0L).let { if (it > 0) it else 0L }
    fun markReady() { ready = true }
    override fun play() { runCatching { player.play() } }
    override fun pause() { runCatching { player.pause() } }
    override fun setSpeed(speed: Float) { runCatching { player.setPlaybackSpeed(speed) } }
    override fun seekTo(position: Long) { runCatching { player.seekTo(position) } }
    override fun release() { ready = false; runCatching { player.release() } }
}

private class IjkVideoEngine(
    private val player: IjkMediaPlayer,
    initialSpeed: Float
) : VideoEngine {
    private var ready = false
    private var speed = initialSpeed
    override val isReady: Boolean get() = ready
    override val isPlaying: Boolean get() = if (ready) runCatching { player.isPlaying }.getOrDefault(false) else false
    override val currentPosition: Long get() = if (ready) runCatching { player.currentPosition }.getOrDefault(0L) else 0L
    override val duration: Long get() = if (ready) runCatching { player.duration }.getOrDefault(0L).coerceAtLeast(0L) else 0L
    fun markReady() {
        ready = true
        setSpeed(speed)
    }
    override fun play() { runCatching { player.start() } }
    override fun pause() { runCatching { player.pause() } }
    override fun setSpeed(speed: Float) {
        this.speed = speed
        if (ready) runCatching { player.setSpeed(speed) }
    }
    override fun seekTo(position: Long) { runCatching { player.seekTo(position) } }
    override fun release() {
        ready = false
        runCatching { player.setDisplay(null) }
        runCatching { player.reset() }
        runCatching { player.release() }
    }
}

private class SystemVideoEngine(
    private val player: MediaPlayer,
    initialSpeed: Float
) : VideoEngine {
    private var ready = false
    private var speed = initialSpeed
    override val isReady: Boolean get() = ready
    override val isPlaying: Boolean get() = if (ready) runCatching { player.isPlaying }.getOrDefault(false) else false
    override val currentPosition: Long get() = if (ready) runCatching { player.currentPosition.toLong() }.getOrDefault(0L) else 0L
    override val duration: Long get() = if (ready) runCatching { player.duration.toLong() }.getOrDefault(0L).coerceAtLeast(0L) else 0L
    fun markReady() {
        ready = true
        if (speed != 1.0f) setSpeed(speed)
    }
    override fun play() { runCatching { player.start() } }
    override fun pause() { runCatching { player.pause() } }
    override fun setSpeed(speed: Float) {
        this.speed = speed
        if (!ready) return
        runCatching {
            val wasPlaying = player.isPlaying
            player.playbackParams = player.playbackParams.setSpeed(speed)
            if (!wasPlaying) player.pause()
        }
    }
    override fun seekTo(position: Long) { runCatching { player.seekTo(position.toInt()) } }
    override fun release() {
        ready = false
        runCatching { player.setDisplay(null) }
        runCatching { player.reset() }
        runCatching { player.release() }
    }
}
