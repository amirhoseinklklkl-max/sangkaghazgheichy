package com.ancientpersia.rps.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ancientpersia.rps.BuildConfig
import com.ancientpersia.rps.data.GamePrefs
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.Purchase

/**
 * اتصال به پرداخت درون‌برنامه‌ای مایکت (روش رسمی مستندات myket.ir/kb/pages/java)
 * با کتابخانه com.github.myketstore:myket-billing-client
 *
 * جریان: setup → queryInventory (تحویل خریدهای نیمه‌کاره + قیمت‌های پنل)
 *        → launchPurchaseFlow → تحویل جایزه → consumeAsync
 *
 * همه محصولات این بازی «مصرف‌شدنی» هستند (سکه / بسته شاهنشاهی) و بعد از تحویل مصرف می‌شوند
 * تا کاربر بتواند دوباره خرید کند. توکن هر خرید در GamePrefs ثبت می‌شود تا اگر خریدی
 * بین تحویل و مصرف قطع شد، در اجرای بعدی فقط یک‌بار تحویل داده شود (بدون دوباره‌پرداختی).
 */
object MyketIap {

    private const val TAG = "MyketIap"

    // شناسه محصولات — باید دقیقاً با پنل توسعه‌دهندگان مایکت یکی باشد
    const val SKU_BAG = "Coin600"       // بسته کیسه: ۶۰۰ سکه
    const val SKU_CHEST = "coin1500"    // بسته صندوق: ۱٫۵۰۰ سکه
    const val SKU_TREASURE = "coin2200" // بسته گنج: ۲٫۲۰۰ سکه
    const val SKU_SHAH = "shah"         // بسته شاهنشاهی: ۵٫۰۰۰ سکه + دست شاهنشاهی

    val ALL_SKUS = listOf(SKU_BAG, SKU_CHEST, SKU_TREASURE, SKU_SHAH)

    /** سکه‌ای که هر بسته به کاربر می‌دهد */
    fun coinsFor(sku: String): Int = when (sku) {
        SKU_BAG -> 600
        SKU_CHEST -> 1500
        SKU_TREASURE -> 2200
        SKU_SHAH -> 5000
        else -> 0
    }

    interface Callback {
        /** خرید موفق و تحویل شد؛ coins = سکه اضافه‌شده، imperialUnlocked = دست شاهنشاهی تازه باز شد */
        fun onDelivered(coins: Int, imperialUnlocked: Boolean)
        fun onCanceled()
        fun onError(message: String?)
    }

    private var helper: IabHelper? = null
    private var ready = false
    private val setupWaiters = mutableListOf<(Boolean) -> Unit>()
    private val priceCache = mutableMapOf<String, String>()

    val isReady: Boolean get() = ready

    /** قیمت محصول از پنل مایکت (مثل «۵٫۰۰۰ تومان»)؛ قبل از queryInventory خالی است */
    fun priceOf(sku: String): String? = priceCache[sku]

    // ---------- setup ----------

    /**
     * اتصال به سرویس مایکت (idempotent). اگر مایکت نصب نباشد یا سرویس در دسترس نباشد
     * onReady(false) صدا زده می‌شود.
     */
    fun setup(appContext: Context, onReady: (Boolean) -> Unit) {
        if (ready) {
            onReady(true)
            return
        }
        setupWaiters.add(onReady)
        if (helper != null) return // اتصال در جریان است

        val h = IabHelper(appContext.applicationContext, BuildConfig.IAB_PUBLIC_KEY)
        helper = h
        h.enableDebugLogging(BuildConfig.DEBUG)
        h.startSetup(IabHelper.OnIabSetupFinishedListener { result ->
            // اگر در همین فاصله release() شده باشد به اتصال مرده وابسته نمی‌مانیم
            if (helper !== h) return@OnIabSetupFinishedListener
            if (result == null || !result.isSuccess) {
                Log.w(TAG, "Myket setup failed: ${result?.message}")
                try { h.dispose() } catch (_: Exception) {}
                helper = null
                notifyWaiters(false)
            } else {
                ready = true
                notifyWaiters(true)
            }
        })
    }

    private fun notifyWaiters(ok: Boolean) {
        val waiters = setupWaiters.toList()
        setupWaiters.clear()
        waiters.forEach { runCatching { it(ok) } }
    }

    // ---------- inventory ----------

    /**
     * به‌روزرسانی خریدها (بسیار مهم طبق مستندات): هر خرید مصرف‌نشده‌ای که تحویل نرفته باشد
     * همین‌جا تحویل و مصرف می‌شود؛ قیمت‌های پنل هم ذخیره می‌شوند.
     * recovered = آیا خریدی تحویل داده شد که قبلاً پرداخت شده بود؟
     */
    fun queryInventory(onDone: (ok: Boolean, recovered: Boolean) -> Unit) {
        val h = helper
        if (!ready || h == null) {
            onDone(false, false)
            return
        }
        try {
            h.queryInventoryAsync(true, ALL_SKUS,
                IabHelper.QueryInventoryFinishedListener { result, inv ->
                    if (helper !== h) return@QueryInventoryFinishedListener
                    if (result == null || result.isFailure || inv == null) {
                        Log.w(TAG, "queryInventory failed: ${result?.message}")
                        onDone(false, false)
                        return@QueryInventoryFinishedListener
                    }
                    var recovered = false
                    ALL_SKUS.forEach { sku ->
                        runCatching { inv.getSkuDetails(sku)?.let { priceCache[sku] = it.price } }
                        val p = inv.getPurchase(sku)
                        if (p != null) {
                            // خرید نیمه‌کاره: تحویل (فقط بار اول) و سپس مصرف
                            if (grant(p) > 0) recovered = true
                            consume(p)
                        }
                    }
                    onDone(true, recovered)
                })
        } catch (e: Exception) {
            Log.w(TAG, "queryInventory busy: ${e.message}")
            onDone(false, false)
        }
    }

    // ---------- purchase ----------

    /** شروع فرآیند خرید یک بسته؛ نتیجه از طریق Callback برمی‌گردد */
    fun purchase(activity: Activity, sku: String, cb: Callback) {
        val h = helper
        if (!ready || h == null) {
            setup(activity.applicationContext) { ok ->
                if (ok) purchase(activity, sku, cb) else cb.onError(null)
            }
            return
        }
        try {
            h.launchPurchaseFlow(activity, sku,
                IabHelper.OnIabPurchaseFinishedListener { result, purchase ->
                    if (result == null || result.isFailure || purchase == null) {
                        val response = result?.response ?: -1
                        if (response == IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED ||
                            response == IabHelper.IABHELPER_USER_CANCELLED) {
                            cb.onCanceled()
                        } else {
                            cb.onError(result?.message)
                        }
                        return@OnIabPurchaseFinishedListener
                    }
                    if (purchase.sku != sku) {
                        cb.onError("unexpected sku: ${purchase.sku}")
                        return@OnIabPurchaseFinishedListener
                    }
                    val coins = grant(purchase)
                    consume(purchase)
                    cb.onDelivered(coins, sku == SKU_SHAH)
                },
                "skg|$sku")
        } catch (e: Exception) {
            Log.w(TAG, "purchase flow busy: ${e.message}")
            cb.onError(e.message)
        }
    }

    // ---------- delivery & consume ----------

    /**
     * تحویل جایزه یک خرید؛ فقط اگر توکن آن قبلاً تحویل نشده باشد.
     * @return سکه‌های اضافه‌شده (۰ یعنی قبلاً تحویل شده بود)
     */
    private fun grant(purchase: Purchase): Int {
        val sku = purchase.sku
        val token = purchase.token
        if (token.isNullOrEmpty()) return 0
        if (GamePrefs.hasGrantedToken(token)) return 0

        val coins = coinsFor(sku)
        if (coins <= 0) return 0
        GamePrefs.addGrantedToken(token)
        GamePrefs.coins = GamePrefs.coins + coins
        if (sku == SKU_SHAH) {
            grantImperialIfNeeded()
        }
        Log.i(TAG, "Delivered $coins coins for $sku (token=${token.take(8)}…)")
        return coins
    }

    /** باز کردن دست شاهنشاهی اگر باز نبود؛ @return آیا تازه باز شد */
    private fun grantImperialIfNeeded(): Boolean {
        if (GamePrefs.ownsSkin("imperial")) return false
        GamePrefs.addOwnedSkin("imperial")
        Log.i(TAG, "Imperial skin unlocked")
        return true
    }

    /** مصرف خرید تا کاربر بتواند دوباره همان بسته را بخرد */
    private fun consume(purchase: Purchase) {
        val h = helper ?: return
        try {
            h.consumeAsync(purchase, IabHelper.OnConsumeFinishedListener { p, result ->
                if (result == null || result.isFailure) {
                    // اگر مصرف اینجا نشد، در queryInventory بعدی دوباره تلاش می‌شود
                    Log.w(TAG, "consume failed: ${result?.message}")
                } else {
                    Log.i(TAG, "Consumed: ${p?.sku}")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "consume busy: ${e.message}")
        }
    }

    // ---------- release ----------

    /** قطع اتصال سرویس مایکت (هنگام خروج از فروشگاه) */
    fun release() {
        notifyWaiters(false)
        helper?.let { h ->
            try { h.dispose() } catch (_: Exception) {}
        }
        helper = null
        ready = false
    }
}
