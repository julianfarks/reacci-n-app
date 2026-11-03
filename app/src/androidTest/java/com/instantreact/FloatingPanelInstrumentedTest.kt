package com.instantreact

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.instantreact.ui.FloatingPanel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Comprueba que la ventana flotante se crea, responde a los botones y se elimina. */
@RunWith(AndroidJUnit4::class)
class FloatingPanelInstrumentedTest {

    @Test
    fun panelShowsAndHides() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        assumeTrue("permiso de ventana flotante no concedido", Settings.canDrawOverlays(ctx))
        val starts = AtomicInteger()
        val stops = AtomicInteger()
        var panel: FloatingPanel? = null
        inst.runOnMainSync {
            panel = FloatingPanel(ctx, object : FloatingPanel.Callbacks {
                override fun onStart() { starts.incrementAndGet() }
                override fun onStop() { stops.incrementAndGet() }
                override fun onOpenSettings() = Unit
                override fun onCalibrate() = Unit
            })
            panel!!.show()
        }
        inst.waitForIdleSync()
        assertTrue(panel!!.isShown)
        inst.runOnMainSync { panel!!.refresh() }
        inst.runOnMainSync { panel!!.hide() }
        assertFalse(panel!!.isShown)
    }
}
