package com.instantreact

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.instantreact.core.Engine
import com.instantreact.core.Profiles
import com.instantreact.service.Prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Primer arranque (8 puntos ya colocados), cambio de cantidad y perfiles persistentes en el dispositivo. */
@RunWith(AndroidJUnit4::class)
class ProfilesInstrumentedTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun freshInstall() {
        ctx.getSharedPreferences("instant_react", 0).edit().clear().commit()
        Prefs.resetCacheForTests()
    }

    @Before fun setUp() = freshInstall()

    @After fun tearDown() = freshInstall()

    @Test
    fun firstLaunchShowsEightCalibratedPointsInsideTheScreen() {
        Prefs.init(ctx)
        val cfg = Engine.config
        assertEquals(8, cfg.count)
        assertTrue("calibrado desde el primer arranque", cfg.calibrated)
        val m = realScreen(ctx)
        val pw = minOf(m.widthPixels, m.heightPixels); val ph = maxOf(m.widthPixels, m.heightPixels)
        assertEquals(pw, cfg.calibW); assertEquals(ph, cfg.calibH)
        for (i in 0 until 8) assertTrue("punto $i dentro", cfg.xs[i] in 0..pw && cfg.ys[i] in 0..ph && cfg.radii[i] > 0)
        assertEquals("Perfil 8 puntos", Engine.profileName)
        assertEquals(3, Prefs.repo(ctx).list().size)
    }

    @Test
    fun countAndProfilesSurviveRestart() {
        Prefs.init(ctx)
        Prefs.updateActive(ctx) { Profiles.resize(it, 12) }
        assertEquals(12, Engine.config.count)
        val repo = Prefs.repo(ctx)
        val mine = repo.create("Mi perfil", 5)
        repo.setActive(mine.id)
        Prefs.apply(ctx)
        assertEquals(5, Engine.config.count)

        // "reinicio" de la app: se descarta la caché en memoria y se vuelve a leer del disco
        Prefs.resetCacheForTests()
        Prefs.init(ctx)
        assertEquals("Mi perfil", Engine.profileName)
        assertEquals(5, Engine.config.count)
        Prefs.repo(ctx).setActive(Prefs.repo(ctx).list().first().id)
        Prefs.apply(ctx)
        assertEquals(12, Engine.config.count)
    }
}
