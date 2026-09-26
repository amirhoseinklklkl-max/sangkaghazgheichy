package com.ancientpersia.rps.ads

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryListener

/**
 * تبلیغ جایزه‌ای ادیوری (کلاکت فروشگاه).
 * کاربر با کلیک روی کلاکت تبلیغ را می‌بیند؛ اگر تا انتها ببیند (isRewarded)
 * ۱۵ سکه جایزه می‌گیرد.
 * مستندات: https://docs.adivery.com/android
 */
object AdReward {

    /** کلید اپلیکیشن ادیوری */
    const val APP_KEY = "457538e1-9dbc-435b-bbe8-05782986e321"

    /** کلید جایگاه تبلیغ (جایزه‌ای) */
    const val PLACEMENT = "e76be4e1-8f0b-4a6c-a992-9dfcd296562b"

    /** سکه جایزه تماشای کامل تبلیغ */
    const val REWARD_COINS = 15

    private var configured = false
    private var listener: AdiveryListener? = null
    private var busy = false
    private val handler = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null

    /** یک بار در شروع پردازنده؛ از SangApp فراخوانی می‌شود */
    fun init(app: Application) {
        if (configured) return
        configured = true
        Adivery.setLoggingEnabled(false)
        Adivery.configure(app, APP_KEY)
    }

    /** پیش‌بارگذاری تبلیغ هنگام باز شدن فروشگاه تا کلاکت سریع پاسخ دهد */
    fun prewarm(activity: Activity) {
        if (!configured) return
        if (busy) return
        if (!Adivery.isLoaded(PLACEMENT)) {
            try {
                Adivery.prepareRewardedAd(activity, PLACEMENT)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * نمایش تبلیغ جایزه‌ای:
     * اگر تبلیغ آماده باشد همان لحظه نمایش داده می‌شود، وگرنه درخواست می‌شود
     * و پس از لود خودکار پخش می‌گردد. در پایان، فقط در صورت تماشای کامل
     * onRewarded فراخوانی می‌شود.
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onUnavailable: () -> Unit) {
        if (!configured || busy) return
        busy = true
        var shown = false

        // اگر تبلیغ در مدت زمان معقول آماده نشد، پیام عدم دسترسی بده
        timeout = Runnable {
            if (busy) {
                finish()
                if (!shown) onUnavailable()
            }
        }
        handler.postDelayed(timeout!!, 15_000L)

        detach()
        listener = object : AdiveryListener() {

            override fun onRewardedAdLoaded(placementId: String) {
                if (placementId != PLACEMENT || shown) return
                if (Adivery.isLoaded(PLACEMENT)) {
                    shown = true
                    Adivery.showAd(PLACEMENT)
                }
            }

            override fun onRewardedAdShown(placementId: String) {
                if (placementId != PLACEMENT) return
                shown = true
                cancelTimeout()
            }

            override fun onRewardedAdClicked(placementId: String) = Unit

            override fun onRewardedAdClosed(placementId: String, isRewarded: Boolean) {
                if (placementId != PLACEMENT) return
                val rewarded = isRewarded
                finish()
                if (rewarded) onRewarded() else onUnavailable()
            }
        }
        Adivery.addPlacementListener(PLACEMENT, listener)

        if (Adivery.isLoaded(PLACEMENT)) {
            shown = true
            Adivery.showAd(PLACEMENT)
        } else {
            try {
                Adivery.prepareRewardedAd(activity, PLACEMENT)
            } catch (_: Exception) {
                finish()
                onUnavailable()
            }
        }
    }

    private fun cancelTimeout() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
    }

    private fun detach() {
        listener?.let { Adivery.removePlacementListener(PLACEMENT) }
        listener = null
    }

    private fun finish() {
        cancelTimeout()
        detach()
        busy = false
    }
}
