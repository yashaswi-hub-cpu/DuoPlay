package com.duoplay.video.captions

sealed class ModelState {
    data object Idle : ModelState()
    data class Downloading(val pct: Int) : ModelState()
    data object Loading : ModelState()
    data object Ready : ModelState()
    data class Error(val msg: String) : ModelState()
}
