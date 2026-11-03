package com.instantreact.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.instantreact.core.CircleFinder
import com.instantreact.core.DetectorConfig
import com.instantreact.service.Snapshot
import kotlin.math.hypot

/**
 * Modo de calibración: pantalla completa translúcida con 6 marcadores numerados arrastrables.
 * Permite cambiar el tamaño del área de detección, auto-detectar los 6 círculos grises, guardar o cancelar.
 */
class CalibrationOverlay(
    private val ctx: Context,
    private val initial: DetectorConfig,
    private val screenW: Int,
    private val screenH: Int,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onSave(cfg: DetectorConfig)
        fun onCancel()

        /** Última captura reducida de la pantalla (sin nuestra superposición), o null. */
        fun grabSnapshot(): Snapshot?
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private val markersX = FloatArray(DetectorConfig.CIRCLE_COUNT)
    private val markersY = FloatArray(DetectorConfig.CIRCLE_COUNT)
    private var radius = initial.radius.toFloat().coerceIn(4f, 150f)
    private var selected = -1

    private val root = FrameLayout(ctx)
    private lateinit var canvasView: MarkerView
    private lateinit var bar: LinearLayout
    private lateinit var radiusText: TextView
    private var barOnTop = true
    private var attached = false

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private inner class MarkerView(c: Context) : View(c) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.dp(2).toFloat()
            color = Palette.BLUE
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x332F8BFF
        }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.dp(1).toFloat()
            color = Color.WHITE
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = ctx.dp(14).toFloat()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0x44000000)
            for (i in 0 until DetectorConfig.CIRCLE_COUNT) {
                val x = markersX[i]
                val y = markersY[i]
                ring.color = if (i == selected) Palette.GREEN else Palette.BLUE
                canvas.drawCircle(x, y, radius, fill)
                canvas.drawCircle(x, y, radius, ring)
                val arm = ctx.dp(8).toFloat()
                canvas.drawLine(x - arm, y, x + arm, y, cross)
                canvas.drawLine(x, y - arm, x, y + arm, cross)
                canvas.drawText((i + 1).toString(), x, y - radius - ctx.dp(6), label)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    var best = -1
                    var bestD = Float.MAX_VALUE
                    for (i in 0 until DetectorConfig.CIRCLE_COUNT) {
                        val d = hypot(e.x - markersX[i], e.y - markersY[i])
                        if (d < bestD) {
                            bestD = d
                            best = i
                        }
                    }
                    selected = if (bestD <= maxOf(radius * 1.6f, ctx.dp(48).toFloat())) best else -1
                    invalidate()
                    return selected >= 0
                }
                MotionEvent.ACTION_MOVE -> {
                    if (selected >= 0) {
                        markersX[selected] = e.x.coerceIn(0f, width.toFloat())
                        markersY[selected] = e.y.coerceIn(0f, height.toFloat())
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    selected = -1
                    invalidate()
                    return true
                }
            }
            return true
        }
    }

    private fun build() {
        canvasView = MarkerView(ctx)
        root.addView(canvasView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Palette.PANEL, ctx.dp(16).toFloat(), Palette.BLUE_DIM, ctx.dp(1))
            setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(12))
        }
        bar.addView(ctx.text("Calibración", 13f, Palette.BLUE, true))
        bar.addView(ctx.text("Arrastra los 6 marcadores sobre los círculos grises.", 11f, Palette.MUTED))

        val sizeRow = ctx.row()
        sizeRow.margins(0, ctx.dp(8), 0, 0)
        radiusText = ctx.text("", 12f, Palette.TEXT, true)
        radiusText.weight(1f)
        sizeRow.addView(radiusText)
        sizeRow.addView(ctx.pill("−", false, 14f) { changeRadius(-2f) })
        sizeRow.addView(ctx.pill("+", false, 14f) { changeRadius(2f) }.margins(ctx.dp(6), 0, 0, 0))
        bar.addView(sizeRow)

        val actions = ctx.row()
        actions.margins(0, ctx.dp(8), 0, 0)
        val auto = ctx.pill("Auto", false, 12f) { autoDetect() }
        val save = ctx.pill("Guardar", true, 12f) { save() }
        val cancel = ctx.pill("Cancelar", false, 12f) { callbacks.onCancel() }
        val move = ctx.pill("⇅", false, 12f) { toggleBar() }
        auto.weight(1f)
        save.weight(1f)
        cancel.weight(1f)
        (auto.layoutParams as LinearLayout.LayoutParams).rightMargin = ctx.dp(4)
        (save.layoutParams as LinearLayout.LayoutParams).rightMargin = ctx.dp(4)
        (cancel.layoutParams as LinearLayout.LayoutParams).rightMargin = ctx.dp(4)
        actions.addView(auto)
        actions.addView(save)
        actions.addView(cancel)
        actions.addView(move)
        bar.addView(actions)

        placeBar()
        updateRadiusText()
    }

    private fun placeBar() {
        root.removeView(bar)
        val barLp = FrameLayout.LayoutParams(ctx.dp(300), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = (if (barOnTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
            topMargin = ctx.dp(36)
            bottomMargin = ctx.dp(36)
        }
        root.addView(bar, barLp)
    }

    private fun toggleBar() {
        barOnTop = !barOnTop
        placeBar()
    }

    private fun changeRadius(delta: Float) {
        radius = (radius + delta).coerceIn(4f, 150f)
        updateRadiusText()
        canvasView.invalidate()
    }

    private fun updateRadiusText() {
        radiusText.text = "Área de detección: ${radius.toInt()} px"
    }

    private fun defaultPositions() {
        val cols = 3
        for (i in 0 until DetectorConfig.CIRCLE_COUNT) {
            val c = i % cols
            val r = i / cols
            markersX[i] = screenW * (c + 1) / (cols + 1f)
            markersY[i] = screenH * (0.4f + 0.2f * r)
        }
    }

    private fun loadInitial(offsetX: Int, offsetY: Int) {
        if (!initial.calibrated) {
            defaultPositions()
            return
        }
        var sx = 1f
        var sy = 1f
        if (initial.calibW > 0 && initial.calibH > 0 && (initial.calibW != screenW || initial.calibH != screenH)) {
            val same = (initial.calibW >= initial.calibH) == (screenW >= screenH)
            if (!same) {
                defaultPositions()
                return
            }
            sx = screenW.toFloat() / initial.calibW
            sy = screenH.toFloat() / initial.calibH
        }
        for (i in 0 until DetectorConfig.CIRCLE_COUNT) {
            markersX[i] = initial.xs[i] * sx - offsetX
            markersY[i] = initial.ys[i] * sy - offsetY
        }
    }

    private fun autoDetect() {
        root.visibility = View.INVISIBLE
        handler.postDelayed({
            val snap = callbacks.grabSnapshot()
            root.visibility = View.VISIBLE
            if (snap == null) {
                Toast.makeText(ctx, "No hay captura disponible todavía", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }
            val found = CircleFinder.find(snap.pixels, snap.w, snap.h)
            if (found.isEmpty()) {
                Toast.makeText(ctx, "No encontré círculos grises. Colócalos a mano.", Toast.LENGTH_LONG).show()
                return@postDelayed
            }
            val loc = IntArray(2)
            canvasView.getLocationOnScreen(loc)
            for ((i, c) in found.withIndex()) {
                if (i >= DetectorConfig.CIRCLE_COUNT) break
                markersX[i] = (c.x * snap.step + snap.step / 2f) - loc[0]
                markersY[i] = (c.y * snap.step + snap.step / 2f) - loc[1]
            }
            val avgR = found.map { it.radius }.average().toFloat() * snap.step
            radius = (avgR * 0.7f).coerceIn(4f, 150f)
            updateRadiusText()
            canvasView.invalidate()
            val msg = if (found.size < DetectorConfig.CIRCLE_COUNT) {
                "Encontré ${found.size} de 6. Coloca el resto a mano."
            } else {
                "Encontré los 6 círculos. Revisa y pulsa Guardar."
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }, 250L)
    }

    private fun save() {
        val loc = IntArray(2)
        canvasView.getLocationOnScreen(loc)
        val xs = IntArray(DetectorConfig.CIRCLE_COUNT) { Math.round(markersX[it] + loc[0]) }
        val ys = IntArray(DetectorConfig.CIRCLE_COUNT) { Math.round(markersY[it] + loc[1]) }
        callbacks.onSave(
            initial.copy(
                xs = xs,
                ys = ys,
                radius = radius.toInt(),
                calibrated = true,
                calibW = screenW,
                calibH = screenH,
            ),
        )
    }

    fun show() {
        if (attached) return
        build()
        wm.addView(root, lp)
        attached = true
        canvasView.post {
            val loc = IntArray(2)
            canvasView.getLocationOnScreen(loc)
            loadInitial(loc[0], loc[1])
            canvasView.invalidate()
        }
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        if (attached) {
            try {
                wm.removeView(root)
            } catch (_: Exception) {
            }
            attached = false
        }
    }
}
