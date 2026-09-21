package cx.n181.stv.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cx.n181.stv.StvApp
import cx.n181.stv.data.HistoryItem
import cx.n181.stv.data.PlayerEngine
import cx.n181.stv.data.SourceConfig
import cx.n181.stv.data.VideoDetail
import cx.n181.stv.data.VideoSummary
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import android.content.Context
import android.content.ContextWrapper

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
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val scope = rememberCoroutineScope()
    val playFocusRequester = remember { FocusRequester() }

    var activeSourceKey by remember { mutableStateOf(sourceKey) }
    var activeVideoId by remember { mutableStateOf(videoId) }
    var currentEpisodeIndex by remember { mutableStateOf(episodeIndex) }
    var startPosition by remember { mutableStateOf(startPositionMs) }
    var sourceConfig by remember { mutableStateOf<SourceConfig?>(null) }
    var allSources by remember { mutableStateOf<List<SourceConfig>>(emptyList()) }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var playerEngine by remember { mutableStateOf<PlayerEngine?>(null) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var videoEngine by remember { mutableStateOf<VideoEngine?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var altResults by remember { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var altLoading by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    val episodes = detail?.playGroups?.flatMap { it.episodes }.orEmpty()
    val currentEpisode = episodes.firstOrNull { it.index == currentEpisodeIndex }

    fun releaseCurrentPlayer() {
        exoPlayer?.release()
        exoPlayer = null
        videoEngine?.release()
        videoEngine = null
    }

    fun switchEngine(engine: PlayerEngine) {
        scope.launch {
            container.settingsRepository.savePlayerEngine(engine)
            playerEngine = engine
            reloadTick += 1
        }
    }

    fun wakeControls() {
        controlsVisible = true
        interactionTick += 1
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        videoEngine?.setSpeed(speed)
        wakeControls()
    }

    LaunchedEffect(Unit) {
        playerEngine = container.settingsRepository.loadPlayerEngine()
    }

    LaunchedEffect(activeSourceKey, activeVideoId) {
        errorMessage = null
        altResults = emptyList()
        detail = null
        releaseCurrentPlayer()

        try {
            val config = container.appConfigRepository.load()
            allSources = config.sources.filter { it.enabled }.sortedBy { it.order }
            val source = allSources.firstOrNull { it.key == activeSourceKey }
                ?: throw IllegalStateException("资源不存在")
            sourceConfig = source
            detail = container.macCmsClient.detail(source, activeVideoId)
        } catch (error: Exception) {
            errorMessage = error.message ?: "加载失败"
        }
    }

    LaunchedEffect(
        detail,
        currentEpisodeIndex,
        playerEngine,
        surfaceHolder,
        reloadTick
    ) {
        val currentDetail = detail ?: return@LaunchedEffect
        val engine = playerEngine ?: return@LaunchedEffect
        val episode = currentDetail.playGroups
            .flatMap { group -> group.episodes }
            .firstOrNull { it.index == currentEpisodeIndex }

        if (episode == null) {
            errorMessage = "没有找到可播放的剧集"
            return@LaunchedEffect
        }
        if (engine != PlayerEngine.EXO && surfaceHolder == null) {
            return@LaunchedEffect
        }

        releaseCurrentPlayer()
        val config = container.appConfigRepository.load()
        val source = allSources.firstOrNull { it.key == currentDetail.summary.sourceKey }
        val userAgent = "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"

        val requestHeaders = buildMap {
            put("Accept", "*/*")
            put("User-Agent", userAgent)
            if (source != null) {
                runCatching {
                    val host = source.api.toHttpUrl()
                    put("Referer", "${host.scheme}://${host.host}/")
                }
                putAll(source.headers)
            }
        }.toMap()

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
                            config.player.minBufferSeconds * 1_000,
                            config.player.maxBufferSeconds * 1_000,
                            config.player.playbackStartBufferSeconds * 1_000,
                            config.player.rebufferSeconds * 1_000
                        )
                        .build()

                    val newPlayer = ExoPlayer.Builder(context)
                        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                        .setLoadControl(loadControl)
                        .setSeekBackIncrementMs(config.player.seekStepSeconds * 1_000L)
                        .setSeekForwardIncrementMs(config.player.seekStepSeconds * 1_000L)
                        .build()

                    newPlayer.setMediaItem(
                        MediaItem.Builder()
                            .setUri(episode.url)
                            .setMimeType(
                                when {
                                    episode.url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                                    episode.url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
                                    else -> null
                                }
                            )
                            .setMediaMetadata(
                                MediaMetadata.Builder().setTitle(currentDetail.summary.title).build()
                            )
                            .build()
                    )
                    newPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            isPlaying = newPlayer.isPlaying
                            if (playbackState == Player.STATE_ENDED && config.player.autoNext) {
                                val next = episodes.firstOrNull { it.index == currentEpisodeIndex + 1 }
                                if (next != null) {
                                    currentEpisodeIndex = next.index
                                    startPosition = 0L
                                }
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            errorMessage = "ExoPlayer 无法播放（${error.errorCode}），请切换内核或换源"
                        }
                    })

                    newPlayer.setPlaybackSpeed(playbackSpeed)
                    newPlayer.seekTo(startPosition)
                    newPlayer.prepare()
                    newPlayer.playWhenReady = true
                    exoPlayer = newPlayer
                    videoEngine = ExoVideoEngine(newPlayer)
                }

                PlayerEngine.IJK -> {
                    val newPlayer = IjkMediaPlayer()
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", 1L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "connect_timeout", 10_000_000L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "read_timeout", 25_000_000L)
                    newPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", userAgent)
                    newPlayer.setOption(
                        IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                        "referer",
                        requestHeaders["Referer"].orEmpty()
                    )

                    newPlayer.setDataSource(episode.url, requestHeaders)
                    newPlayer.setSurface(surfaceHolder?.surface)
                    val engineWrapper = IjkVideoEngine(newPlayer, playbackSpeed)
                    newPlayer.setOnPreparedListener { player ->
                        engineWrapper.markReady()
                        if (startPosition > 0) player.seekTo(startPosition)
                        player.start()
                    }
                    newPlayer.setOnCompletionListener {
                        if (config.player.autoNext) {
                            val next = episodes.firstOrNull { it.index == currentEpisodeIndex + 1 }
                            if (next != null) {
                                currentEpisodeIndex = next.index
                                startPosition = 0L
                            }
                        }
                    }
                    newPlayer.setOnErrorListener { _, what, extra ->
                        if (what == -38) return@setOnErrorListener true
                        errorMessage = "IJKPlayer 播放错误（$what/$extra），请切换内核或换源"
                        true
                    }
                    newPlayer.prepareAsync()
                    videoEngine = engineWrapper
                }

                PlayerEngine.SYSTEM -> {
                    val newPlayer = MediaPlayer()
                    newPlayer.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    newPlayer.setDataSource(context, android.net.Uri.parse(episode.url), requestHeaders)
                    newPlayer.setSurface(surfaceHolder?.surface)
                    val engineWrapper = SystemVideoEngine(newPlayer, playbackSpeed)
                    newPlayer.setOnPreparedListener { player ->
                        engineWrapper.markReady()
                        if (startPosition > 0) player.seekTo(startPosition.toInt())
                        player.start()
                    }
                    newPlayer.setOnCompletionListener {
                        if (config.player.autoNext) {
                            val next = episodes.firstOrNull { it.index == currentEpisodeIndex + 1 }
                            if (next != null) {
                                currentEpisodeIndex = next.index
                                startPosition = 0L
                            }
                        }
                    }
                    newPlayer.setOnErrorListener { _, what, extra ->
                        if (what == -38) return@setOnErrorListener true
                        errorMessage = "系统播放器错误（$what/$extra），请切换内核或换源"
                        true
                    }
                    newPlayer.prepareAsync()
                    videoEngine = engineWrapper
                }
            }
            startPosition = 0L
            errorMessage = null
        } catch (error: Exception) {
            releaseCurrentPlayer()
            errorMessage = "${engineLabel(engine)} 初始化失败：${error.message ?: "未知错误"}"
        }
    }

    DisposableEffect(Unit) {
        onDispose { releaseCurrentPlayer() }
    }

    LaunchedEffect(videoEngine) {
        while (isActive) {
            val current = videoEngine
            if (current != null && current.isReady) {
                positionMs = current.currentPosition
                durationMs = current.duration
                isPlaying = current.isPlaying
            }
            delay(1_000L)
        }
    }

    LaunchedEffect(videoEngine, detail, currentEpisodeIndex) {
        if (videoEngine == null || detail == null) return@LaunchedEffect
        while (isActive) {
            delay(10_000L)
            val currentDetail = detail ?: continue
            val currentSource = sourceConfig ?: continue
            val episode = currentDetail.playGroups
                .flatMap { group -> group.episodes }
                .firstOrNull { it.index == currentEpisodeIndex } ?: continue
            val current = videoEngine ?: continue
            if (!current.isReady) continue
            val position = current.currentPosition
            val duration = current.duration

            if (position > 1_000L && duration > 0L && position < duration - 2_000L) {
                container.historyStore.saveHistory(
                    HistoryItem(
                        sourceKey = currentDetail.summary.sourceKey,
                        sourceName = currentSource.name,
                        videoId = currentDetail.summary.videoId,
                        title = currentDetail.summary.title,
                        poster = currentDetail.summary.poster,
                        episodeIndex = currentEpisodeIndex,
                        episodeName = episode.name,
                        positionMs = position,
                        durationMs = duration,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun togglePlay() {
        val current = videoEngine ?: return
        if (current.isPlaying) current.pause() else current.play()
    }

    fun seekBackByStep() {
        val current = videoEngine ?: return
        current.seekTo((current.currentPosition - 10_000L).coerceAtLeast(0L))
    }

    fun seekForwardByStep() {
        val current = videoEngine ?: return
        val target = current.currentPosition + 10_000L
        current.seekTo(if (current.duration > 0) target.coerceAtMost(current.duration) else target)
    }

    fun retry() {
        releaseCurrentPlayer()
        errorMessage = null
        reloadTick += 1
    }

    fun findAlternatives() {
        val title = detail?.summary?.title ?: return
        scope.launch {
            altLoading = true
            errorMessage = null
            altResults = container.mediaSearchRepository.search(
                keyword = title,
                sources = allSources.filter { it.key != activeSourceKey }
            ).filter { it.title == title }
            altLoading = altResults.isEmpty()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        wakeControls()
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                wakeControls()
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        togglePlay()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        videoEngine?.play()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        videoEngine?.pause()
                        true
                    }
                    else -> false
                }
            },
        color = Color.Black
    ) {
        Box(Modifier.fillMaxSize()) {
            when (playerEngine) {
                PlayerEngine.EXO -> {
                    AndroidView(
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            }
                        },
                        update = { view -> view.player = exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                PlayerEngine.IJK, PlayerEngine.SYSTEM -> {
                    AndroidView(
                        factory = { viewContext ->
                            SurfaceView(viewContext).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        surfaceHolder = holder
                                    }

                                    override fun surfaceChanged(
                                        holder: SurfaceHolder,
                                        format: Int,
                                        width: Int,
                                        height: Int
                                    ) {
                                        surfaceHolder = holder
                                    }

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        if (surfaceHolder == holder) surfaceHolder = null
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                null -> Unit
            }

    LaunchedEffect(controlsVisible, isPlaying, interactionTick, videoEngine?.isReady) {
        val ready = videoEngine?.isReady == true
        if (controlsVisible && ready && isPlaying) {
            delay(5_000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            playFocusRequester.requestFocus()
        }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

            if (controlsVisible) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = detail?.summary?.title ?: "加载中",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append(playerEngine?.let { engineLabel(it) } ?: "准备中")
                                currentEpisode?.let { append(" · ${it.name}") }
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    TvOutlinedButton(onClick = { findAlternatives() }) {
                        Text("换源")
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = {
                            if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            else 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvButton(
                            onClick = { togglePlay() },
                            modifier = Modifier.focusRequester(playFocusRequester)
                        ) {
                            Text(if (isPlaying) "暂停" else "播放")
                        }
                        TvOutlinedButton(onClick = { seekBackByStep() }) { Text("-10s") }
                        TvOutlinedButton(onClick = { seekForwardByStep() }) { Text("+10s") }
                        TvOutlinedButton(
                            onClick = {
                                episodes.firstOrNull { it.index == currentEpisodeIndex - 1 }?.let {
                                    currentEpisodeIndex = it.index
                                    startPosition = 0L
                                }
                            }
                        ) { Text("上一集") }
                        TvOutlinedButton(
                            onClick = {
                                episodes.firstOrNull { it.index == currentEpisodeIndex + 1 }?.let {
                                    currentEpisodeIndex = it.index
                                    startPosition = 0L
                                }
                            }
                        ) { Text("下一集") }
                        Text(
                            text = "${positionMs / 60_000}:${(positionMs % 60_000) / 1_000} / ${durationMs / 60_000}:${(durationMs % 60_000) / 1_000}",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1.0f, 1.5f, 2.0f).forEach { speed ->
                            TvChip(
                                onClick = {
                                    if (playbackSpeed != speed) setPlaybackSpeed(speed)
                                },
                                enabled = true,
                                selected = playbackSpeed == speed
                            ) {
                                Text("${speed}x")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        Spacer(Modifier.width(12.dp))
                        PlayerEngine.entries.forEach { engine ->
                                TvChip(
                                    onClick = {
                                        if (playerEngine != engine) switchEngine(engine)
                                    },
                                    enabled = true,
                                    selected = playerEngine == engine
                                ) {
                                    Text(engineLabel(engine))
                            }
                        }
                    }
                }
            }

            }

            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(onClick = { retry() }) { Text("重试") }
                        TvOutlinedButton(onClick = { findAlternatives() }) { Text("查找其他源") }
                    }
                }
            }

            if (altLoading) {
                Text(
                    text = "正在查找其他源...",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (altResults.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .padding(14.dp)
                ) {
                    Text("可换源播放", color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        altResults.take(8).forEach { item ->
                            TvOutlinedButton(onClick = {
                                activeSourceKey = item.sourceKey
                                activeVideoId = item.videoId
                                currentEpisodeIndex = 0
                                startPosition = 0L
                                altResults = emptyList()
                            }) {
                                Text(item.sourceName)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun engineLabel(engine: PlayerEngine): String = when (engine) {
    PlayerEngine.EXO -> "Exo"
    PlayerEngine.IJK -> "IJK"
    PlayerEngine.SYSTEM -> "系统"
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
    override val isReady: Boolean get() = true
    override val isPlaying: Boolean get() = player.isPlaying
    override val currentPosition: Long get() = player.currentPosition
    override val duration: Long get() = if (player.duration > 0) player.duration else 0L
    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun setSpeed(speed: Float) {
        runCatching { player.setPlaybackSpeed(speed) }
    }
    override fun seekTo(position: Long) = player.seekTo(position)
    override fun release() = player.release()
}

private class IjkVideoEngine(
    private val player: IjkMediaPlayer,
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
        setSpeed(speed)
    }
    override fun play() {
        runCatching { player.start() }
    }
    override fun pause() {
        runCatching { player.pause() }
    }
    override fun setSpeed(speed: Float) {
        this.speed = speed
        runCatching { player.setSpeed(speed) }
    }
    override fun seekTo(position: Long) {
        runCatching { player.seekTo(position) }
    }
    override fun release() {
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
        setSpeed(speed)
    }
    override fun play() {
        runCatching { player.start() }
    }
    override fun pause() {
        runCatching { player.pause() }
    }
    override fun setSpeed(speed: Float) {
        this.speed = speed
        runCatching {
            val params = player.playbackParams.setSpeed(speed)
            player.playbackParams = params
        }
    }
    override fun seekTo(position: Long) {
        runCatching { player.seekTo(position.toInt()) }
    }
    override fun release() {
        runCatching { player.release() }
    }
}
