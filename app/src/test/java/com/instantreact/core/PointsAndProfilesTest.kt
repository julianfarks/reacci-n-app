package com.instantreact.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsAndProfilesTest {
    private fun repo(store: KeyValueStore = MapStore(), w: Int = 1116, h: Int = 2480) = ProfileRepository(store, w, h)

    // ---------------------------------------------------------- primer arranque
    @Test fun firstRunCreatesCalibratedEightPointProfile() {
        val r = repo()
        assertFalse(r.hasProfiles())
        r.ensureDefaults()
        val cfg = r.buildConfig()
        assertEquals(8, cfg.count)
        assertTrue("calibrado de fábrica", cfg.calibrated)
        assertEquals(1116, cfg.calibW); assertEquals(2480, cfg.calibH)
        assertEquals("Perfil 8 puntos", r.active().name)
        assertEquals(listOf("Perfil 8 puntos", "Perfil 6 puntos", "Perfil 10 puntos"), r.list().map { it.name })
        assertEquals(listOf(8, 6, 10), r.list().map { it.count })
    }

    @Test fun defaultEightPointsSitOnTheGameGrid() {
        val (xs, ys) = DefaultLayout.positions(8, 1116, 2480)
        // rejilla medida en la captura: x=275/558/840, y=1245/1527/1810 (sin el centro)
        val expected = listOf(275 to 1245, 558 to 1245, 840 to 1245, 275 to 1527, 840 to 1527, 275 to 1810, 558 to 1810, 840 to 1810)
        for (i in 0 until 8) {
            assertTrue("x$i ${xs[i]}", Math.abs(xs[i] - expected[i].first) <= 3)
            assertTrue("y$i ${ys[i]}", Math.abs(ys[i] - expected[i].second) <= 3)
        }
    }

    @Test fun secondRunKeepsSavedStateAndDoesNotRecreateDefaults() {
        val store = MapStore()
        val r1 = repo(store)
        r1.ensureDefaults()
        val p = r1.active()
        r1.save(p.copy(xs = p.xs.copyOf().also { it[0] = 999 }))
        val r2 = repo(store)
        r2.ensureDefaults()
        assertEquals(3, r2.list().size)
        assertEquals(999, r2.active().xs[0])
    }

    // ---------------------------------------------------------- cantidad de puntos
    @Test fun everyRequestedCountIsSupported() {
        for (n in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 16, 24, 50, 200)) {
            val (xs, ys) = DefaultLayout.positions(n, 1080, 2400)
            assertEquals(n, xs.size); assertEquals(n, ys.size)
            val distinct = (0 until n).map { xs[it] to ys[it] }.toSet()
            assertEquals("posiciones distintas para $n", n, distinct.size)
            for (i in 0 until n) assertTrue(xs[i] in 0..1080 && ys[i] in 0..2400)
        }
    }

    @Test fun resizeCreatesAndRemovesMarkersAutomatically() {
        val r = repo(); r.ensureDefaults()
        val p8 = r.active()
        val p12 = Profiles.resize(p8, 12)
        assertEquals(12, p12.count); assertEquals(12, p12.radii.size)
        val back = Profiles.resize(p12, 8)
        assertEquals(8, back.count)
        assertEquals(p8.xs.toList(), back.xs.toList()) // pristine: vuelve a la disposición por defecto
    }

    @Test fun resizeKeepsUserCalibrationOfExistingPoints() {
        val r = repo(); r.ensureDefaults()
        val moved = r.active().let { it.copy(xs = it.xs.copyOf().also { a -> a[2] = 777 }, ys = it.ys.copyOf().also { a -> a[2] = 888 }) }
        val bigger = Profiles.resize(moved, 10)
        assertEquals(10, bigger.count)
        assertEquals(777, bigger.xs[2]); assertEquals(888, bigger.ys[2])
        val smaller = Profiles.resize(moved, 3)
        assertEquals(3, smaller.count)
        assertEquals(777, smaller.xs[2])
    }

    @Test fun customCountIsClampedToSafeRange() {
        val r = repo(); r.ensureDefaults()
        assertEquals(DetectorConfig.MAX_POINTS, Profiles.resize(r.active(), 100000).count)
        assertEquals(1, Profiles.resize(r.active(), 0).count)
        assertTrue("el tope es alto, no un límite artificial bajo", DetectorConfig.MAX_POINTS >= 100)
    }

    // ---------------------------------------------------------- perfiles
    @Test fun profilesKeepTheirOwnCoordinatesSizesAndColorSettings() {
        val r = repo(); r.ensureDefaults()
        val a = r.active()
        r.save(a.copy(radii = IntArray(a.count) { 33 }, blueThreshold = 90, xs = a.xs.copyOf().also { it[0] = 11 }))
        val b = r.create("Mi perfil", 5)
        r.save(b.copy(blueThreshold = 25, radii = IntArray(5) { 44 }))
        r.setActive(b.id)
        assertEquals(5, r.buildConfig().count); assertEquals(25, r.buildConfig().blueThreshold); assertEquals(44, r.buildConfig().radii[0])
        r.setActive(a.id)
        assertEquals(8, r.buildConfig().count); assertEquals(90, r.buildConfig().blueThreshold)
        assertEquals(33, r.buildConfig().radii[3]); assertEquals(11, r.buildConfig().xs[0])
    }

    @Test fun duplicateRenameDeleteAndPersistence() {
        val store = MapStore()
        val r = repo(store); r.ensureDefaults()
        val copy = r.duplicate(r.activeId(), "Copia | rara\nnombre")!!
        assertEquals("Copia / rara nombre", copy.name)
        r.rename(copy.id, "Otro")
        assertEquals("Otro", repo(store).get(copy.id)!!.name)
        assertTrue(r.delete(copy.id)); assertNull(r.get(copy.id))
        r.setActive(r.list().last().id)
        val victim = r.activeId()
        assertTrue(r.delete(victim)); assertNotEquals(victim, r.activeId()); assertNotNull(r.get(r.activeId()))
        while (r.list().size > 1) r.delete(r.list().first().id)
        assertFalse("no se puede borrar el último perfil", r.delete(r.list().first().id))
    }

    @Test fun globalSettingsAreSharedAcrossProfiles() {
        val r = repo(); r.ensureDefaults()
        r.delayMs = 75; r.tapDurationMs = 20
        r.setActive(r.list()[1].id)
        assertEquals(75L, r.buildConfig().delayMs); assertEquals(20L, r.buildConfig().tapDurationMs)
    }

    @Test fun codecRoundTripAndCorruptData() {
        val p = DefaultLayout.profile("p9", "X", 10, 1080, 2400, 42)
        assertEquals(p, ProfileCodec.decode("p9", ProfileCodec.encode(p)))
        assertNull(ProfileCodec.decode("p9", "basura"))
        assertNull(ProfileCodec.decode("p9", "n|60|1|1|1,2|1|5"))
    }
}
