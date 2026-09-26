package com.ancientpersia.rps.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.ancientpersia.rps.MainActivity
import com.ancientpersia.rps.R
import com.ancientpersia.rps.ads.AdReward
import com.ancientpersia.rps.audio.SoundManager
import com.ancientpersia.rps.billing.MyketIap
import com.ancientpersia.rps.data.BackgroundItem
import com.ancientpersia.rps.data.BackgroundRepo
import com.ancientpersia.rps.data.GamePrefs
import com.ancientpersia.rps.data.Skin
import com.ancientpersia.rps.data.SkinRepo
import com.ancientpersia.rps.databinding.FragmentShopBinding
import com.ancientpersia.rps.game.Gesture
import com.ancientpersia.rps.util.PersianUtil
import com.ancientpersia.rps.util.Res

/**
 * فروشگاه: دست‌ها، پس‌زمینه‌ها و بازار سیاه (خرید درون‌برنامه‌ای)
 */
class ShopFragment : Fragment() {

    private var _b: FragmentShopBinding? = null
    private val b get() = _b!!

    private var selectedSkin: Skin? = null
    private var selectedBg: BackgroundItem? = null
    private var cycleIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private val floatAnims = mutableListOf<ObjectAnimator>()

    /** قیمت هر بسته بازار سیاه (به‌روزرسانی از پنل مایکت بعد از queryInventory) */
    private val marketPriceViews = mutableMapOf<String, TextView>()

    // ---------- lifecycle ----------

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentShopBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = activity as MainActivity
        val ctx = requireContext()

        b.bgImage.setImageResource(
            Res.drawable(ctx, BackgroundRepo.drawableName(GamePrefs.equippedBackground))
        )
        b.btnBack.setOnClickListener {
            SoundManager.play("click")
            act.showMenu()
        }

        selectedSkin = SkinRepo.byId(GamePrefs.equippedSkin)
        selectedBg = BackgroundRepo.byId(GamePrefs.equippedBackground)

        b.tabHands.setOnClickListener { SoundManager.play("click"); selectTab(0) }
        b.tabBackgrounds.setOnClickListener { SoundManager.play("click"); selectTab(1) }
        b.tabMarket.setOnClickListener { SoundManager.play("click"); selectTab(2) }

        b.btnWear.setOnClickListener { onWearSkin() }
        b.btnWearBg.setOnClickListener { onWearBg() }

        // کلاکت جایزه: تماشای تبلیغ ادیوری تا انتها = ۱۵ سکه
        val appCtx = requireContext().applicationContext
        b.btnReward.setOnClickListener {
            SoundManager.play("click")
            Toast.makeText(appCtx, R.string.ad_loading, Toast.LENGTH_SHORT).show()
            AdReward.showRewarded(requireActivity(),
                onRewarded = {
                    GamePrefs.coins += AdReward.REWARD_COINS
                    refreshShopCoins()
                    SoundManager.play("coin")
                    Toast.makeText(appCtx,
                        appCtx.getString(R.string.ad_rewarded, PersianUtil.toFa(AdReward.REWARD_COINS)),
                        Toast.LENGTH_LONG).show()
                },
                onUnavailable = {
                    Toast.makeText(appCtx, R.string.ad_failed, Toast.LENGTH_SHORT).show()
                })
        }

        selectTab(0)
        startFloats()
        buildMarket()
        refreshShopCoins()

        handler.postDelayed(cycleStep, 1800L)

        // اتصال به مایکت: تحویل خریدهای نیمه‌کاره + قیمت‌های به‌روز پنل
        val appCtx0 = requireContext().applicationContext
        MyketIap.setup(appCtx0) { ok ->
            if (!isAdded) return@setup
            if (!ok) return@setup
            MyketIap.queryInventory { _, recovered ->
                if (!isAdded || _b == null) return@queryInventory
                refreshMarketPrices()
                refreshShopCoins()
                if (recovered) {
                    SoundManager.play("coin")
                    Toast.makeText(appCtx0, R.string.iap_delivered_late, Toast.LENGTH_LONG).show()
                    refreshSkins()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_b != null) {
            b.bgImage.setImageResource(
                Res.drawable(requireContext(), BackgroundRepo.drawableName(GamePrefs.equippedBackground))
            )
            refreshShopCoins()
        }
        // پیش‌بارگذاری تبلیغ جایزه‌ای ادیوری
        AdReward.prewarm(requireActivity())
    }

    override fun onPause() {
        handler.removeCallbacks(cycleStep)
        stopFloats()
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(cycleStep)
        stopFloats()
        MyketIap.release()
        _b = null
        super.onDestroyView()
    }

    // ---------- tabs ----------

    private fun selectTab(index: Int) {
        b.handsTab.visibility = if (index == 0) View.VISIBLE else View.GONE
        b.bgsTab.visibility = if (index == 1) View.VISIBLE else View.GONE
        b.marketTab.visibility = if (index == 2) View.VISIBLE else View.GONE
        styleTab(b.tabHands, index == 0)
        styleTab(b.tabBackgrounds, index == 1)
        styleTab(b.tabMarket, index == 2)
        if (index == 0) {
            refreshSkins()
            updateSkinPreview()
        } else if (index == 1) {
            refreshBgs()
            updateBgPreview()
        }
    }

    private fun styleTab(t: Button, selected: Boolean) {
        val ctx = requireContext()
        t.setBackgroundResource(if (selected) R.drawable.btn_gold else R.drawable.btn_lapis)
        t.setTextColor(
            ContextCompat.getColor(ctx, if (selected) R.color.lapis_deep else R.color.gold_pale)
        )
    }

    // ---------- hands tab ----------

    private fun refreshSkins() {
        b.skinsRow.removeAllViews()
        SkinRepo.all.forEach { b.skinsRow.addView(skinCard(it)) }
    }

    private fun skinCard(skin: Skin): View {
        val ctx = requireContext()
        val selected = selectedSkin?.id == skin.id

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(122), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(10)
            }
            isClickable = true
            isFocusable = true
        }

        val iv = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(100))
            setImageResource(Res.drawable(ctx, SkinRepo.drawableName(skin.id, Gesture.FIST)))
        }
        card.addView(iv)

        val nameColor = if (selected) R.color.lapis_deep else R.color.text_primary
        card.addView(TextView(ctx).apply {
            text = skin.name
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, nameColor))
        })

        if (skin.imperial) {
            card.addView(TextView(ctx).apply {
                text = getString(R.string.in_black_market)
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, if (selected) R.color.lapis_deep else R.color.gold))
            })
        } else {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(17), dp(17))
                setImageResource(R.drawable.coin)
            })
            row.addView(TextView(ctx).apply {
                text = " ${PersianUtil.toFa(skin.price)}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, nameColor))
            })
            card.addView(row)
        }

        card.setBackgroundResource(
            if (selected) R.drawable.card_skin_selected else R.drawable.card_skin_normal
        )
        card.setOnClickListener {
            SoundManager.play("click")
            selectedSkin = skin
            refreshSkins()
            updateSkinPreview()
        }
        return card
    }

    private fun updateSkinPreview() {
        val ctx = requireContext()
        val skin = selectedSkin ?: SkinRepo.byId(GamePrefs.equippedSkin)
        val owned = GamePrefs.ownsSkin(skin.id)
        val equipped = GamePrefs.equippedSkin == skin.id

        val g = Gesture.entries[cycleIndex % 3]
        b.previewHand.setImageResource(Res.drawable(ctx, SkinRepo.drawableName(skin.id, g)))

        b.tvTreasure.text = getString(if (owned) R.string.in_treasure else R.string.not_in_treasure)
        b.tvTreasure.setTextColor(
            ContextCompat.getColor(ctx, if (owned) R.color.gold_pale else R.color.win_red)
        )

        b.btnWear.text = getString(if (equipped) R.string.wearing else R.string.wear_it)
        b.btnWear.isEnabled = !equipped
        b.btnWear.alpha = if (equipped) 0.6f else 1f
        b.btnWear.setBackgroundResource(if (equipped) R.drawable.btn_lapis else R.drawable.btn_gold)
    }

    private fun onWearSkin() {
        val ctx = requireContext()
        val skin = selectedSkin ?: return
        val owned = GamePrefs.ownsSkin(skin.id)

        if (!owned) {
            if (skin.imperial) {
                // دست شاهنشاهی فقط از بسته شاهنشاهی در بازار سیاه باز می‌شود
                selectTab(2)
                return
            }
            if (GamePrefs.coins < skin.price) {
                SoundManager.play("drum")
                Toast.makeText(ctx, R.string.not_enough_coins, Toast.LENGTH_SHORT).show()
                return
            }
            GamePrefs.coins = GamePrefs.coins - skin.price
            GamePrefs.addOwnedSkin(skin.id)
            SoundManager.play("buy")
            Toast.makeText(ctx, R.string.bought_worn, Toast.LENGTH_SHORT).show()
        } else {
            SoundManager.play("click")
        }
        GamePrefs.equippedSkin = skin.id
        refreshSkins()
        updateSkinPreview()
        refreshShopCoins()
    }

    /** چرخش دوره‌ای حالت‌های دست در پیش‌نمایش */
    private val cycleStep = object : Runnable {
        override fun run() {
            if (!isAdded || _b == null) return
            if (b.handsTab.visibility == View.VISIBLE && selectedSkin != null) {
                cycleIndex++
                val skin = selectedSkin!!
                val g = Gesture.entries[cycleIndex % 3]
                b.previewHand.setImageResource(
                    Res.drawable(requireContext(), SkinRepo.drawableName(skin.id, g))
                )
            }
            handler.postDelayed(this, 1800L)
        }
    }

    // ---------- backgrounds tab ----------

    private fun refreshBgs() {
        b.bgsRow.removeAllViews()
        BackgroundRepo.all.forEach { b.bgsRow.addView(bgCard(it)) }
    }

    private fun bgCard(item: BackgroundItem): View {
        val ctx = requireContext()
        val selected = selectedBg?.id == item.id
        val owned = GamePrefs.ownsBackground(item.id)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(128), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(10)
            }
            isClickable = true
            isFocusable = true
        }

        card.addView(ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(180))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(Res.drawable(ctx, BackgroundRepo.thumbName(item.id)))
        })

        val nameColor = if (selected) R.color.lapis_deep else R.color.text_primary
        card.addView(TextView(ctx).apply {
            text = item.name
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, nameColor))
        })

        if (item.price <= 0) {
            card.addView(TextView(ctx).apply {
                text = "پیش‌فرض"
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, nameColor))
            })
        } else {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(17), dp(17))
                setImageResource(R.drawable.coin)
            })
            row.addView(TextView(ctx).apply {
                text = " ${PersianUtil.toFa(item.price)}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, nameColor))
            })
            card.addView(row)
        }

        card.setBackgroundResource(
            if (selected) R.drawable.card_skin_selected else R.drawable.card_skin_normal
        )
        card.setOnClickListener {
            SoundManager.play("click")
            selectedBg = item
            refreshBgs()
            updateBgPreview()
        }
        return card
    }

    private fun updateBgPreview() {
        val ctx = requireContext()
        val item = selectedBg ?: BackgroundRepo.byId(GamePrefs.equippedBackground)
        val owned = GamePrefs.ownsBackground(item.id)
        val equipped = GamePrefs.equippedBackground == item.id

        b.previewBg.setImageResource(Res.drawable(ctx, BackgroundRepo.drawableName(item.id)))

        b.tvTreasureBg.text = getString(if (owned) R.string.in_treasure else R.string.not_in_treasure)
        b.tvTreasureBg.setTextColor(
            ContextCompat.getColor(ctx, if (owned) R.color.gold_pale else R.color.win_red)
        )

        b.btnWearBg.text = getString(if (equipped) R.string.wearing else R.string.wear_it)
        b.btnWearBg.isEnabled = !equipped
        b.btnWearBg.alpha = if (equipped) 0.6f else 1f
        b.btnWearBg.setBackgroundResource(if (equipped) R.drawable.btn_lapis else R.drawable.btn_gold)
    }

    private fun onWearBg() {
        val ctx = requireContext()
        val item = selectedBg ?: return
        val owned = GamePrefs.ownsBackground(item.id)

        if (!owned) {
            if (GamePrefs.coins < item.price) {
                SoundManager.play("drum")
                Toast.makeText(ctx, R.string.not_enough_coins, Toast.LENGTH_SHORT).show()
                return
            }
            GamePrefs.coins = GamePrefs.coins - item.price
            GamePrefs.addOwnedBackground(item.id)
            SoundManager.play("buy")
            Toast.makeText(ctx, R.string.bought_worn, Toast.LENGTH_SHORT).show()
        } else {
            SoundManager.play("click")
        }
        GamePrefs.equippedBackground = item.id
        refreshBgs()
        updateBgPreview()
        refreshShopCoins()
        // اعمال فوری در پس‌زمینه همه صفحات
        b.bgImage.setImageResource(Res.drawable(ctx, BackgroundRepo.drawableName(item.id)))
    }

    // ---------- black market tab ----------

    private fun refreshShopCoins() {
        if (_b == null) return
        b.tvShopCoins.text = PersianUtil.toFa(GamePrefs.coins)
    }

    private fun buildMarket() {
        b.marketCol.removeAllViews()
        marketPriceViews.clear()
        b.marketCol.addView(imperialPack())
        val packs = listOf(
            MarketPack("کیسه ۶۰۰ سکه", "۵٫۰۰۰ تومان", "برای شروع گنجور تو", MyketIap.SKU_BAG),
            MarketPack("صندوق ۱٫۵۰۰ سکه", "۱۰٫۰۰۰ تومان", "پیشنهاد ویژه پهلوانان", MyketIap.SKU_CHEST),
            MarketPack("گنج ۲٫۲۰۰ سکه", "۱۵٫۰۰۰ تومان", "گنجینه شاهان", MyketIap.SKU_TREASURE),
        )
        packs.forEach { b.marketCol.addView(coinPack(it)) }
    }

    private data class MarketPack(
        val title: String, val price: String, val desc: String, val sku: String
    )

    private fun imperialPack(): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
            background = ContextCompat.getDrawable(ctx, R.drawable.card_gold_pack)
        }

        card.addView(ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(136))
            setImageResource(Res.drawable(ctx, SkinRepo.drawableName("imperial", Gesture.FIST)))
        })
        card.addView(TextView(ctx).apply {
            text = getString(R.string.imperial_pack)
            textSize = 25f
            setTextColor(ContextCompat.getColor(ctx, R.color.lapis_deep))
        })
        card.addView(TextView(ctx).apply {
            text = getString(R.string.imperial_desc)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, R.color.lapis))
        })
        card.addView(TextView(ctx).apply {
            text = "۲۵٫۰۰۰ تومان"
            textSize = 19f
            setTextColor(ContextCompat.getColor(ctx, R.color.lapis))
        }.also { marketPriceViews[MyketIap.SKU_SHAH] = it })
        card.addView(Button(ctx).apply {
            text = getString(R.string.buy_now)
            textSize = 18f
            background = ContextCompat.getDrawable(ctx, R.drawable.btn_lapis)
            setTextColor(ContextCompat.getColor(ctx, R.color.gold_pale))
            setOnClickListener { startPurchase(MyketIap.SKU_SHAH) }
        }.also { btn ->
            btn.layoutParams = LinearLayout.LayoutParams(dp(200), dp(52)).apply {
                topMargin = dp(10)
            }
        })
        return card
    }

    private fun coinPack(pack: MarketPack): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
            background = ContextCompat.getDrawable(ctx, R.drawable.panel_lapis)
        }

        card.addView(ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
            setImageResource(R.drawable.coin)
        })
        card.addView(TextView(ctx).apply {
            text = pack.title
            textSize = 21f
            setTextColor(ContextCompat.getColor(ctx, R.color.gold))
        })
        card.addView(TextView(ctx).apply {
            text = pack.desc
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
        })
        card.addView(TextView(ctx).apply {
            text = pack.price
            textSize = 16f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        }.also { marketPriceViews[pack.sku] = it })
        card.addView(Button(ctx).apply {
            text = getString(R.string.buy_now)
            textSize = 17f
            background = ContextCompat.getDrawable(ctx, R.drawable.btn_gold)
            setTextColor(ContextCompat.getColor(ctx, R.color.lapis_deep))
            setOnClickListener { startPurchase(pack.sku) }
        }.also { btn ->
            btn.layoutParams = LinearLayout.LayoutParams(dp(200), dp(50)).apply {
                topMargin = dp(8)
            }
        })
        return card
    }

    // ---------- خرید درون‌برنامه‌ای مایکت ----------

    /** به‌روزرسانی قیمت بسته‌ها از پنل مایکت (اگر queryInventory موفق باشد) */
    private fun refreshMarketPrices() {
        if (_b == null) return
        marketPriceViews.forEach { (sku, tv) ->
            MyketIap.priceOf(sku)?.let { tv.text = it }
        }
    }

    /** کلیک روی «خرید» هر بسته: اتصال به مایکت و آغاز جریان پرداخت */
    private fun startPurchase(sku: String) {
        val appCtx = requireContext().applicationContext
        SoundManager.play("click")
        Toast.makeText(appCtx, R.string.iap_connecting, Toast.LENGTH_SHORT).show()

        MyketIap.setup(appCtx) { ok ->
            if (!isAdded) return@setup
            if (!ok) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.tab_market)
                    .setMessage(R.string.iap_myket_missing)
                    .setPositiveButton("باشه", null)
                    .show()
                return@setup
            }
            MyketIap.purchase(requireActivity(), sku, object : MyketIap.Callback {
                override fun onDelivered(coins: Int, imperialUnlocked: Boolean) {
                    if (!isAdded || _b == null) return
                    refreshShopCoins()
                    SoundManager.play("coin")
                    val msg = when {
                        imperialUnlocked -> getString(R.string.iap_success_shah)
                        coins > 0 -> getString(R.string.iap_success_coins, PersianUtil.toFa(coins))
                        else -> getString(R.string.iap_delivered_late)
                    }
                    Toast.makeText(appCtx, msg, Toast.LENGTH_LONG).show()
                    if (imperialUnlocked) {
                        selectedSkin = SkinRepo.byId("imperial")
                        refreshSkins()
                    }
                }

                override fun onCanceled() {
                    if (!isAdded) return
                    Toast.makeText(appCtx, R.string.iap_canceled, Toast.LENGTH_SHORT).show()
                }

                override fun onError(message: String?) {
                    if (!isAdded) return
                    Toast.makeText(
                        appCtx,
                        getString(R.string.iap_failed, message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }
    }

    // ---------- floating animations ----------

    private fun startFloats() {
        stopFloats()
        b.triangleRow.children.forEachIndexed { i, v ->
            floatAnims += ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -10f, 0f).apply {
                duration = 850L + i * 130L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        }
        // مثلث‌های ردیف پایین: نوک رو به بالا، همان موج‌خوردن ردیف بالا
        b.triangleRowBottom.children.forEachIndexed { i, v ->
            floatAnims += ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -10f, 0f).apply {
                duration = 850L + (4 - i) * 130L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        }
        floatAnims += ObjectAnimator.ofFloat(b.ivDown, View.TRANSLATION_Y, 0f, 9f, 0f).apply {
            duration = 700L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        floatAnims += ObjectAnimator.ofFloat(b.btnReward, View.TRANSLATION_Y, 0f, -7f, 0f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopFloats() {
        floatAnims.forEach { it.cancel() }
        floatAnims.clear()
    }

    // ---------- helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
