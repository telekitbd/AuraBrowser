package com.aura.browser

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * One screen to manage the simple lists:
 *  hide         - elements the user hid with the manual ad blocker
 *  addomains    - extra ad domains blocked by the user
 *  adallow      - sites where the ad blocker is switched off
 *  adultdomains - extra sites added to the adult block list
 */
class ManageActivity : AppCompatActivity() {

    private lateinit var store: Store
    private var mode = "hide"
    private var key = "hide"
    private var rows: List<String> = emptyList()

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var adapter: RowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        mode = intent.getStringExtra("mode") ?: "hide"
        key = when (mode) {
            "addomains" -> "addom"
            "adallow" -> "adallow"
            "adultdomains" -> "adultdom"
            else -> "hide"
        }
        val titleText = when (mode) {
            "addomains" -> "Blocked ad domains"
            "adallow" -> "Ad blocker is off on"
            "adultdomains" -> "Blocked sites"
            else -> "Hidden elements"
        }
        val canAdd = mode != "hide"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), 0, dp(4), 0)
        val back = iconButton(R.drawable.ic_arrow_back, 48, 56)
        back.setOnClickListener { finish() }
        val title = tv(titleText, 20f)
        title.setPadding(dp(8), 0, 0, 0)
        val btnAdd = iconButton(R.drawable.ic_add, 48, 56)
        val btnClear = iconButton(R.drawable.ic_delete, 48, 56)
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (canAdd) bar.addView(btnAdd, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(btnClear, LinearLayout.LayoutParams(dp(48), dp(56)))

        listView = ListView(this)
        listView.divider = ColorDrawable(0xFF333333.toInt())
        listView.dividerHeight = 1
        val bg = GradientDrawable()
        bg.setColor(0xFF1E1E1E.toInt())
        bg.cornerRadius = dp(24).toFloat()
        listView.background = bg
        listView.clipToOutline = true
        adapter = RowAdapter()
        listView.adapter = adapter

        emptyView = tv("Nothing here yet", 16f, 0xFF888888.toInt())
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(0, dp(40), 0, 0)
        emptyView.visibility = View.GONE

        root.addView(bar)
        root.addView(emptyView)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        lp.setMargins(dp(10), dp(6), dp(10), dp(10))
        root.addView(listView, lp)
        setContentView(root)

        btnAdd.setOnClickListener { addDialog() }
        btnClear.setOnClickListener {
            if (rows.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setMessage("Remove everything in this list?")
                .setPositiveButton("Remove all") { _, _ ->
                    if (mode == "adultdomains") askPin { clearAll() } else clearAll()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        refresh()
    }

    private fun clearAll() {
        for (r in store.strings(key)) store.removeString(key, r)
        refresh()
    }

    private fun refresh() {
        rows = store.strings(key)
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addDialog() {
        val e = EditText(this)
        e.hint = "example.com"
        e.maxLines = 1
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        val box = LinearLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("Add domain")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val d = cleanDomain(e.text.toString())
                if (d.isEmpty() || !d.contains(".")) {
                    toast("Enter a domain like example.com")
                } else {
                    store.addString(key, d)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun remove(raw: String) {
        if (mode == "adultdomains") askPin {
            store.removeString(key, raw)
            refresh()
        } else {
            store.removeString(key, raw)
            refresh()
        }
    }

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val raw = rows[position]
            val row = LinearLayout(this@ManageActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(16), dp(10), dp(4), dp(10))

            val col = LinearLayout(this@ManageActivity)
            col.orientation = LinearLayout.VERTICAL
            val main: String
            val sub: String
            if (mode == "hide") {
                main = raw.substringAfter('\n')
                sub = raw.substringBefore('\n')
            } else {
                main = raw
                sub = ""
            }
            val t = tv(main, 15f)
            t.maxLines = 2
            t.ellipsize = TextUtils.TruncateAt.MIDDLE
            col.addView(t)
            if (sub.isNotEmpty()) {
                val s = tv(sub, 13f, 0xFF999999.toInt())
                s.maxLines = 1
                col.addView(s)
            }
            val del = iconButton(R.drawable.ic_close, 44, 44, 20)
            del.setOnClickListener { remove(raw) }
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(del)
            return row
        }
    }
}
