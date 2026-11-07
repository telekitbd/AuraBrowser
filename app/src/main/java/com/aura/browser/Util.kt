package com.aura.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

val ENGINES = listOf("Google", "Bing", "DuckDuckGo", "Brave", "Yahoo")

fun sha256(s: String): String {
    val d = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
    return d.joinToString("") { "%02x".format(it) }
}

/** Runs [onOk] right away when no PIN is set, otherwise only after the right PIN is typed. */
fun Context.askPin(onOk: () -> Unit) {
    val saved = Store(this).getStr("pin", "")
    if (saved.isEmpty()) {
        onOk()
        return
    }
    val e = EditText(this)
    e.hint = "PIN"
    e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    val box = LinearLayout(this)
    box.setPadding(dp(20), dp(8), dp(20), 0)
    box.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    AlertDialog.Builder(this)
        .setTitle("Enter PIN")
        .setView(box)
        .setPositiveButton("OK") { _, _ ->
            if (sha256(e.text.toString()) == saved) onOk() else toast("Wrong PIN")
        }
        .setNegativeButton("Cancel", null)
        .show()
}

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

fun Context.iconView(res: Int, sizeDp: Int = 24, color: Int = Color.WHITE): ImageView {
    val v = ImageView(this)
    v.setImageResource(res)
    v.setColorFilter(color)
    v.scaleType = ImageView.ScaleType.FIT_CENTER
    v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    return v
}

fun Context.iconButton(res: Int, wDp: Int, hDp: Int, iconDp: Int = 24, color: Int = Color.WHITE): ImageView {
    val v = ImageView(this)
    v.setImageResource(res)
    v.setColorFilter(color)
    v.scaleType = ImageView.ScaleType.FIT_CENTER
    v.setPadding(dp((wDp - iconDp) / 2), dp((hDp - iconDp) / 2), dp((wDp - iconDp) / 2), dp((hDp - iconDp) / 2))
    v.layoutParams = LinearLayout.LayoutParams(dp(wDp), dp(hDp))
    return v
}

fun Context.siteIcon(title: String, url: String, sizeDp: Int): TextView {
    val host = hostOf(url)
    val fav = FaviconCache.get(this, host) ?: return letterIcon(title.ifBlank { host }, sizeDp)
    val px = dp(sizeDp)
    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF2A2A2A.toInt()
    c.drawCircle(px / 2f, px / 2f, px / 2f, p)
    val inner = (px * 0.56f).toInt()
    val scaled = Bitmap.createScaledBitmap(fav, inner, inner, true)
    c.drawBitmap(scaled, (px - inner) / 2f, (px - inner) / 2f, null)
    val v = TextView(this)
    v.background = BitmapDrawable(resources, out)
    v.layoutParams = LinearLayout.LayoutParams(px, px)
    return v
}

object FaviconCache {
    private val mem = ConcurrentHashMap<String, Bitmap>()
    private val tried = Collections.synchronizedSet(HashSet<String>())

    private fun file(ctx: Context, host: String): File {
        val d = File(ctx.filesDir, "fav")
        d.mkdirs()
        return File(d, host.replace("/", "_") + ".png")
    }

    fun get(ctx: Context, host: String): Bitmap? {
        if (host.isEmpty()) return null
        mem[host]?.let { return it }
        val f = file(ctx, host)
        if (f.exists()) {
            val b = BitmapFactory.decodeFile(f.path)
            if (b != null) {
                mem[host] = b
                return b
            }
        }
        return null
    }

    fun save(ctx: Context, host: String, bmp: Bitmap) {
        if (host.isEmpty()) return
        mem[host] = bmp
        try {
            file(ctx, host).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
        }
    }

    fun ensure(ctx: Context, host: String, onDone: () -> Unit) {
        if (host.isEmpty() || get(ctx, host) != null || !tried.add(host)) return
        val app = ctx.applicationContext
        Thread {
            try {
                val conn = URL("https://www.google.com/s2/favicons?domain=$host&sz=64")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val b = BitmapFactory.decodeStream(conn.inputStream)
                if (b != null) {
                    save(app, host, b)
                    Handler(Looper.getMainLooper()).post { onDone() }
                }
            } catch (_: Exception) {
            }
        }.start()
    }
}

fun cleanDomain(s: String): String {
    var t = s.trim().lowercase().removePrefix("http://").removePrefix("https://").removePrefix("www.")
    t = t.substringBefore('/').substringBefore('?').substringBefore(':')
    return t
}

/** One-time warning before the free VPN servers are used. */
fun Context.vpnWarn(store: Store, onOk: () -> Unit) {
    if (store.getBool("vpn_warned", false)) {
        onOk()
        return
    }
    AlertDialog.Builder(this)
        .setTitle("Before you use free servers")
        .setMessage(
            "Free servers are run by strangers. They can see which sites you open (not what you type on " +
                "https:// sites), they can be slow, and they stop working often.\n\n" +
                "Do not log in to your bank or other sensitive accounts while the VPN is on. " +
                "This VPN only covers pages opened inside Aura, not downloads or other apps."
        )
        .setPositiveButton("I understand") { _, _ ->
            store.setBool("vpn_warned", true)
            onOk()
        }
        .setNegativeButton("Cancel", null)
        .show()
}
