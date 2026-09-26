package com.ancientpersia.rps.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ancientpersia.rps.R

/**
 * مدیریت افکت‌های صوتی با SoundPool (بدون فایل خارجی، همه سنتز شده‌اند)
 */
object SoundManager {

    private var pool: SoundPool? = null
    private val map = HashMap<String, Int>()
    private val ready = HashSet<Int>()
    private var muted = false

    fun init(ctx: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
        pool?.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) ready.add(soundId)
        }
        val res = mapOf(
            "click" to R.raw.sfx_click,
            "coin" to R.raw.sfx_coin,
            "win" to R.raw.sfx_win,
            "lose" to R.raw.sfx_lose,
            "draw" to R.raw.sfx_draw,
            "whoosh" to R.raw.sfx_whoosh,
            "drum" to R.raw.sfx_drum,
            "buy" to R.raw.sfx_buy,
        )
        for ((key, r) in res) {
            map[key] = pool!!.load(ctx.applicationContext, r, 1)
        }
    }

    /** قطع/وصل صدای بازی؛ در حالت قطع هیچ افکتی پخش نمی‌شود */
    fun setMuted(m: Boolean) {
        muted = m
    }

    fun isMuted(): Boolean = muted

    fun play(key: String, volume: Float = 1f) {
        if (muted) return
        val id = map[key] ?: return
        if (id in ready) {
            pool?.play(id, volume, volume, 1, 0, 1f)
        }
    }

    fun release() {
        pool?.release()
        pool = null
        map.clear()
        ready.clear()
    }
}
