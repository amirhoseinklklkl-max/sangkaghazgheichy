package com.ancientpersia.rps.game

/**
 * موتور بازی سنگ کاغذ قیچی با ۵ راند و حساب‌وکتاب مخفیانه سکه.
 * تا پایان نبرد، تغییر سکه‌ها به کاربر نشان داده نمی‌شود.
 */
class RpsGame(val opponentName: String, val opponentSkin: String) {

    data class RoundResult(val player: Gesture, val ai: Gesture, val outcome: Outcome)

    enum class Outcome { WIN, LOSE, DRAW }

    companion object {
        const val TOTAL_ROUNDS = 5
        const val WIN_COIN = 10
        const val LOSE_COIN = -5
        const val DRAW_COIN = 0

        fun outcomeOf(player: Gesture, ai: Gesture): Outcome = when {
            player == ai -> Outcome.DRAW
            (player == Gesture.FIST && ai == Gesture.SCISSORS) ||
                    (player == Gesture.SCISSORS && ai == Gesture.PALM) ||
                    (player == Gesture.PALM && ai == Gesture.FIST) -> Outcome.WIN
            else -> Outcome.LOSE
        }
    }

    private val results = mutableListOf<RoundResult>()

    val playedRounds: Int get() = results.size
    val isFinished: Boolean get() = results.size >= TOTAL_ROUNDS

    fun play(player: Gesture): RoundResult {
        // نکته: دست حریف فقط «یک بار» تصادفی می‌شود تا دست نمایش‌داده‌شده
        // با همان دستی که قضاوت می‌شود یکی باشد (رفع باگ مساوی)
        val ai = Gesture.random()
        val rr = RoundResult(player, ai, outcomeOf(player, ai))
        results.add(rr)
        return rr
    }

    fun roundsWon(): Int = results.count { it.outcome == Outcome.WIN }
    fun roundsLost(): Int = results.count { it.outcome == Outcome.LOSE }
    fun roundsDraw(): Int = results.count { it.outcome == Outcome.DRAW }

    /** محاسبه مخفیانه سکه‌ها؛ فقط در پایان نبرد نمایش داده می‌شود */
    fun netCoins(): Int = results.sumOf {
        when (it.outcome) {
            Outcome.WIN -> WIN_COIN
            Outcome.LOSE -> LOSE_COIN
            Outcome.DRAW -> DRAW_COIN
        }
    }

    fun lastResult(): RoundResult? = results.lastOrNull()
}
