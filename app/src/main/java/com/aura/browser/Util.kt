package com.aura.browser

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun hostOf(url: String): String = try {
    Uri.parse(url).host ?: url
} catch (e: Exception) {
    url
}

fun normalizeUrl(s: String): String {
    val t = s.trim()
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
}

fun Context.tv(text: String, sp: Float = 14f, color: Int = Color.WHITE): TextView {
    val v = TextView(this)
    v.text = text
    v.textSize = sp
    v.setTextColor(color)
    return v
}

fun Context.letterIcon(text: String, sizeDp: Int, forced: String? = null): TextView {
    val colors = intArrayOf(
        0xFFE53935.toInt(), 0xFF8E24AA.toInt(), 0xFF3949AB.toInt(),
        0xFF00897B.toInt(), 0xFFF4511E.toInt(), 0xFF6D4C41.toInt(),
        0xFF039BE5.toInt(), 0xFF7CB342.toInt()
    )
    val ch = forced
        ?: (text.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•")
    val bg = GradientDrawable()
    bg.shape = GradientDrawable.OVAL
    bg.setColor(colors[(text.lowercase().hashCode() and 0x7fffffff) % colors.size])
    val v = TextView(this)
    v.text = ch
    v.setTextColor(Color.WHITE)
    v.textSize = sizeDp * 0.4f
    v.gravity = Gravity.CENTER
    v.typeface = Typeface.DEFAULT_BOLD
    v.background = bg
    v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    return v
}

fun Context.twoFieldDialog(
    title: String,
    hint1: String,
    hint2: String,
    v1: String,
    v2: String,
    onOk: (String, String) -> Unit
) {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(dp(20), dp(8), dp(20), 0)
    val e1 = EditText(this)
    e1.hint = hint1
    e1.setText(v1)
    e1.maxLines = 1
    val e2 = EditText(this)
    e2.hint = hint2
    e2.setText(v2)
    e2.maxLines = 1
    e2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    box.addView(e1)
    box.addView(e2)
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton("OK") { _, _ ->
            onOk(e1.text.toString().trim(), e2.text.toString().trim())
        }
        .setNegativeButton("Cancel", null)
        .show()
}
