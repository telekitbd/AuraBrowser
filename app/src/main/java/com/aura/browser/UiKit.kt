package com.aura.browser

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/** Small set of building blocks so every sheet in Aura looks the same: cards, switches, stat tiles. */
object UiColors {
    val ACCENT = 0xFF7C8CFF.toInt()
    val GREEN = 0xFF4CD08A.toInt()
    val RED = 0xFFFF6B6B.toInt()
    val GREY = 0xFF8A8A8A.toInt()
    val CARD = 0xFF2A2A2E.toInt()
    val CARD_SEL = 0xFF30345A.toInt()
    val TEXT2 = 0xFFA0A0A8.toInt()
}

fun Context.uiRound(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = dp(radiusDp).toFloat()
    if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor)
    return g
}

fun Context.uiRipple(bg: Drawable): Drawable =
    RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null)

fun Context.uiSwitch(checked: Boolean, accent: Int, onChange: (Boolean) -> Unit): SwitchCompat {
    val sw = SwitchCompat(this)
    sw.isChecked = checked
    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
    sw.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, 0xFFB0B0B0.toInt()))
    sw.trackTintList = ColorStateList(states, intArrayOf(accent, 0xFF4A4A4E.toInt()))
    sw.setOnCheckedChangeListener { _, v -> onChange(v) }
    return sw
}

class UiHead(val view: LinearLayout, val title: TextView, val sub: TextView, val badge: LinearLayout, val icon: ImageView)

/** Big header: coloured round icon, title, status line and an optional switch on the right. */
fun Context.uiHeader(
    iconRes: Int, accent: Int, title: String, subtitle: String,
    switchOn: Boolean? = null, onSwitch: ((Boolean) -> Unit)? = null
): UiHead {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(dp(4), dp(2), dp(4), dp(14))

    val badge = LinearLayout(this)
    badge.gravity = Gravity.CENTER
    badge.background = uiRound(withAlpha(accent, 0x2E), 18)
    val icon = iconView(iconRes, 28, accent)
    badge.addView(icon)
    row.addView(badge, LinearLayout.LayoutParams(dp(56), dp(56)))

    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    val t = tv(title, 21f)
    t.typeface = Typeface.DEFAULT_BOLD
    val s = tv(subtitle, 13.5f, accent)
    col.addView(t)
    col.addView(s)
    row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(14) })

    if (switchOn != null && onSwitch != null) {
        row.addView(uiSwitch(switchOn, accent, onSwitch))
    }
    return UiHead(row, t, s, badge, icon)
}

fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)

fun UiHead.setState(accent: Int, subtitle: String) {
    sub.text = subtitle
    sub.setTextColor(accent)
    icon.setColorFilter(accent)
    badge.background = badge.context.uiRound(withAlpha(accent, 0x2E), 18)
}

/** A row of equal tiles: big number on top, small caption below. Returns the value views so they can be updated. */
fun Context.uiStats(items: List<Pair<String, String>>, accent: Int): Pair<LinearLayout, List<TextView>> {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    val values = mutableListOf<TextView>()
    for ((i, it) in items.withIndex()) {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.setPadding(dp(8), dp(14), dp(8), dp(14))
        tile.background = uiRound(UiColors.CARD, 16)
        val v = tv(it.first, 22f, accent)
        v.typeface = Typeface.DEFAULT_BOLD
        v.gravity = Gravity.CENTER
        val c = tv(it.second, 12f, UiColors.TEXT2)
        c.gravity = Gravity.CENTER
        tile.addView(v)
        tile.addView(c)
        values.add(v)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (i > 0) lp.leftMargin = dp(8)
        row.addView(tile, lp)
    }
    return Pair(row, values)
}

fun Context.uiLabel(text: String): TextView {
    val l = tv(text.uppercase(), 11.5f, UiColors.TEXT2)
    l.typeface = Typeface.DEFAULT_BOLD
    l.letterSpacing = 0.08f
    l.setPadding(dp(6), dp(18), dp(6), dp(8))
    return l
}

/** Tappable card row. [trailing] can be a switch, a chevron or nothing. */
fun Context.uiRow(
    iconRes: Int, title: String, subtitle: String? = null, accent: Int = UiColors.ACCENT,
    selected: Boolean = false, trailing: View? = null, onClick: (() -> Unit)? = null
): LinearLayout {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(dp(14), dp(12), dp(14), dp(12))
    val bg = if (selected) uiRound(UiColors.CARD_SEL, 16, accent, 2) else uiRound(UiColors.CARD, 16)
    row.background = if (onClick != null) uiRipple(bg) else bg

    val ib = LinearLayout(this)
    ib.gravity = Gravity.CENTER
    ib.background = uiRound(withAlpha(accent, 0x26), 12)
    ib.addView(iconView(iconRes, 22, accent))
    row.addView(ib, LinearLayout.LayoutParams(dp(42), dp(42)))

    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    val t = tv(title, 16f)
    col.addView(t)
    if (!subtitle.isNullOrEmpty()) {
        val s = tv(subtitle, 12.5f, UiColors.TEXT2)
        s.setPadding(0, dp(2), 0, 0)
        col.addView(s)
    }
    row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(14) })
    if (trailing != null) row.addView(trailing)
    if (onClick != null) row.setOnClickListener { onClick() }
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.topMargin = dp(8)
    row.layoutParams = lp
    return row
}

fun Context.uiChevron(): ImageView = iconView(R.drawable.ic_arrow_forward, 20, UiColors.TEXT2)

fun Context.uiNote(text: String): TextView {
    val n = tv(text, 12f, UiColors.TEXT2)
    n.setPadding(dp(6), dp(14), dp(6), 0)
    return n
}
