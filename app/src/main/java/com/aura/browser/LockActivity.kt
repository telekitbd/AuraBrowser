package com.aura.browser

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** Full-screen lock: PIN pad plus optional fingerprint. Shown on top of the browser. */
class LockActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var dots: TextView
    private lateinit var msg: TextView
    private val entered = StringBuilder()
    private var pinLen = 4
    private var cancel: CancellationSignal? = null
    private var bioAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        pinLen = store.getInt("lockpinlen", 4).coerceIn(4, 8)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(dp(24), dp(56), dp(24), dp(24))

        root.addView(iconView(R.drawable.ic_lock, 44, 0xFF9FA8FF.toInt()))

        val title = tv("Aura Browser", 22f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        title.setPadding(0, dp(16), 0, 0)
        root.addView(title)

        msg = tv("Enter your PIN", 14f, 0xFF999999.toInt())
        msg.gravity = Gravity.CENTER
        msg.setPadding(0, dp(8), 0, dp(18))
        root.addView(msg)

        dots = tv("", 26f)
        dots.gravity = Gravity.CENTER
        dots.letterSpacing = 0.25f
        root.addView(dots)
        showDots()

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val pad = LinearLayout(this)
        pad.orientation = LinearLayout.VERTICAL
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("bio", "0", "del")
        )
        for (r in rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in r) row.addView(key(k))
            pad.addView(row)
        }
        root.addView(pad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (!bioAsked) {
            bioAsked = true
            window.decorView.post { askBio() }
        }
    }

    override fun onDestroy() {
        cancel?.cancel()
        super.onDestroy()
    }

    private fun key(k: String): View {
        val cell = LinearLayout(this)
        cell.gravity = Gravity.CENTER
        cell.layoutParams = LinearLayout.LayoutParams(dp(88), dp(80))
        if (k == "bio") {
            if (bioAvailable()) {
                val ic = iconButton(R.drawable.ic_fingerprint, 72, 72, 34, 0xFF9FA8FF.toInt())
                ic.setOnClickListener { askBio() }
                cell.addView(ic)
            }
            return cell
        }
        if (k == "del") {
            val t = tv("Del", 16f, 0xFFBBBBBB.toInt())
            t.gravity = Gravity.CENTER
            t.layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            t.setOnClickListener { backspace() }
            cell.addView(t)
            return cell
        }
        val t = tv(k, 26f)
        t.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xFF1E1E1E.toInt())
        t.background = bg
        t.layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
        t.setOnClickListener { press(k) }
        cell.addView(t)
        return cell
    }

    private fun showDots() {
        val sb = StringBuilder()
        for (i in 0 until pinLen) {
            if (i > 0) sb.append(' ')
            sb.append(if (i < entered.length) '\u25CF' else '\u25CB')
        }
        dots.text = sb.toString()
    }

    private fun backspace() {
        if (entered.isNotEmpty()) entered.setLength(entered.length - 1)
        showDots()
    }

    private fun press(d: String) {
        if (entered.length >= 8) return
        entered.append(d)
        msg.text = "Enter your PIN"
        msg.setTextColor(0xFF999999.toInt())
        showDots()
        val saved = store.getStr("lockpin", "")
        if (AppLock.hash(entered.toString()) == saved) {
            unlock()
        } else if (entered.length >= pinLen) {
            msg.text = "Wrong PIN, try again"
            msg.setTextColor(0xFFFF6B6B.toInt())
            entered.setLength(0)
            showDots()
        }
    }

    private fun unlock() {
        AppLock.locked = false
        cancel?.cancel()
        finish()
    }

    @Suppress("DEPRECATION")
    private fun bioAvailable(): Boolean {
        if (!store.getBool("lockbio", false)) return false
        return try {
            val bm = getSystemService(BiometricManager::class.java) ?: return false
            val r = if (Build.VERSION.SDK_INT >= 30) {
                bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            } else {
                bm.canAuthenticate()
            }
            r == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    private fun askBio() {
        if (!bioAvailable()) return
        try {
            cancel?.cancel()
            val cs = CancellationSignal()
            cancel = cs
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("Unlock Aura Browser")
                .setNegativeButton("Use PIN", mainExecutor) { _, _ -> }
                .build()
            prompt.authenticate(cs, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                }
            })
        } catch (_: Exception) {
        }
    }
}
