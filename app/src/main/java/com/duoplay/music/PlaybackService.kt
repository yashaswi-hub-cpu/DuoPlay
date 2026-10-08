package com.duoplay.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Runs the player as a foreground media service, so music keeps playing
 * with the screen off and while you use other apps.
 * Files are played straight from their original bytes: no re-encoding.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())

    private val saveTick = object : Runnable {
        override fun run() {
            session?.player?.let { store.lastPosition = it.currentPosition }
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = Store(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                store.lastIndex = player.currentMediaItemIndex
                store.lastPosition = player.currentPosition
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(saveTick)
                if (isPlaying) {
                    handler.postDelayed(saveTick, 5000)
                } else {
                    store.lastIndex = player.currentMediaItemIndex
                    store.lastPosition = player.currentPosition
                }
            }
        })

        // Tapping the notification (or lock-screen player) opens the app.
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.duoplay.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveTick)
        session?.run {
            store.lastIndex = player.currentMediaItemIndex
            store.lastPosition = player.currentPosition
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
