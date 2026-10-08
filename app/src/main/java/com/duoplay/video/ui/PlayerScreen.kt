package com.duoplay.video.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.duoplay.video.captions.AudioTap
import com.duoplay.video.captions.CaptionEngine
import com.duoplay.video.captions.CaptionModel
import com.duoplay.video.captions.ModelState
import com.duoplay.video.data.HistoryItem
import com.duoplay.video.data.HistoryStore
import com.duoplay.video.util.fmtTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class VideoSource(val uri: Uri, val name: String)

private enum class Sheet { None, Speed, Captions, Jump }

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

private fun speedLabel(s: Float) = if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"

@OptIn(UnstableApi::class)
private fun buildPlayer(ctx: Context, tap: AudioTap): ExoPlayer {
    val factory = object : DefaultRenderersFactory(ctx) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(false)
                .setAudioProcessors(arrayOf<AudioProcessor>(tap))
                .build()
    }
    factory.setEnableDecoderFallback(true)
    // Async codec queueing keeps the decoder fed on its own thread: fewer dropped frames (VLC-like smoothness)
    factory.forceEnableMediaCodecAsynchronousQueueing()
    // Keep 60 s of already-played video in memory so going back is instant,
    // and buffer further ahead so playback never stalls.
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(20_000, 60_000, 400, 1_000)
        .setBackBuffer(60_000, true)
        .build()
    return ExoPlayer.Builder(ctx, factory)
        .setLoadControl(load)
        .build()
        // Frame-exact seeks (skip buttons, jump to time, resume land on the exact second).
        // Fast keyframe seeks are used only while dragging the seek bar.
        .apply { setSeekParameters(SeekParameters.EXACT) }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    video: VideoSource,
    store: HistoryStore,
    engine: CaptionEngine,
    onClose: () -> Unit
) {
    val activity = LocalContext.current as ComponentActivity
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val tap = remember {
        AudioTap(
            onChunk = { b, s, e -> engine.feed(b, s, e) },
            onDiscontinuity = { engine.reset() }
        )
    }
    val exo = remember { buildPlayer(activity, tap) }

    var isPlaying by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var ccOn by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    var resumeAfterScrub by remember { mutableStateOf(false) }
    var lastScrubSeek by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var landscape by remember { mutableStateOf(false) }
    val modelState by engine.state.collectAsState()

    fun saveProgress() {
        val d = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: durMs
        val p = exo.currentPosition.coerceAtLeast(0)
        if (p <= 0 && d <= 0) return
        store.upsert(
            HistoryItem(
                uri = video.uri.toString(),
                name = video.name,
                positionMs = p,
                durationMs = d,
                lastPlayed = System.currentTimeMillis()
            )
        )
    }

    fun seekTo(target: Long) {
        val d = exo.duration
        val t = target.coerceIn(0L, if (d != C.TIME_UNSET && d > 0) d else Long.MAX_VALUE)
        tap.pendingBaseMs = t // tell the caption engine where the audio will continue from
        exo.seekTo(t)
        posMs = t
        tick++
    }

    fun skip(sec: Int) = seekTo(exo.currentPosition + sec * 1000L)

    // Fullscreen, keep awake
    DisposableEffect(Unit) {
        val c = WindowCompat.getInsetsController(activity.window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Load the video, resuming from history if we have it
    LaunchedEffect(video.uri) {
        val saved = store.get(video.uri.toString())
        val resumable = saved != null && saved.positionMs > 3_000 && !saved.finished
        val start = if (resumable) (saved!!.positionMs - 2_000).coerceAtLeast(0) else 0L
        tap.pendingBaseMs = start
        exo.setMediaItem(MediaItem.fromUri(video.uri), start)
        exo.prepare()
        exo.playWhenReady = true
        posMs = start
        if (start > 0) note = "Resumed from ${fmtTime(start)}"
    }

    // Player events
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durMs = exo.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) saveProgress()
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMsg = "This video can't be played on this phone (${error.errorCodeName})."
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }

    // Save when leaving the app, release on exit
    DisposableEffect(Unit) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) {
                saveProgress()
                exo.pause()
            }
        }
        activity.lifecycle.addObserver(obs)
        onDispose {
            activity.lifecycle.removeObserver(obs)
            saveProgress()
            exo.release()
        }
    }

    // Position ticker, caption lookup, and periodic history save
    LaunchedEffect(Unit) {
        var lastSave = 0L
        var lastCaption = 0L
        while (true) {
            if (!dragging) posMs = exo.currentPosition.coerceAtLeast(0)
            val now = SystemClock.elapsedRealtime()
            if (now - lastCaption >= 200) {
                caption = if (ccOn) engine.captionAt(posMs) else null
                lastCaption = now
            }
            if (now - lastSave > 5_000) {
                if (exo.isPlaying) saveProgress()
                lastSave = now
            }
            // Fast updates while the seek bar is on screen so it glides; relaxed otherwise
            delay(if (controlsVisible) 50L else 200L)
        }
    }

    // If captions were used before, fetch the model while the video plays
    LaunchedEffect(Unit) { engine.prefetchIfWanted() }

    // Captions on/off
    LaunchedEffect(ccOn) {
        tap.enabled = ccOn
        engine.active = ccOn
        if (ccOn) engine.ensureReady()
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, isPlaying, tick, sheet) {
        if (controlsVisible && isPlaying && sheet == Sheet.None) {
            delay(3_500)
            controlsVisible = false
        }
    }

    LaunchedEffect(note) {
        if (note != null) {
            delay(3_000)
            note = null
        }
    }

    BackHandler {
        if (sheet != Sheet.None) sheet = Sheet.None else onClose()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    player = exo
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Tap to show/hide controls, double-tap left/right edge to skip 10s
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        tick++
                    },
                    onDoubleTap = { o ->
                        if (o.x < size.width / 2) skip(-10) else skip(10)
                        controlsVisible = true
                    }
                )
            }
        )

        // Caption status (download / loading / error)
        if (ccOn && modelState !is ModelState.Ready) {
            val msg = when (val s = modelState) {
                is ModelState.Downloading -> "Downloading ${engine.model.label} captions… ${s.pct}%  (one time)"
                ModelState.Loading -> "Getting captions ready…"
                is ModelState.Error -> "Captions failed: ${s.msg}. Tap to retry"
                else -> "Captions starting…"
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .displayCutoutPadding()
                    .padding(top = 76.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = modelState is ModelState.Error) {
                        scope.launch { engine.ensureReady() }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(msg, color = Amber, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }

        // Resume note
        if (note != null) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .displayCutoutPadding()
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Amber)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(note!!, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Live captions
        val capBottom by animateDpAsState(if (controlsVisible) 158.dp else 36.dp, label = "capBottom")
        val cap = caption
        if (ccOn && !cap.isNullOrBlank()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .displayCutoutPadding()
                    .padding(bottom = capBottom, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    cap,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Controls
        AnimatedVisibility(
            visible = controlsVisible && errorMsg == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                        .displayCutoutPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Chalk)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        video.name,
                        color = Chalk,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    ToolChip("CC", ccOn) {
                        if (ccOn) ccOn = false
                        else if (engine.isDownloaded(engine.model)) ccOn = true
                        else sheet = Sheet.Captions
                        tick++
                    }
                    if (ccOn) {
                        Spacer(Modifier.width(6.dp))
                        ToolChip("Lang", false) { sheet = Sheet.Captions }
                    }
                    Spacer(Modifier.width(6.dp))
                    ToolChip(speedLabel(speed), speed != 1f) {
                        sheet = Sheet.Speed
                    }
                    Spacer(Modifier.width(6.dp))
                    ToolChip("Rotate", false) {
                        landscape = !landscape
                        activity.requestedOrientation =
                            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                }

                // Centre play / pause
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Amber)
                        .clickable {
                            if (exo.isPlaying) exo.pause() else {
                                if (exo.playbackState == Player.STATE_ENDED) seekTo(0)
                                exo.play()
                            }
                            tick++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) PauseGlyph() else
                        Icon(Icons.Filled.PlayArrow, "Play", tint = Ink, modifier = Modifier.size(44.dp))
                }

                // Bottom: seek bar + skip chips
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .displayCutoutPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tap the time to jump to any moment
                        Text(
                            fmtTime(if (dragging) dragPos.toLong() else posMs),
                            color = Amber, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Amber.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                .clickable { sheet = Sheet.Jump; tick++ }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                        Slider(
                            value = if (dragging) dragPos else posMs.toFloat().coerceAtMost(maxOf(durMs, 1L).toFloat()),
                            onValueChange = {
                                if (!dragging) {
                                    // Scrub like VLC: pause, then show the frame under your finger
                                    dragging = true
                                    resumeAfterScrub = exo.playWhenReady
                                    exo.pause()
                                    exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                                }
                                dragPos = it
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastScrubSeek > 90) {
                                    lastScrubSeek = now
                                    exo.seekTo(it.toLong())
                                }
                                tick++
                            },
                            onValueChangeFinished = {
                                exo.setSeekParameters(SeekParameters.EXACT)
                                seekTo(dragPos.toLong())
                                if (resumeAfterScrub) exo.play()
                                dragging = false
                            },
                            valueRange = 0f..maxOf(durMs, 1L).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Amber,
                                activeTrackColor = Amber,
                                inactiveTrackColor = Chalk.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(
                            fmtTime(durMs),
                            color = Chalk, fontFamily = FontFamily.Monospace, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkipChip("−30s") { skip(-30) }
                            SkipChip("−20s") { skip(-20) }
                            SkipChip("−10s") { skip(-10) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkipChip("+10s") { skip(10) }
                            SkipChip("+20s") { skip(20) }
                            SkipChip("+30s") { skip(30) }
                        }
                    }
                }
            }
        }

        // Playback error
        errorMsg?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(msg, color = Chalk, textAlign = TextAlign.Center, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Amber)
                        .clickable { onClose() }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) { Text("Go back", color = Ink, fontWeight = FontWeight.Bold) }
            }
        }

        // Sheets
        if (sheet == Sheet.Speed) {
            ModalBottomSheet(onDismissRequest = { sheet = Sheet.None }, containerColor = Slate) {
                Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
                    Text(
                        "Playback speed", color = Chalk, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, fontSize = 22.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SPEEDS.forEach { s ->
                            ToolChip(speedLabel(s), s == speed) {
                                speed = s
                                exo.setPlaybackSpeed(s)
                                sheet = Sheet.None
                            }
                        }
                    }
                }
            }
        }

        if (sheet == Sheet.Jump) {
            JumpSheet(
                currentMs = posMs,
                durMs = durMs,
                onGo = { t ->
                    seekTo(t)
                    sheet = Sheet.None
                    note = "Jumped to ${fmtTime(t)}"
                },
                onDismiss = { sheet = Sheet.None }
            )
        }

        if (sheet == Sheet.Captions) {
            ModalBottomSheet(onDismissRequest = { sheet = Sheet.None }, containerColor = Slate) {
                Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
                    Text(
                        "Captions", color = Chalk, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, fontSize = 22.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Made on your phone while the video plays. Needs internet only for the first download.",
                        color = Muted, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    CaptionModel.values().forEach { m ->
                        val selected = engine.model == m
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Slate2 else Color.Transparent)
                                .border(1.dp, if (selected) Amber else Chalk.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .clickable {
                                    ccOn = true
                                    sheet = Sheet.None
                                    scope.launch { engine.switchModel(m) }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.label, color = Chalk, fontWeight = FontWeight.Bold)
                                Text(m.hint, color = Muted, fontSize = 12.sp)
                            }
                            Text(
                                if (engine.isDownloaded(m)) "Ready" else "~${m.sizeMb} MB",
                                color = if (engine.isDownloaded(m)) Amber else Muted,
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpSheet(
    currentMs: Long,
    durMs: Long,
    onGo: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var h by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    // Where the video is right now, shown faintly in the empty boxes
    val nowS = currentMs / 1000
    val entered = h.isNotEmpty() || m.isNotEmpty() || s.isNotEmpty()
    val totalMs = ((h.toLongOrNull() ?: 0L) * 3600 + (m.toLongOrNull() ?: 0L) * 60 + (s.toLongOrNull() ?: 0L)) * 1000
    val tooFar = durMs > 0 && totalMs > durMs

    fun go() {
        if (!entered) return
        onGo(if (durMs > 0) totalMs.coerceAtMost(durMs) else totalMs)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Slate) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp).imePadding()) {
            Text(
                "Jump to time", color = Chalk, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 22.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Now at ${fmtTime(currentMs)}  of  ${fmtTime(durMs)}",
                color = Muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                TimeField(
                    value = h, hint = "%02d".format(nowS / 3600), label = "hours",
                    imeAction = ImeAction.Next, onChange = { h = it }, onGo = ::go,
                    modifier = Modifier.weight(1f)
                )
                Text(":", color = Chalk, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                TimeField(
                    value = m, hint = "%02d".format((nowS % 3600) / 60), label = "minutes",
                    imeAction = ImeAction.Next, onChange = { m = it }, onGo = ::go,
                    modifier = Modifier.weight(1f)
                )
                Text(":", color = Chalk, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                TimeField(
                    value = s, hint = "%02d".format(nowS % 60), label = "seconds",
                    imeAction = ImeAction.Go, onChange = { s = it }, onGo = ::go,
                    modifier = Modifier.weight(1f)
                )
            }
            if (tooFar) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "This video is only ${fmtTime(durMs)} long, so it will jump to the end.",
                    color = Amber, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolChip("Start", false) { onGo(0L) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (entered) Amber else Amber.copy(alpha = 0.35f))
                        .clickable(enabled = entered) { go() }
                        .padding(horizontal = 36.dp, vertical = 12.dp)
                ) { Text("Go", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun TimeField(
    value: String,
    hint: String,
    label: String,
    imeAction: ImeAction,
    onChange: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val big = TextStyle(
        fontSize = 28.sp, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Slate2)
                .border(1.dp, Chalk.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(2)) },
                singleLine = true,
                textStyle = big.copy(color = Chalk),
                cursorBrush = SolidColor(Amber),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) Text(hint, style = big.copy(color = Chalk.copy(alpha = 0.3f)))
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun PauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 9.dp, height = 30.dp).clip(RoundedCornerShape(3.dp)).background(Ink))
        Box(Modifier.size(width = 9.dp, height = 30.dp).clip(RoundedCornerShape(3.dp)).background(Ink))
    }
}

@Composable
private fun SkipChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(38.dp)
            .widthIn(min = 52.dp)
            .clip(RoundedCornerShape(19.dp))
            .border(1.dp, Chalk.copy(alpha = 0.55f), RoundedCornerShape(19.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Chalk, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToolChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (active) Amber else Color.Black.copy(alpha = 0.45f))
            .border(1.dp, if (active) Amber else Chalk.copy(alpha = 0.45f), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (active) Ink else Chalk,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
