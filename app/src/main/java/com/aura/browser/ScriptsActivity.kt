package com.aura.browser

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.net.HttpURLConnection
import java.net.URL

class ScriptsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), 0, dp(4), 0)
        val back = iconButton(R.drawable.ic_arrow_back, 48, 56)
        back.setOnClickListener { finish() }
        val title = tv("User scripts", 22f)
        title.setPadding(dp(8), 0, 0, 0)
        val add = iconButton(R.drawable.ic_add, 48, 56)
        add.setOnClickListener { addMenu() }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(add, LinearLayout.LayoutParams(dp(48), dp(56)))

        val sv = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(10), 0, dp(10), dp(24))
        sv.addView(box)

        root.addView(bar)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        render()
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(0xFF1E1E1E.toInt())
        bg.cornerRadius = dp(20).toFloat()
        c.background = bg
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(8), 0, 0)
        box.addView(c, lp)
        return c
    }

    private fun row(c: LinearLayout, name: String, sub: String, on: Boolean, onClick: () -> Unit, onToggle: () -> Unit,
                    onLong: (() -> Unit)?) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(16), dp(13), dp(8), dp(13))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(name, 16f))
        if (sub.isNotEmpty()) {
            val s = tv(sub, 13f, 0xFF999999.toInt())
            s.maxLines = 2
            s.setPadding(0, dp(2), 0, 0)
            col.addView(s)
        }
        r.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(this)
        sw.isChecked = on
        sw.setOnClickListener { onToggle() }
        r.addView(sw)
        r.setOnClickListener { onClick() }
        if (onLong != null) {
            r.setOnLongClickListener {
                onLong()
                true
            }
        }
        c.addView(r)
    }

    private fun render() {
        box.removeAllViews()

        val master = store.getBool("userscripts_on", true)
        val c1 = card()
        row(c1, "Run user scripts", "Master switch for all scripts", master, {
            store.setBool("userscripts_on", !master)
            render()
        }, {
            store.setBool("userscripts_on", !master)
            render()
        }, null)

        val info = tv(
            "Scripts run after a page finishes loading, only on the sites you list. " +
                "Tap a script to edit it, long press to delete. Paste a Tampermonkey script and its " +
                "@name and @match lines are read for you. GM_addStyle, GM_getValue, GM_setValue and GM_log work.",
            12f, 0xFF888888.toInt()
        )
        info.setPadding(dp(12), dp(12), dp(12), dp(4))
        box.addView(info)

        val list = UserScripts.list(store)
        if (list.isEmpty()) {
            val e = tv("No scripts yet. Tap + to add one.", 14f, 0xFF777777.toInt())
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(40), 0, 0)
            box.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        val c2 = card()
        for (s in list) {
            row(c2, s.name.ifBlank { "Script" }, s.match.replace("\n", ", "), s.on, {
                editDialog(s)
            }, {
                s.on = !s.on
                UserScripts.save(store, s)
                render()
            }, {
                confirmDelete(s)
            })
        }
    }

    private fun confirmDelete(s: UScript) {
        AlertDialog.Builder(this)
            .setTitle("Delete script?")
            .setMessage(s.name)
            .setPositiveButton("Delete") { _, _ ->
                UserScripts.delete(store, s.id)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addMenu() {
        AlertDialog.Builder(this)
            .setTitle("Add script")
            .setItems(arrayOf("Write or paste a script", "Import from a link")) { _, w ->
                if (w == 0) editDialog(null) else importDialog()
            }
            .show()
    }

    private fun importDialog() {
        val e = EditText(this)
        e.hint = "https://.../script.user.js"
        e.maxLines = 1
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        val b = LinearLayout(this)
        b.setPadding(dp(20), dp(8), dp(20), 0)
        b.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("Import from a link")
            .setView(b)
            .setPositiveButton("Download") { _, _ ->
                val u = e.text.toString().trim()
                if (u.startsWith("http://") || u.startsWith("https://")) fetch(u) else toast("Enter a full http(s) link")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetch(u: String) {
        toast("Downloading...")
        Thread {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                runOnUiThread {
                    if (text.isBlank() || text.length > 500000) {
                        toast("That file is empty or too big")
                    } else {
                        val h = UserScripts.parseHeader(text)
                        editDialog(UScript(0L, h.first.ifBlank { hostOf(u) }, h.second, text, true))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Could not download the script") }
            }
        }.start()
    }

    private fun editDialog(s: UScript?) {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(8), dp(20), 0)

        val name = EditText(this)
        name.hint = "Name"
        name.maxLines = 1
        name.setText(s?.name ?: "")

        val match = EditText(this)
        match.hint = "Sites: example.com, or *://*.example.com/*  (one per line, * = every site)"
        match.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI
        match.minLines = 2
        match.gravity = Gravity.TOP
        match.setText(s?.match ?: "")

        val code = EditText(this)
        code.hint = "JavaScript code"
        code.typeface = Typeface.MONOSPACE
        code.textSize = 12f
        code.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        code.minLines = 8
        code.gravity = Gravity.TOP
        code.setText(s?.code ?: "")

        col.addView(name)
        col.addView(match)
        col.addView(code)
        val sc = ScrollView(this)
        sc.addView(col)

        val existing = s != null && s.id != 0L
        val b = AlertDialog.Builder(this)
            .setTitle(if (existing) "Edit script" else "New script")
            .setView(sc)
            .setPositiveButton("Save") { _, _ ->
                commit(s, name.text.toString().trim(), match.text.toString().trim(), code.text.toString())
            }
            .setNegativeButton("Cancel", null)
        if (existing && s != null) {
            b.setNeutralButton("Delete") { _, _ -> confirmDelete(s) }
        }
        b.show()
    }

    private fun commit(old: UScript?, nameIn: String, matchIn: String, code: String) {
        if (code.isBlank()) {
            toast("The script is empty")
            return
        }
        val h = UserScripts.parseHeader(code)
        val n = nameIn.ifBlank { h.first.ifBlank { "Script" } }
        var m = matchIn.ifBlank { h.second }
        if (m.isBlank()) {
            m = "*"
            toast("No site given - the script runs on every site")
        }
        val id = if (old != null && old.id != 0L) old.id else System.currentTimeMillis()
        UserScripts.save(store, UScript(id, n, m, code, old?.on ?: true))
        render()
    }
}
