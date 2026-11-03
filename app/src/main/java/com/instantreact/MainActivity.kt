package com.instantreact

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.instantreact.core.DetectorConfig
import com.instantreact.core.Engine
import com.instantreact.service.CoreService
import com.instantreact.service.Prefs
import com.instantreact.service.ReactAccessibilityService
import com.instantreact.ui.Palette
import com.instantreact.ui.card
import com.instantreact.ui.dp
import com.instantreact.ui.margins
import com.instantreact.ui.pill
import com.instantreact.ui.roundedBg
import com.instantreact.ui.row
import com.instantreact.ui.text
import com.instantreact.ui.weight
import java.util.Locale

/** Pantalla principal: permisos, tiempo de reacción, detección, calibración y estadísticas. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var overlayStatus: TextView
    private lateinit var accStatus: TextView
    private lateinit var captureStatus: TextView
    private lateinit var activateBtn: TextView
    private lateinit var delayInfo: TextView
    private lateinit var latencyInfo: TextView
    private lateinit var calibInfo: TextView
    private lateinit var statsText: TextView
    private lateinit var customDelay: EditText
    private lateinit var blueLabel: TextView
    private lateinit var radiusLabel: TextView
    private lateinit var tapLabel: TextView
    private val chips = ArrayList<Pair<TextView, Long>>()
    private lateinit var customChip: TextView

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 300L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        setContentView(buildUi())
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        content.addView(text("Instant React", 28f, Color.WHITE, true))
        content.addView(text("Detecta el círculo azul y pulsa por ti, al instante.", 13f, Palette.MUTED).margins(0, dp(2), 0, dp(4)))

        // --- Permisos
        val perms = card()
        perms.addView(text("1 · Permisos", 16f, Palette.BLUE, true))
        perms.addView(
            text(
                "Android exige que tú los concedas. Instant React no puede saltarse ninguno.",
                12f, Palette.MUTED,
            ).margins(0, dp(2), 0, dp(10)),
        )

        overlayStatus = text("", 12f, Palette.MUTED, true)
        perms.addView(permRow("Ventana flotante", "Muestra el panel encima del juego.", overlayStatus, "Conceder") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        accStatus = text("", 12f, Palette.MUTED, true)
        perms.addView(permRow("Accesibilidad", "Permite ejecutar los toques. Actívalo en Ajustes > Accesibilidad > Instant React.", accStatus, "Abrir ajustes") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        captureStatus = text("", 12f, Palette.MUTED, true)
        perms.addView(permRow("Captura de pantalla", "Para ver los colores. Elige «Toda la pantalla» al aceptar.", captureStatus, null) {})
        content.addView(perms)

        activateBtn = pillView("ACTIVAR CAPTURA Y PANEL FLOTANTE") { toggleService() }
        activateBtn.margins(0, dp(12), 0, 0)
        content.addView(activateBtn)

        // --- Tiempo de reacción
        val delayCard = card()
        delayCard.addView(text("2 · Tiempo de reacción", 16f, Palette.BLUE, true))
        delayCard.addView(
            text("Retraso que se espera antes de pulsar. 0 ms = pulsación inmediata.", 12f, Palette.MUTED).margins(0, dp(2), 0, dp(10)),
        )
        buildChips(delayCard)
        val customRow = row()
        customRow.margins(0, dp(10), 0, 0)
        customDelay = EditText(this).apply {
            hint = "Personalizado (ms)"
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.TEXT)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER
            background = roundedBg(Palette.BG, dp(10).toFloat(), Palette.CARD_STROKE, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        customDelay.weight(1f)
        customRow.addView(customDelay)
        customRow.addView(pillView("Aplicar") { applyCustomDelay() }.margins(dp(8), 0, 0, 0))
        delayCard.addView(customRow)
        delayInfo = text("", 13f, Palette.TEXT, true).margins(0, dp(12), 0, 0) as TextView
        latencyInfo = text("", 12f, Palette.MUTED).margins(0, dp(2), 0, 0) as TextView
        delayCard.addView(delayInfo)
        delayCard.addView(latencyInfo)
        content.addView(delayCard)

        // --- Detección
        val det = card()
        det.addView(text("3 · Detección y calibración", 16f, Palette.BLUE, true))
        blueLabel = text("", 12f, Palette.TEXT).margins(0, dp(10), 0, 0) as TextView
        det.addView(blueLabel)
        det.addView(seek(10, 150, Engine.config.blueThreshold) { v, done ->
            blueLabel.text = "Umbral de azul: $v  (menor = más sensible)"
            if (done) updateConfig { it.copy(blueThreshold = v) }
        })
        radiusLabel = text("", 12f, Palette.TEXT).margins(0, dp(10), 0, 0) as TextView
        det.addView(radiusLabel)
        det.addView(seek(4, 80, Engine.config.radius) { v, done ->
            radiusLabel.text = "Tamaño del área de detección: $v px"
            if (done) updateConfig { it.copy(radius = v) }
        })
        tapLabel = text("", 12f, Palette.TEXT).margins(0, dp(10), 0, 0) as TextView
        det.addView(tapLabel)
        det.addView(seek(1, 60, Engine.config.tapDurationMs.toInt()) { v, done ->
            tapLabel.text = "Duración del toque: $v ms"
            if (done) updateConfig { it.copy(tapDurationMs = v.toLong()) }
        })
        calibInfo = text("", 12f, Palette.MUTED).margins(0, dp(12), 0, 0) as TextView
        det.addView(calibInfo)
        det.addView(pillView("CALIBRAR LOS 6 CÍRCULOS") { startCalibration() }.margins(0, dp(8), 0, 0))
        content.addView(det)

        // --- Estadísticas
        val st = card()
        st.addView(text("4 · Estadísticas", 16f, Palette.BLUE, true))
        statsText = text("", 13f, Palette.TEXT).margins(0, dp(8), 0, dp(8)) as TextView
        statsText.setLineSpacing(0f, 1.25f)
        st.addView(statsText)
        st.addView(pillView("Reiniciar estadísticas", false) { Engine.stats.reset() })
        content.addView(st)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.BG)
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return scroll
    }

    private fun pillView(label: String, filled: Boolean = true, onClick: () -> Unit): TextView =
        pill(label, filled, 13f, onClick).apply { gravity = Gravity.CENTER }

    private fun permRow(title: String, desc: String, status: TextView, action: String?, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val head = row()
        val t = text(title, 14f, Palette.TEXT, true)
        t.weight(1f)
        head.addView(t)
        head.addView(status)
        box.addView(head)
        box.addView(text(desc, 11f, Palette.MUTED).margins(0, dp(2), 0, dp(4)))
        if (action != null) box.addView(pillView(action, false, onClick))
        return box
    }

    private fun buildChips(parent: LinearLayout) {
        val items = ArrayList<Pair<String, Long>>()
        for (ms in DetectorConfig.DELAY_PRESETS) items.add(Pair("$ms ms", ms))
        var line: LinearLayout? = null
        for ((i, it) in items.withIndex()) {
            if (i % 3 == 0) {
                line = row()
                line.margins(0, if (i == 0) 0 else dp(8), 0, 0)
                parent.addView(line)
            }
            val chip = chipView(it.first) { setDelay(it.second) }
            chip.weight(1f)
            (chip.layoutParams as LinearLayout.LayoutParams).setMargins(if (i % 3 == 0) 0 else dp(8), 0, 0, 0)
            line!!.addView(chip)
            chips.add(Pair(chip, it.second))
        }
        customChip = chipView("Personalizado") { customDelay.requestFocus() }
        customChip.margins(0, dp(8), 0, 0)
        parent.addView(customChip)
    }

    private fun chipView(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Palette.TEXT)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = roundedBg(Palette.BG, dp(10).toFloat(), Palette.CARD_STROKE, dp(1))
            setOnClickListener { onClick() }
        }

    private fun seek(lo: Int, hi: Int, value: Int, onChange: (Int, Boolean) -> Unit): SeekBar =
        SeekBar(this).apply {
            this.max = hi - lo
            this.progress = (value - lo).coerceIn(0, hi - lo)
            progressTintList = ColorStateList.valueOf(Palette.BLUE)
            thumbTintList = ColorStateList.valueOf(Palette.BLUE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    onChange(p + lo, false)
                }

                override fun onStartTrackingTouch(s: SeekBar?) = Unit

                override fun onStopTrackingTouch(s: SeekBar?) {
                    onChange((s?.progress ?: 0) + lo, true)
                }
            })
            onChange(this.progress + lo, false)
        }

    // ------------------------------------------------------------- acciones

    private fun updateConfig(change: (DetectorConfig) -> DetectorConfig) {
        Prefs.save(this, change(Engine.config))
    }

    private fun setDelay(ms: Long) {
        updateConfig { it.copy(delayMs = ms) }
        customDelay.setText("")
        hideKeyboard()
        refresh()
    }

    private fun applyCustomDelay() {
        val v = customDelay.text.toString().trim().toLongOrNull()
        if (v == null || v < 0 || v > 60_000) {
            Toast.makeText(this, "Introduce un valor entre 0 y 60000 ms", Toast.LENGTH_SHORT).show()
            return
        }
        updateConfig { it.copy(delayMs = v) }
        hideKeyboard()
        refresh()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(customDelay.windowToken, 0)
        customDelay.clearFocus()
    }

    private fun toggleService() {
        if (CoreService.instance != null) {
            CoreService.stop(this)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primero concede el permiso de ventana flotante", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                CoreService.start(this, resultCode, data)
                if (!Engine.config.calibrated) {
                    Toast.makeText(this, "Ahora calibra los 6 círculos con el juego en pantalla", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Captura de pantalla denegada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCalibration() {
        if (CoreService.instance == null) {
            Toast.makeText(this, "Activa primero la captura y el panel flotante", Toast.LENGTH_LONG).show()
            return
        }
        CoreService.calibrate(this)
        // Volvemos al juego para calibrar con él a la vista.
        moveTaskToBack(true)
    }

    // -------------------------------------------------------------- refresco

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = ReactAccessibilityService.instance != null
        val capOk = CoreService.instance != null
        setStatus(overlayStatus, overlayOk, "Concedido", "Pendiente")
        setStatus(accStatus, accOk, "Activo", "Desactivado")
        setStatus(captureStatus, capOk, "En marcha", "Sin iniciar")
        activateBtn.text = if (capOk) "DETENER CAPTURA Y PANEL" else "ACTIVAR CAPTURA Y PANEL FLOTANTE"

        val cfg = Engine.config
        val s = Engine.stats.snapshot()
        var matched = false
        for ((chip, ms) in chips) {
            val on = ms == cfg.delayMs
            if (on) matched = true
            styleChip(chip, on)
        }
        styleChip(customChip, !matched)
        delayInfo.text = "Retraso configurado: ${cfg.delayMs} ms"
        latencyInfo.text = if (s.taps == 0) {
            "Latencia real del sistema: — (aún sin pulsaciones)"
        } else {
            String.format(
                Locale.US,
                "Latencia real del sistema: %.0f ms último · %.0f ms media (detección → toque, sin contar el retraso)",
                s.lastSystemLatencyMs, s.meanSystemLatencyMs,
            )
        }
        calibInfo.text = if (cfg.calibrated) {
            "Calibrado: 6 círculos guardados (${cfg.calibW}×${cfg.calibH}). Recalibra si cambias de resolución o mueves el juego."
        } else {
            "Sin calibrar: coloca los 6 marcadores sobre los círculos grises."
        }
        statsText.text = String.format(
            Locale.US,
            "Estímulos detectados: %d\nPulsaciones ejecutadas: %d\nTiempo medio de reacción: %s\nMejor tiempo de reacción: %s\nDetecciones fallidas: %d\nDetecciones correctas: %.1f %%",
            s.detected, s.taps,
            if (s.taps == 0) "—" else String.format(Locale.US, "%.0f ms", s.meanReactionMs),
            if (s.taps == 0) "—" else String.format(Locale.US, "%.0f ms", s.bestReactionMs),
            s.failed,
            if (s.detected == 0) 0.0 else s.successPercent,
        )
    }

    private fun setStatus(v: TextView, ok: Boolean, yes: String, no: String) {
        v.text = if (ok) "● $yes" else "● $no"
        v.setTextColor(if (ok) Palette.GREEN else Palette.RED)
    }

    private fun styleChip(c: TextView, on: Boolean) {
        c.setTextColor(if (on) Color.WHITE else Palette.TEXT)
        c.background = if (on) roundedBg(Palette.BLUE, dp(10).toFloat())
        else roundedBg(Palette.BG, dp(10).toFloat(), Palette.CARD_STROKE, dp(1))
    }

    companion object {
        private const val REQ_CAPTURE = 42
    }
}
