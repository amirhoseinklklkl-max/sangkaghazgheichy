package com.ancientpersia.rps.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ancientpersia.rps.MainActivity
import com.ancientpersia.rps.R
import com.ancientpersia.rps.audio.SoundManager
import com.ancientpersia.rps.data.BackgroundRepo
import com.ancientpersia.rps.data.GamePrefs
import com.ancientpersia.rps.data.Opponents
import com.ancientpersia.rps.data.SkinRepo
import com.ancientpersia.rps.databinding.FragmentGameBinding
import com.ancientpersia.rps.game.Gesture
import com.ancientpersia.rps.game.RpsGame.Outcome
import com.ancientpersia.rps.game.RpsGame
import com.ancientpersia.rps.util.PersianUtil
import com.ancientpersia.rps.util.Res

/**
 * صفحه نبرد: ۵ راند، لوزی‌های رنگی، حساب‌وکتاب مخفیانه سکه‌ها
 */
class GameFragment : Fragment() {

    companion object {
        private const val PENDING = 0xFF56619B.toInt()
        private const val WIN_COLOR = 0xFFF5C542.toInt()
        private const val LOSE_COLOR = 0xFFC0392B.toInt()
        private const val DRAW_COLOR = 0xFF2E86C1.toInt()
        private const val SHUFFLE_TICKS = 6L
        private const val SHUFFLE_MS = 90L
        private const val REVEAL_MS = 620L
        private const val NEXT_ROUND_MS = 1750L
    }

    private var _b: FragmentGameBinding? = null
    private val b get() = _b!!

    private lateinit var game: RpsGame
    private val handler = Handler(Looper.getMainLooper())
    private val runnables = mutableListOf<Runnable>()
    private var canPick = false
    private val diamonds = mutableListOf<View>()
    private var floatAi: ObjectAnimator? = null
    private var floatPlayer: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentGameBinding.inflate(inflater, container, false)
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
            confirmQuit(act)
        }

        diamonds.addAll(listOf(b.diamond1, b.diamond2, b.diamond3, b.diamond4, b.diamond5))
        resetDiamonds()

        b.aiHand.scaleY = -1f  // دست حریف رو به پایین

        b.btnRock.setOnClickListener { pick(Gesture.FIST) }
        b.btnPaper.setOnClickListener { pick(Gesture.PALM) }
        b.btnScissors.setOnClickListener { pick(Gesture.SCISSORS) }

        b.btnAgain.setOnClickListener {
            SoundManager.play("click")
            resetGame()
        }
        b.btnFinalMenu.setOnClickListener {
            SoundManager.play("click")
            act.showMenu()
        }

        startFloating()
        newGame()
    }

    // ---------- game flow ----------

    private fun newGame() {
        game = Opponents.newGame()
        b.tvOpponent.text = game.opponentName
        b.aiHand.setImageResource(handRes(game.opponentSkin, Gesture.FIST))
        b.playerHand.setImageResource(handRes(GamePrefs.equippedSkin, Gesture.FIST))
        b.finalOverlay.visibility = View.GONE
        resetDiamonds()
        updateRoundLabel()
        canPick = true
    }

    private fun resetGame() = newGame()

    private fun updateRoundLabel() {
        b.tvRound.text = getString(
            R.string.round_of,
            PersianUtil.toFa(game.playedRounds + 1),
            PersianUtil.toFa(RpsGame.TOTAL_ROUNDS)
        )
    }

    private fun pick(g: Gesture) {
        if (!canPick || game.isFinished) return
        canPick = false
        SoundManager.play("whoosh")

        b.playerHand.setImageResource(handRes(GamePrefs.equippedSkin, g))
        pop(b.playerHand)

        // هر دو دست بزرگ کمی به سمت مرکز می‌آیند
        val step = 42f * resources.displayMetrics.density
        b.aiHand.animate().translationY(step).setDuration(320L).start()
        b.playerHand.animate().translationY(-step).setDuration(320L).start()

        shuffleAi(g)
    }

    private fun shuffleAi(playerChoice: Gesture) {
        var tick = 0
        val shuffleStep = object : Runnable {
            override fun run() {
                if (!isAdded) return
                if (tick < SHUFFLE_TICKS) {
                    b.aiHand.setImageResource(
                        handRes(game.opponentSkin, Gesture.entries[(tick % 3).toInt()])
                    )
                    tick++
                    post(this, SHUFFLE_MS)
                } else {
                    reveal(playerChoice)
                }
            }
        }
        shuffleStep.run()
    }

    private fun reveal(playerChoice: Gesture) {
        val rr = game.play(playerChoice)

        b.aiHand.setImageResource(handRes(game.opponentSkin, rr.ai))
        pop(b.aiHand)

        // بازگشت دست‌ها به عقب
        b.aiHand.animate().translationY(0f).setDuration(300L).setStartDelay(220L).start()
        b.playerHand.animate().translationY(0f).setDuration(300L).setStartDelay(220L).start()

        post({
            if (!isAdded) return@post
            when (rr.outcome) {
                Outcome.WIN -> {
                    flash(getString(R.string.round_win), WIN_COLOR)
                    SoundManager.play("win")
                }
                Outcome.LOSE -> {
                    flash(getString(R.string.round_lose), LOSE_COLOR)
                    SoundManager.play("lose")
                }
                Outcome.DRAW -> {
                    flash(getString(R.string.round_draw), DRAW_COLOR)
                    SoundManager.play("draw")
                }
            }
            val idx = game.playedRounds - 1
            if (idx in diamonds.indices) {
                val color = when (rr.outcome) {
                    Outcome.WIN -> WIN_COLOR
                    Outcome.LOSE -> LOSE_COLOR
                    Outcome.DRAW -> DRAW_COLOR
                }
                setDiamond(diamonds[idx], color)
            }
        }, REVEAL_MS)

        post({
            if (!isAdded) return@post
            b.tvFlash.visibility = View.GONE
            if (game.isFinished) {
                showFinal()
            } else {
                updateRoundLabel()
                canPick = true
            }
        }, NEXT_ROUND_MS)
    }

    private fun showFinal() {
        val ctx = requireContext()
        val net = game.netCoins()

        // تسویه مخفیانه؛ فقط نتیجه نهایی نمایش داده می‌شود
        GamePrefs.coins = GamePrefs.coins + net

        // آمار منو بر اساس «نتیجه کل نبرد» ثبت می‌شود، نه هر راند:
        //   بردِ راندها > باختِ راندها  → یک برد به منو اضافه می‌شود
        //   باختِ راندها > بردِ راندها  → یک باخت به منو اضافه می‌شود
        //   مساوی بودن                  → هیچ چیزی اضافه نمی‌شود (مساوی)
        if (game.roundsWon() > game.roundsLost()) {
            GamePrefs.wins = GamePrefs.wins + 1
        } else if (game.roundsWon() < game.roundsLost()) {
            GamePrefs.losses = GamePrefs.losses + 1
        }

        b.tvFinalTitle.text = when {
            game.roundsWon() > game.roundsLost() -> getString(R.string.final_win)
            game.roundsWon() < game.roundsLost() -> getString(R.string.final_lose)
            else -> getString(R.string.final_draw)
        }
        b.tvFinalStats.text =
            "${PersianUtil.toFa(game.roundsWon())} برد، " +
                    "${PersianUtil.toFa(game.roundsLost())} باخت، " +
                    "${PersianUtil.toFa(game.roundsDraw())} مساوی"
        when {
            net > 0 -> {
                b.tvFinalCoins.text = "+${PersianUtil.toFa(net)} سکه"
                b.tvFinalCoins.setTextColor(ContextCompat.getColor(ctx, R.color.gold))
                SoundManager.play("coin")
            }
            net < 0 -> {
                b.tvFinalCoins.text = "-${PersianUtil.toFa(-net)} سکه"
                b.tvFinalCoins.setTextColor(ContextCompat.getColor(ctx, R.color.win_red))
                SoundManager.play("lose")
            }
            else -> {
                b.tvFinalCoins.text = "بدون تغییر سکه"
                b.tvFinalCoins.setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
            }
        }

        b.finalOverlay.alpha = 0f
        b.finalOverlay.visibility = View.VISIBLE
        b.finalOverlay.animate().alpha(1f).setDuration(320L).start()
        b.finalOverlay.scaleX = 0.92f
        b.finalOverlay.scaleY = 0.92f
        b.finalOverlay.animate().scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator()).setDuration(320L).start()
    }

    // ---------- ui helpers ----------

    private fun handRes(skinId: String, g: Gesture): Int =
        Res.drawable(requireContext(), SkinRepo.drawableName(skinId, g))

    private fun pop(v: View) {
        // دست حریف scaleY منفی دارد (نوک انگشت‌ها رو به پایین)؛
        // علامت.scaleY باید در پاپ حفظ شود وگرنه دست برمی‌گردد رو به بالا
        val sign = if (v === b.aiHand) -1f else 1f
        v.scaleX = 0.72f
        v.scaleY = 0.72f * sign
        v.animate().scaleX(1f).scaleY(1f * sign)
            .setDuration(260L)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun flash(text: String, color: Int) {
        b.tvFlash.text = text
        b.tvFlash.setTextColor(color)
        b.tvFlash.visibility = View.VISIBLE
        pop(b.tvFlash)
    }

    private fun resetDiamonds() {
        diamonds.forEach { setDiamond(it, PENDING) }
    }

    private fun setDiamond(v: View, color: Int) {
        v.backgroundTintList = ColorStateList.valueOf(color)
        v.scaleX = 1.45f
        v.scaleY = 1.45f
        v.animate().scaleX(1f).scaleY(1f).setDuration(240L)
            .setInterpolator(OvershootInterpolator()).start()
    }

    private fun startFloating() {
        floatAi?.cancel()
        floatPlayer?.cancel()
        floatAi = float(b.aiHandFloat)
        floatPlayer = float(b.playerHandFloat)
    }

    private fun float(v: View): ObjectAnimator =
        ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -15f, 0f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

    private fun confirmQuit(act: MainActivity) {
        if (game.playedRounds == 0 || game.isFinished) {
            act.showMenu()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.quit_battle_title)
            .setMessage(R.string.quit_battle_msg)
            .setPositiveButton(R.string.leave_battle) { _, _ -> act.showMenu() }
            .setNegativeButton(R.string.stay_battle, null)
            .show()
    }

    private fun post(r: Runnable, delayMs: Long) {
        runnables.add(r)
        handler.postDelayed(r, delayMs)
    }

    private fun clearPosts() {
        runnables.forEach { handler.removeCallbacks(it) }
        runnables.clear()
    }

    override fun onResume() {
        super.onResume()
        if (_b != null) {
            b.bgImage.setImageResource(
                Res.drawable(requireContext(), BackgroundRepo.drawableName(GamePrefs.equippedBackground))
            )
            b.playerHand.setImageResource(handRes(GamePrefs.equippedSkin, Gesture.FIST))
            startFloating()
        }
    }

    override fun onPause() {
        canPick = false
        clearPosts()
        floatAi?.cancel()
        floatPlayer?.cancel()
        super.onPause()
    }

    override fun onDestroyView() {
        clearPosts()
        floatAi?.cancel()
        floatPlayer?.cancel()
        _b = null
        super.onDestroyView()
    }
}
