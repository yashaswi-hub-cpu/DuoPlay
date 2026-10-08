package com.duoplay.video.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.duoplay.video.captions.CaptionEngine
import com.duoplay.video.data.DeviceVideo
import com.duoplay.video.data.HistoryItem
import com.duoplay.video.data.HistoryStore

@Composable
fun ClassReelRoot(
    store: HistoryStore,
    hasStorage: Boolean,
    onGrantStorage: () -> Unit,
    pendingVideo: VideoSource?,
    onVideoConsumed: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { CaptionEngine(ctx) }
    var current by remember { mutableStateOf<VideoSource?>(null) }

    LaunchedEffect(pendingVideo) {
        if (pendingVideo != null) {
            current = pendingVideo
            onVideoConsumed()
        }
    }

    fun nameFor(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/') ?: "Video"

    ClassReelTheme {
        Box(Modifier.fillMaxSize().background(Ink)) {
            val video = current
            if (video == null) {
                HomeScreen(
                    store = store,
                    hasStorage = hasStorage,
                    onGrantStorage = onGrantStorage,
                    onOpenExternal = { uri -> current = VideoSource(uri, nameFor(uri)) },
                    onOpenVideo = { v: DeviceVideo -> current = VideoSource(v.uri, v.name) },
                    onOpen = { item: HistoryItem ->
                        current = VideoSource(Uri.parse(item.uri), item.name)
                    }
                )
            } else {
                PlayerScreen(
                    video = video,
                    store = store,
                    engine = engine,
                    onClose = { current = null }
                )
            }
        }
    }
}
