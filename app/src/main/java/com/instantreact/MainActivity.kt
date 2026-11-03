package com.instantreact

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.instantreact.core.DetectorConfig
import com.instantreact.core.Engine
import com.instantreact.core.Profiles
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

/** Pantalla principal: panel de estado, permisos, perfiles, tiempo de reacción, detección y estadísticas. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var pointsInfo: TextView
    private lateinit var stateInfo: TextView
    private lateinit var delaySummary: TextView
    private lateinit var latencySummary: TextView
    private lateinit var respSummary: TextView
    private lateinit var profileInfo: TextView
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
    private lateinit var blueSeek: SeekBar
    private lateinit var radiusSeek: SeekBar
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
        Prefs.init(this) // primer arranque: se crean los perfiles y los 8 puntos ya calibrados
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
        content.addView(text("Detecta el punto que se ilumina de azul y pulsa por ti, al instante.", 13f, Palette.MUTED).margins(0, dp(2), 0, dp(4)))

        // --- Panel principal
        val panel = card()
        panel.addView(text("Panel", 16f, Palette.BLUE, true))
        pointsInfo = text("", 20f, Color.WHITE, true).margins(0, dp(6), 0, dp(8)) as TextView
        panel.addView(pointsInfo)
        val panelBtns = row()
        val countBtn = pillView("Modificar cantidad") { showCountDialog() }
        val calBtn = pillView("Calibrar", true) { startCalibration() }
        countBtn.weight(1f)
        calBtn.weight(1f)
        (countBtn.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(8)
        panelBtns.addView(countBtn)
        panelBtns.addView(calBtn)
        panel.addView(panelBtns)
        stateInfo = text("", 13f, Palette.TEXT, true).margins(0, dp(12), 0, 0) as TextView
        delaySummary = text("", 12f, Palette.TEXT).margins(0, dp(4), 0, 0) as TextView
        latencySummary = text("", 12f, Palette.MUTED).margins(0, dp(2), 0, 0) as TextView
        respSummary = text("", 12f, Palette.TEXT).margins(0, dp(2), 0, 0) as TextView
        panel.addView(stateInfo)
        panel.addView(delaySummary)
        panel.addView(latencySummary)
        panel.addView(respSummary)
        content.addView(panel)

        // --- Permisos
        val perms = card()
        perms.addView(text("Permisos", 16f, Palette.BLUE, true))
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

        // --- Perfiles
        val prof = card()
        prof.addView(text("Perfiles de calibración", 16f, Palette.BLUE, true))
        prof.addView(
            text("Cada perfil guarda sus puntos, el tamaño de cada área y el umbral de color.", 12f, Palette.MUTED).margins(0, dp(2), 0, dp(8)),
        )
        profileInfo = text("", 14f, Palette.TEXT, true) as TextView
        prof.addView(profileInfo)
        val pr1 = row()
        pr1.margins(0, dp(10), 0, 0)
        val switchBtn = pillView("Cambiar perfil") { showProfilesDialog() }
        val copyBtn = pillView("Guardar copia", false) { saveCopy() }
        switchBtn.weight(1f); copyBtn.weight(1f)
        (switchBtn.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(8)
        pr1.addView(switchBtn); pr1.addView(copyBtn)
        prof.addView(pr1)
        val pr2 = row()
        pr2.margins(0, dp(8), 0, 0)
        val renBtn = pillView("Renombrar", false) { renameProfile() }
        val delBtn = pillView("Eliminar", false) { deleteProfile() }
        renBtn.weight(1f); delBtn.weight(1f)
        (renBtn.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(8)
        pr2.addView(renBtn); pr2.addView(delBtn)
        prof.addView(pr2)
        content.addView(prof)

        // --- Tiempo de reacción
        val delayCard = card()
        delayCard.addView(text("Tiempo de reacción", 16f, Palette.BLUE, true))
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
        val cfg = Engine.config
        val det = card()
        det.addView(text("Detección y calibración", 16f, Palette.BLUE, true))
        blueLabel = text("", 12f, Palette.TEXT).margins(0, dp(10), 0, 0) as TextView
        det.addView(blueLabel)
        blueSeek = seek(10, 150, cfg.blueThreshold) { v, done ->
            blueLabel.text = "Umbral de azul: $v  (menor = más sensible)"
            if (done) Prefs.updateActive(this) { it.copy(blueThreshold = v) }
        }
        det.addView(blueSeek)
        radiusLabel = text("", 12f, Palette.TEXT).margins(0, dp(10), 0, 0) as TextView
        det.addView(radiusLabel)
        val avgR = if (cfg.count == 0) 18 else cfg.radii.average().toInt()
        radiusSeek = seek(4, 150, avgR) { v, done ->
            radiusLabel.text = "Tamaño del área de detección (todos los puntos): $v px"
            if (done) Prefs.updateActive(this) { it.copy(radii = IntArray(it.count) { v }) }
        }
        det.addView(radiusSeek)
        tapLabel = text("", 12f, Palette.TEXT).margins(0, dp(10), 0, 0) as TextView
        det.addView(tapLabel)
        det.addView(seek(1, 60, cfg.tapDurationMs.toInt()) { v, done ->
            tapLabel.text = "Duración del toque: $v ms"
            if (done) Prefs.setTapDuration(this, v.toLong())
        })
        calibInfo = text("", 12f, Palette.MUTED).margins(0, dp(12), 0, 0) as TextView
        det.addView(calibInfo)
        det.addView(pillView("CALIBRAR PUNTOS") { startCalibration() }.margins(0, dp(8), 0, 0))
        content.addView(det)

        // --- Estadísticas
        val st = card()
        st.addView(text("Estadísticas", 16f, Palette.BLUE, true))
        statsText = text("", 13f, Palette.TEXT).margins(0, dp(8), 0, dp(8)) as TextView
        statsText.setLineSpacing(0f, 1.25f)
        st.addView(statsText)
        st.addView(pillView("Reiniciar estadísticas", false) { Engine.stats.reset() })
        content.addView(st)

        return ScrollView(this).apply {
            setBackgroundColor(Palette.BG)
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
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
        var line: LinearLayout? = null
        for ((i, ms) in DetectorConfig.DELAY_PRESETS.withIndex()) {
            if (i % 3 == 0) {
                line = row()
                line.margins(0, if (i == 0) 0 else dp(8), 0, 0)
                parent.addView(line)
            }
            val chip = chipView("$ms ms") { setDelay(ms) }
            chip.weight(1f)
            (chip.layoutParams as LinearLayout.LayoutParams).setMargins(if (i % 3 == 0) 0 else dp(8), 0, 0, 0)
            line!!.addView(chip)
            chips.add(Pair(chip, ms))
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

    // ------------------------------------------------------------- diálogos

    private fun dialog(): AlertDialog.Builder = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun padded(v: View): View = FrameLayout(this).apply {
        setPadding(dp(20), dp(8), dp(20), dp(4))
        addView(v)
    }

    private fun showCountDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var dlg: AlertDialog
        var line: LinearLayout? = null
        for ((i, n) in DetectorConfig.COUNT_PRESETS.withIndex()) {
            if (i % 3 == 0) {
                line = row()
                line.margins(0, if (i == 0) 0 else dp(8), 0, 0)
                box.addView(line)
            }
            val chip = chipView("$n") { dlg.dismiss(); applyCount(n) }
            chip.weight(1f)
            (chip.layoutParams as LinearLayout.LayoutParams).setMargins(if (i % 3 == 0) 0 else dp(8), 0, 0, 0)
            line!!.addView(chip)
        }
        val custom = EditText(this).apply {
            hint = "Personalizada (1–${DetectorConfig.MAX_POINTS})"
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.TEXT)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val crow = row()
        crow.margins(0, dp(12), 0, 0)
        custom.weight(1f)
        crow.addView(custom)
        crow.addView(pillView("Aplicar") {
            val n = custom.text.toString().trim().toIntOrNull()
            if (n == null || n !in 1..DetectorConfig.MAX_POINTS) {
                Toast.makeText(this, "Introduce un número entre 1 y ${DetectorConfig.MAX_POINTS}", Toast.LENGTH_SHORT).show()
            } else {
                dlg.dismiss(); applyCount(n)
            }
        }.margins(dp(8), 0, 0, 0))
        box.addView(crow)
        dlg = dialog()
            .setTitle("Cantidad de puntos (ahora: ${Engine.config.count})")
            .setView(padded(box))
            .setNegativeButton("Cerrar", null)
            .create()
        dlg.show()
    }

    private fun applyCount(n: Int) {
        Prefs.updateActive(this) { Profiles.resize(it, n) }
        Toast.makeText(this, "$n puntos. Pulsa Calibrar si alguno no coincide con su círculo.", Toast.LENGTH_LONG).show()
        syncSliders()
        refresh()
    }

    private fun promptText(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(initial)
            setTextColor(Palette.TEXT)
            setSelectAllOnFocus(true)
        }
        dialog().setTitle(title).setView(padded(et))
            .setPositiveButton("Aceptar") { _, _ -> ok(et.text.toString()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showProfilesDialog() {
        val repo = Prefs.repo(this)
        val list = repo.list()
        val active = repo.activeId()
        val names = list.map { (if (it.id == active) "✓ " else "    ") + it.name + "  (${it.count} puntos)" } + "＋ Nuevo perfil…"
        dialog().setTitle("Perfiles")
            .setItems(names.toTypedArray()) { _, which ->
                if (which < list.size) {
                    repo.setActive(list[which].id)
                    onProfileChanged()
                } else {
                    newProfileDialog()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun newProfileDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = EditText(this).apply { hint = "Nombre"; setTextColor(Palette.TEXT); setHintTextColor(Palette.MUTED); setText("Perfil personalizado") }
        val count = EditText(this).apply {
            hint = "Número de puntos (1–${DetectorConfig.MAX_POINTS})"
            setTextColor(Palette.TEXT); setHintTextColor(Palette.MUTED)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Engine.config.count.toString())
        }
        box.addView(name); box.addView(count)
        dialog().setTitle("Nuevo perfil").setView(padded(box))
            .setPositiveButton("Crear") { _, _ ->
                val n = count.text.toString().trim().toIntOrNull()
                if (n == null || n !in 1..DetectorConfig.MAX_POINTS) {
                    Toast.makeText(this, "Número de puntos no válido", Toast.LENGTH_SHORT).show()
                } else {
                    val repo = Prefs.repo(this)
                    val p = repo.create(name.text.toString(), n, Engine.config.blueThreshold)
                    repo.setActive(p.id)
                    onProfileChanged()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveCopy() {
        val repo = Prefs.repo(this)
        val cur = repo.active()
        promptText("Guardar copia del perfil", cur.name + " (copia)") { n ->
            repo.duplicate(cur.id, n)?.let { repo.setActive(it.id) }
            onProfileChanged()
        }
    }

    private fun renameProfile() {
        val repo = Prefs.repo(this)
        val cur = repo.active()
        promptText("Renombrar perfil", cur.name) { n ->
            repo.rename(cur.id, n)
            onProfileChanged()
        }
    }

    private fun deleteProfile() {
        val repo = Prefs.repo(this)
        val cur = repo.active()
        if (repo.list().size <= 1) {
            Toast.makeText(this, "No se puede eliminar el último perfil", Toast.LENGTH_SHORT).show()
            return
        }
        dialog().setTitle("¿Eliminar «${cur.name}»?")
            .setMessage("Se perderán sus puntos y ajustes.")
            .setPositiveButton("Eliminar") { _, _ ->
                repo.delete(cur.id)
                onProfileChanged()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun onProfileChanged() {
        Prefs.apply(this)
        syncSliders()
        refresh()
    }

    private fun syncSliders() {
        val cfg = Engine.config
        blueSeek.progress = (cfg.blueThreshold - 10).coerceIn(0, 140)
        val avgR = if (cfg.count == 0) 18 else cfg.radii.average().toInt()
        radiusSeek.progress = (avgR - 4).coerceIn(0, 146)
    }

    // ------------------------------------------------------------- acciones

    private fun setDelay(ms: Long) {
        Prefs.setDelay(this, ms)
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
        Prefs.setDelay(this, v)
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
                Toast.makeText(this, "Listo. Abre el juego y pulsa INICIAR (o Calibrar para afinar los puntos)", Toast.LENGTH_LONG).show()
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

        pointsInfo.text = "${cfg.count} ${if (cfg.count == 1) "punto" else "puntos"} · ${Engine.profileName}"
        val (stateTxt, stateColor) = when {
            !capOk -> Pair("Estado: captura sin iniciar", Palette.MUTED)
            !cfg.calibrated -> Pair("Estado: sin calibrar", Palette.ORANGE)
            Engine.orientationMismatch -> Pair("Estado: gira la pantalla (orientación distinta a la calibrada)", Palette.ORANGE)
            Engine.running -> Pair("Estado: detección activa", Palette.GREEN)
            else -> Pair("Estado: detenida (pulsa INICIAR en el panel flotante)", Palette.TEXT)
        }
        stateInfo.text = stateTxt
        stateInfo.setTextColor(stateColor)
        delaySummary.text = "Tiempo de reacción configurado: ${cfg.delayMs} ms"
        latencySummary.text = if (s.taps == 0) {
            "Latencia real del sistema: —"
        } else {
            String.format(Locale.US, "Latencia real del sistema: %.0f ms (media %.0f ms)", s.lastSystemLatencyMs, s.meanSystemLatencyMs)
        }
        respSummary.text = "Respuestas: ${s.taps}"
        profileInfo.text = "Activo: ${Engine.profileName} · ${cfg.count} puntos"

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
        calibInfo.text = "Perfil «${Engine.profileName}»: ${cfg.count} puntos (${cfg.calibW}×${cfg.calibH}). " +
            "Ya están colocados; usa Calibrar para ajustarlos al juego (Auto, arrastrar, añadir o quitar)."
        statsText.text = String.format(
            Locale.US,
            "Estímulos detectados: %d\nPulsaciones ejecutadas: %d\nÚltimo punto iluminado: %s\nTiempo medio de reacción: %s\nMejor tiempo de reacción: %s\nDetecciones fallidas: %d\nDetecciones correctas: %.1f %%",
            s.detected, s.taps,
            if (s.lastPoint >= 0) "${s.lastPoint + 1}" else "—",
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
