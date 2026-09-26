package com.ancientpersia.rps.data

import com.ancientpersia.rps.game.RpsGame

/**
 * حریفان هوش مصنوعی با نام‌های قدیمی و پوست تصادفی
 */
object Opponents {

    val names = listOf(
        "سردار گودرز",
        "پهلوان پنبه",
        "آرش کمانگیر",
        "رستم دستان",
        "سهراب",
        "کاوه آهنگر",
    )

    private val aiSkins = listOf(
        "default", "gol", "iran", "hakhamaneshi", "bronze", "gold", "khatam",
        "nastaliq", "henna", "kaman", "damavand", "mah", "kashi"
    )

    fun randomName(): String = names.random()

    fun randomSkin(): String = aiSkins.random()

    fun newGame(): RpsGame = RpsGame(randomName(), randomSkin())
}
