package com.ancientpersia.rps

import android.app.Application
import com.ancientpersia.rps.ads.AdReward
import com.ancientpersia.rps.audio.SoundManager
import com.ancientpersia.rps.data.GamePrefs

/**
 * Application کلاس بازی: مقداردهی اولیه حافظه، صدا و SDK ادیوری (یک بار در هر پردازش)
 */
class SangApp : Application() {

    override fun onCreate() {
        super.onCreate()
        GamePrefs.init(this)
        SoundManager.init(this)
        AdReward.init(this)
    }
}
