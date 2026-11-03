package com.instantreact.service

import android.content.Context
import com.instantreact.core.DetectorConfig
import com.instantreact.core.Engine

/** Persistencia de la calibración y los ajustes (SharedPreferences). */
object Prefs {
    private const val FILE = "instant_react"

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(ctx: Context): DetectorConfig {
        val p = sp(ctx)
        val xs = IntArray(DetectorConfig.CIRCLE_COUNT) { p.getInt("x$it", 0) }
        val ys = IntArray(DetectorConfig.CIRCLE_COUNT) { p.getInt("y$it", 0) }
        return DetectorConfig(
            xs = xs,
            ys = ys,
            radius = p.getInt("radius", 18),
            blueThreshold = p.getInt("blue", 60),
            delayMs = p.getLong("delay", 0L),
            tapDurationMs = p.getLong("tapDur", 10L),
            calibrated = p.getBoolean("calibrated", false),
            calibW = p.getInt("calibW", 0),
            calibH = p.getInt("calibH", 0),
        )
    }

    /** Guarda y publica la configuración en el motor. */
    fun save(ctx: Context, c: DetectorConfig) {
        val e = sp(ctx).edit()
        for (i in 0 until DetectorConfig.CIRCLE_COUNT) {
            e.putInt("x$i", c.xs[i])
            e.putInt("y$i", c.ys[i])
        }
        e.putInt("radius", c.radius)
        e.putInt("blue", c.blueThreshold)
        e.putLong("delay", c.delayMs)
        e.putLong("tapDur", c.tapDurationMs)
        e.putBoolean("calibrated", c.calibrated)
        e.putInt("calibW", c.calibW)
        e.putInt("calibH", c.calibH)
        e.apply()
        Engine.config = c
    }

    fun init(ctx: Context) {
        Engine.config = load(ctx)
    }

    fun panelPos(ctx: Context): Pair<Int, Int> {
        val p = sp(ctx)
        return Pair(p.getInt("panelX", 24), p.getInt("panelY", 160))
    }

    fun savePanelPos(ctx: Context, x: Int, y: Int) {
        sp(ctx).edit().putInt("panelX", x).putInt("panelY", y).apply()
    }
}
