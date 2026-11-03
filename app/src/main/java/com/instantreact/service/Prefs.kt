package com.instantreact.service

import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.instantreact.core.Engine
import com.instantreact.core.KeyValueStore
import com.instantreact.core.Profile
import com.instantreact.core.ProfileRepository
import com.instantreact.core.DetectorConfig

/** Persistencia: perfiles de calibración + ajustes globales sobre SharedPreferences. */
object Prefs {
    private const val FILE = "instant_react"

    private class SpStore(private val sp: SharedPreferences) : KeyValueStore {
        override fun get(key: String): String? = sp.getString(key, null)
        override fun put(key: String, value: String?) {
            val e = sp.edit()
            if (value == null) e.remove(key) else e.putString(key, value)
            e.apply()
        }
    }

    private var repo: ProfileRepository? = null

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun portraitSize(ctx: Context): Pair<Int, Int> {
        val dm = ctx.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val m = DisplayMetrics()
        dm.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
        return Pair(minOf(m.widthPixels, m.heightPixels), maxOf(m.widthPixels, m.heightPixels))
    }

    /** Tamaño de pantalla en vertical (para crear perfiles por defecto). */
    fun portrait(ctx: Context): Pair<Int, Int> = portraitSize(ctx)

    @Synchronized
    fun repo(ctx: Context): ProfileRepository {
        repo?.let { return it }
        val (pw, ph) = portraitSize(ctx)
        val prefs = sp(ctx)
        val r = ProfileRepository(SpStore(prefs), pw, ph)
        if (!r.hasProfiles()) {
            // Primer arranque: los 8 puntos aparecen ya calibrados por defecto.
            r.ensureDefaults()
            migrateLegacy(prefs, r)
        } else {
            r.ensureDefaults()
        }
        repo = r
        return r
    }

    /** La versión 1.0 guardaba una sola calibración de 6 puntos: se conserva como un perfil más. */
    private fun migrateLegacy(sp: SharedPreferences, r: ProfileRepository) {
        if (!sp.getBoolean("calibrated", false)) return
        val xs = IntArray(6) { sp.getInt("x$it", 0) }
        val ys = IntArray(6) { sp.getInt("y$it", 0) }
        val radius = sp.getInt("radius", 18)
        val p = r.create("Perfil anterior (6 puntos)", 6, sp.getInt("blue", 60))
        r.save(
            p.copy(xs = xs, ys = ys, radii = IntArray(6) { radius }, calibW = sp.getInt("calibW", p.calibW), calibH = sp.getInt("calibH", p.calibH)),
        )
        if (sp.getLong("delay", -1L) >= 0) r.delayMs = sp.getLong("delay", 0L)
        if (sp.getLong("tapDur", -1L) > 0) r.tapDurationMs = sp.getLong("tapDur", 10L)
        sp.edit().remove("calibrated").apply()
    }

    /** Solo para pruebas instrumentadas: simula un nuevo arranque de la app. */
    @Synchronized
    fun resetCacheForTests() {
        repo = null
    }

    /** Publica en el motor la configuración del perfil activo. */
    fun apply(ctx: Context) {
        val r = repo(ctx)
        Engine.profileName = r.active().name
        Engine.config = r.buildConfig()
    }

    fun init(ctx: Context) = apply(ctx)

    fun updateActive(ctx: Context, change: (Profile) -> Profile) {
        val r = repo(ctx)
        r.save(change(r.active()))
        apply(ctx)
    }

    fun setDelay(ctx: Context, ms: Long) {
        repo(ctx).delayMs = ms
        apply(ctx)
    }

    fun setTapDuration(ctx: Context, ms: Long) {
        repo(ctx).tapDurationMs = ms
        apply(ctx)
    }

    /** Guarda el resultado de la calibración en el perfil activo. */
    fun saveCalibration(ctx: Context, cfg: DetectorConfig) {
        updateActive(ctx) {
            it.copy(xs = cfg.xs.copyOf(), ys = cfg.ys.copyOf(), radii = cfg.radii.copyOf(), calibW = cfg.calibW, calibH = cfg.calibH)
        }
    }

    fun panelPos(ctx: Context): Pair<Int, Int> {
        val p = sp(ctx)
        return Pair(p.getInt("panelX", 24), p.getInt("panelY", 160))
    }

    fun savePanelPos(ctx: Context, x: Int, y: Int) {
        sp(ctx).edit().putInt("panelX", x).putInt("panelY", y).apply()
    }
}
