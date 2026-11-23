package com.kjota.ritmo.data

import android.content.Context

object Prefs {
    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("ritmo", Context.MODE_PRIVATE)

    fun getLong(ctx: Context, key: String, def: Long = 0L): Long = sp(ctx).getLong(key, def)
    fun putLong(ctx: Context, key: String, value: Long) {
        sp(ctx).edit().putLong(key, value).apply()
    }
}
