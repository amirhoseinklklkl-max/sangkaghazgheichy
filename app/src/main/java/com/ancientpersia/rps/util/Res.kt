package com.ancientpersia.rps.util

import android.content.Context

/**
 * دسترسی به drawable ها با نام رشته‌ای (برای پوست‌ها و پس‌زمینه‌های پویا)
 */
object Res {

    private val cache = HashMap<String, Int>()

    fun drawable(ctx: Context, name: String): Int {
        val key = "d:$name"
        return cache.getOrPut(key) {
            val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            require(id != 0) { "drawable not found: $name" }
            id
        }
    }
}
