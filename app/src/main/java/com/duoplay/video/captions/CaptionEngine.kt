package com.duoplay.video.captions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * No-op caption engine. Vosk offline captions were dropped from Duo Play.
 * This stub keeps PlayerScreen compiling without dragging in the native libs.
 */
class CaptionEngine(@Suppress("UNUSED_PARAMETER") ctx: Context) {
    val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Idle)
    var active: Boolean = false
    val model: CaptionModel = CaptionModel.Base

    fun feed(
        @Suppress("UNUSED_PARAMETER") bytes: ByteArray,
        @Suppress("UNUSED_PARAMETER") startMs: Long,
        @Suppress("UNUSED_PARAMETER") endMs: Long
    ) { }

    fun reset() { }

    fun captionAt(@Suppress("UNUSED_PARAMETER") posMs: Long): String? = null

    fun prefetchIfWanted() { }

    fun ensureReady() { }

    fun isDownloaded(@Suppress("UNUSED_PARAMETER") m: CaptionModel): Boolean = false

    fun switchModel(@Suppress("UNUSED_PARAMETER") m: CaptionModel) { }
}
