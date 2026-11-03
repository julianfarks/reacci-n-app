package com.instantreact.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.instantreact.core.Engine
import com.instantreact.service.Prefs
import kotlin.math.abs

/**
 * Ventana flotante (TYPE_APPLICATION_OVERLAY) movible y minimizable. Es compacta y sin fondo a
 * pantalla completa, así no tapa los círculos del minijuego; se puede arrastrar desde la cabecera.
 */
class FloatingPanel(
    private val ctx: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onStart()
        fun onStop()
        fun onOpenSettings()
        fun onCalibrate()
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private val root = FrameLayout(ctx)
    private lateinit var panel: LinearLayout
    private lateinit var icon: TextView
    private lateinit var dot: View
    private lateinit var stateText: TextView
    private lateinit var delayText: TextView
    private lateinit var lastText: TextView
    private lateinit var tapsText: TextView
    private lateinit var startBtn: TextView
    private lateinit var stopBtn: TextView

    private var pulse: ObjectAnimator? = null
    private var attached = false
    private var minimized = false
    private var lastRunning: Boolean? = null

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val pos = Prefs.panelPos(ctx)
        x = pos.first
        y = pos.second
    }

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 120L)
        }
    }

    private fun build() {
        panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Palette.PANEL, ctx.dp(16).toFloat(), Palette.BLUE_DIM, ctx.dp(1))
            setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(10))
            minimumWidth = ctx.dp(190)
        }

        // Cabecera (zona de arrastre)
        val header = ctx.row()
        dot = View(ctx).apply {
            background = roundedBg(Palette.MUTED, ctx.dp(5).toFloat())
            layoutParams = LinearLayout.LayoutParams(ctx.dp(10), ctx.dp(10)).apply { rightMargin = ctx.dp(8) }
        }
        val title = ctx.text("INSTANT REACT", 11f, Palette.BLUE, true).apply { letterSpacing = 0.08f }
        title.weight(1f)
        val minBtn = ctx.text("—", 16f, Palette.TEXT, true).apply {
            setPadding(ctx.dp(10), 0, ctx.dp(4), 0)
            setOnClickListener { setMinimized(true) }
        }
        header.addView(dot)
        header.addView(title)
        header.addView(minBtn)
        attachDrag(header, null)
        panel.addView(header)

        stateText = ctx.text("Detenido", 12f, Palette.TEXT, true).margins(0, ctx.dp(6), 0, ctx.dp(6)) as TextView
        panel.addView(stateText)

        val buttons = ctx.row()
        startBtn = smallButton("INICIAR", true) { callbacks.onStart() }
        stopBtn = smallButton("DETENER", false) { callbacks.onStop() }
        startBtn.weight(1f)
        stopBtn.weight(1f)
        (startBtn.layoutParams as LinearLayout.LayoutParams).rightMargin = ctx.dp(6)
        buttons.addView(startBtn)
        buttons.addView(stopBtn)
        panel.addView(buttons)

        delayText = ctx.text("", 11f, Palette.MUTED).margins(0, ctx.dp(8), 0, 0) as TextView
        lastText = ctx.text("", 11f, Palette.TEXT).margins(0, ctx.dp(2), 0, 0) as TextView
        tapsText = ctx.text("", 11f, Palette.TEXT).margins(0, ctx.dp(2), 0, ctx.dp(8)) as TextView
        panel.addView(delayText)
        panel.addView(lastText)
        panel.addView(tapsText)

        val bottom = ctx.row()
        val cfgBtn = smallButton("⚙ Config", false) { callbacks.onOpenSettings() }
        val calBtn = smallButton("◎ Calibrar", false) { callbacks.onCalibrate() }
        cfgBtn.weight(1f)
        calBtn.weight(1f)
        (cfgBtn.layoutParams as LinearLayout.LayoutParams).rightMargin = ctx.dp(6)
        bottom.addView(cfgBtn)
        bottom.addView(calBtn)
        panel.addView(bottom)

        icon = ctx.text("IR", 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(Palette.BLUE, ctx.dp(22).toFloat(), Color.WHITE, ctx.dp(1))
            layoutParams = FrameLayout.LayoutParams(ctx.dp(44), ctx.dp(44))
            visibility = View.GONE
        }
        attachDrag(icon) { setMinimized(false) }

        root.addView(panel)
        root.addView(icon)
    }

    private fun smallButton(label: String, filled: Boolean, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(if (filled) Color.WHITE else Palette.BLUE)
            background = if (filled) roundedBg(Palette.BLUE, ctx.dp(8).toFloat())
            else roundedBg(Color.TRANSPARENT, ctx.dp(8).toFloat(), Palette.BLUE, ctx.dp(1))
            setPadding(ctx.dp(6), ctx.dp(8), ctx.dp(6), ctx.dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

    private fun attachDrag(v: View, onTap: (() -> Unit)?) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        if (attached) wm.updateViewLayout(root, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) Prefs.savePanelPos(ctx, lp.x, lp.y) else onTap?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    private fun setMinimized(min: Boolean) {
        minimized = min
        panel.visibility = if (min) View.GONE else View.VISIBLE
        icon.visibility = if (min) View.VISIBLE else View.GONE
        if (attached) wm.updateViewLayout(root, lp)
    }

    fun show() {
        if (attached) return
        if (!::panel.isInitialized) build()
        root.alpha = 0f
        wm.addView(root, lp)
        attached = true
        root.animate().alpha(1f).setDuration(220).start()
        refresh()
        handler.post(ticker)
    }

    fun hide() {
        handler.removeCallbacks(ticker)
        pulse?.cancel()
        if (attached) {
            try {
                wm.removeView(root)
            } catch (_: Exception) {
            }
            attached = false
        }
    }

    val isShown: Boolean get() = attached

    fun refresh() {
        if (!::panel.isInitialized) return
        val cfg = Engine.config
        val s = Engine.stats.snapshot()
        val running = Engine.running
        stateText.text = PanelText.state(running, cfg.calibrated, Engine.orientationMismatch)
        delayText.text = PanelText.delay(cfg.delayMs)
        lastText.text = PanelText.last(s.lastReactionMs, s.lastSystemLatencyMs, s.taps)
        tapsText.text = PanelText.taps(s.taps)
        startBtn.alpha = if (running) 0.4f else 1f
        stopBtn.alpha = if (running) 1f else 0.4f
        if (lastRunning != running) {
            lastRunning = running
            dot.background = roundedBg(if (running) Palette.GREEN else Palette.MUTED, ctx.dp(5).toFloat())
            icon.background = roundedBg(
                if (running) Palette.GREEN else Palette.BLUE, ctx.dp(22).toFloat(), Color.WHITE, ctx.dp(1),
            )
            pulse?.cancel()
            dot.alpha = 1f
            if (running) {
                pulse = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f).apply {
                    duration = 650
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
    }
}
