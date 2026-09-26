package com.ancientpersia.rps.game

/**
 * سه حالت دست: سنگ (مشت)، قیچی، کاغذ (کف دست)
 */
enum class Gesture {
    FIST,      // سنگ
    SCISSORS,  // قیچی
    PALM;      // کاغذ

    companion object {
        fun random(): Gesture = entries.random()
    }
}
