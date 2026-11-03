package com.instantreact.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Paleta: tema oscuro con detalles azules. */
object Palette {
    const val BG = 0xFF0B0F14.toInt()
    const val CARD = 0xFF131A22.toInt()
    const val CARD_STROKE = 0xFF1F2A36.toInt()
    const val BLUE = 0xFF2F8BFF.toInt()
    const val BLUE_DIM = 0xFF1B4F94.toInt()
    const val TEXT = 0xFFE8EEF5.toInt()
    const val MUTED = 0xFF8A97A6.toInt()
    const val GREEN = 0xFF3DDC84.toInt()
    const val RED = 0xFFFF5C5C.toInt()
    const val ORANGE = 0xFFFFB84D.toInt()
    const val PANEL = 0xE60E1319.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun roundedBg(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

fun Context.text(
    value: String,
    sizeSp: Float = 14f,
    color: Int = Palette.TEXT,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    text = value
    textSize = sizeSp
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

/** Botón plano con ripple. filled = relleno azul; si no, contorno azul. */
fun Context.pill(label: String, filled: Boolean = true, sizeSp: Float = 14f, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        textSize = sizeSp
        isAllCaps = false
        setTextColor(if (filled) Color.WHITE else Palette.BLUE)
        stateListAnimator = null
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        val r = dp(10).toFloat()
        val base = if (filled) roundedBg(Palette.BLUE, r) else roundedBg(Color.TRANSPARENT, r, Palette.BLUE, dp(1))
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, null)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
    }

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBg(Palette.CARD, dp(16).toFloat(), Palette.CARD_STROKE, dp(1))
    setPadding(dp(16), dp(14), dp(16), dp(16))
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(12) }
}

fun Context.row(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

fun View.margins(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): View {
    val lp = (layoutParams as? LinearLayout.LayoutParams)
        ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.setMargins(l, t, r, b)
    layoutParams = lp
    return this
}

fun View.weight(w: Float = 1f): View {
    val old = layoutParams as? LinearLayout.LayoutParams
    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
    if (old != null) lp.setMargins(old.leftMargin, old.topMargin, old.rightMargin, old.bottomMargin)
    layoutParams = lp
    return this
}
