package com.duoplay.video.captions

enum class CaptionModel(val label: String, val hint: String, val sizeMb: Int) {
    Base("Base", "Smaller, faster", 40),
    Small("Small", "Balanced", 90),
    Medium("Medium", "Better accuracy", 200)
}
