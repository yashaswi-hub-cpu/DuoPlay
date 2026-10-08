package com.duoplay.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.media3.session.MediaController

@Composable
fun MyMusicRoot(controller: MediaController?) {
    val ctx = LocalContext.current
    val store = Store(ctx)
    MusicApp(store, controller)
}
