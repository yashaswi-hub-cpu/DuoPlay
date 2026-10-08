package com.duoplay

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.duoplay.video.data.HistoryStore
import com.duoplay.video.ui.VideoSource
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)

    private val historyStore by lazy { HistoryStore(applicationContext) }
    private var pendingVideo by mutableStateOf<VideoSource?>(null)
    private var hasStorage by mutableStateOf(false)

    private fun storagePermission() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun checkStorage() =
        ContextCompat.checkSelfPermission(this, storagePermission()) ==
            PackageManager.PERMISSION_GRANTED

    private val storageRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { hasStorage = it }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleVideoIntent(intent)

        setContent {
            DuoPlayApp(
                controller = controller,
                historyStore = historyStore,
                hasStorage = hasStorage,
                onGrantStorage = { storageRequest.launch(storagePermission()) },
                pendingVideo = pendingVideo,
                onVideoConsumed = { pendingVideo = null }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, com.duoplay.music.PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        controllerFuture = f
        f.addListener(
            { runCatching { controller = f.get() } },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        hasStorage = checkStorage()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVideoIntent(intent)
    }

    private fun handleVideoIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri -> pendingVideo = VideoSource(uri, displayName(uri)) }
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.let { return it }
                }
            }
        } catch (_: Exception) { }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
    }
}
