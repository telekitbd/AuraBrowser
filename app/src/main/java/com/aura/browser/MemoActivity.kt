package com.aura.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
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
import androidx.core.widget.doAfterTextChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoActivity : AppCompatActivity() {

    private lateinit var store: Store
    private var shown: List<Memo> = emptyList()
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var searchBox: EditText
    private lateinit var adapter: BaseAdapter
    private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(6), 0, dp(6), 0)
        val back = iconButton(R.drawable.ic_arrow_back, 48, 56)
        back.setOnClickListener { finish() }
        bar.addView(back)
        bar.addView(tv("Memo", 20f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val add = iconButton(R.drawable.ic_add, 48, 56)
        add.setOnClickListener { editor(null, "", "", "") }
        bar.addView(add)
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        searchBox = EditText(this)
        searchBox.hint = "Search memos"
        searchBox.setHintTextColor(0xFF777777.toInt())
        searchBox.setTextColor(Color.WHITE)
        searchBox.maxLines = 1
        searchBox.inputType = InputType.TYPE_CLASS_TEXT
        searchBox.doAfterTextChanged { reload() }
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sp.setMargins(dp(14), 0, dp(14), dp(6))
        root.addView(searchBox, sp)

        emptyView = tv("No memos yet.\nTap + to write one.", 15f, 0xFF888888.toInt())
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(0, dp(60), 0, 0)
        root.addView(emptyView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        listView = ListView(this)
        listView.divider = null
        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = shown[position].id
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val m = shown[position]
                val box = LinearLayout(this@MemoActivity)
                box.orientation = LinearLayout.VERTICAL
                box.setPadding(dp(18), dp(12), dp(18), dp(12))
                val t = tv(m.title.ifBlank { "Untitled" }, 16f)
                t.maxLines = 1
                t.ellipsize = TextUtils.TruncateAt.END
                t.typeface = android.graphics.Typeface.DEFAULT_BOLD
                box.addView(t)
                if (m.body.isNotBlank()) {
                    val b = tv(m.body, 13f, 0xFFBBBBBB.toInt())
                    b.maxLines = 2
                    b.ellipsize = TextUtils.TruncateAt.END
                    b.setPadding(0, dp(3), 0, 0)
                    box.addView(b)
                }
                var meta = fmt.format(Date(m.time))
                if (m.url.isNotBlank()) meta += "  •  " + hostOf(m.url)
                val mt = tv(meta, 11f, 0xFF7C8CFF.toInt())
                mt.setPadding(0, dp(4), 0, 0)
                box.addView(mt)
                return box
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val m = shown[pos]
            editor(m, m.title, m.body, m.url)
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            actions(shown[pos])
            true
        }
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        reload()

        if (intent.hasExtra("new")) {
            editor(
                null,
                intent.getStringExtra("newTitle") ?: "",
                intent.getStringExtra("newText") ?: "",
                intent.getStringExtra("newUrl") ?: ""
            )
        }
    }

    private fun reload() {
        val q = searchBox.text.toString().trim().lowercase()
        val all = store.getMemos()
        shown = if (q.isEmpty()) all else all.filter {
            it.title.lowercase().contains(q) || it.body.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun editor(existing: Memo?, title: String, body: String, url: String) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val t = EditText(this)
        t.hint = "Title"
        t.setText(title)
        t.maxLines = 1
        val b = EditText(this)
        b.hint = "Write your note…"
        b.setText(body)
        b.minLines = 6
        b.gravity = Gravity.TOP
        b.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val u = EditText(this)
        u.hint = "Page link (optional)"
        u.setText(url)
        u.maxLines = 1
        u.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        box.addView(t)
        box.addView(b)
        box.addView(u)
        val sv = android.widget.ScrollView(this)
        sv.addView(box)
        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New memo" else "Edit memo")
            .setView(sv)
            .setPositiveButton("Save") { _, _ ->
                val tt = t.text.toString().trim()
                val bb = b.text.toString().trim()
                val uu = u.text.toString().trim()
                if (tt.isEmpty() && bb.isEmpty() && uu.isEmpty()) {
                    toast("Empty memo not saved")
                } else {
                    val id = existing?.id ?: System.currentTimeMillis()
                    store.saveMemo(Memo(id, tt.ifEmpty { bb.lineSequence().firstOrNull()?.take(40) ?: "" }, bb, uu, 0L))
                    toast("Memo saved")
                    reload()
                }
            }
            .setNegativeButton("Cancel", null)
        if (existing != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                store.removeMemo(existing.id)
                reload()
            }
        }
        builder.show()
    }

    private fun actions(m: Memo) {
        val labels = mutableListOf("Share", "Copy text")
        if (m.url.isNotBlank()) labels.add("Open page")
        labels.add("Delete")
        AlertDialog.Builder(this)
            .setTitle(m.title.ifBlank { "Memo" })
            .setItems(labels.toTypedArray()) { _, w ->
                when (labels[w]) {
                    "Share" -> {
                        val i = Intent(Intent.ACTION_SEND)
                        i.type = "text/plain"
                        i.putExtra(Intent.EXTRA_TEXT, memoText(m))
                        startActivity(Intent.createChooser(i, "Share memo"))
                    }
                    "Copy text" -> {
                        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("memo", memoText(m)))
                        toast("Copied")
                    }
                    "Open page" -> {
                        setResult(RESULT_OK, Intent().putExtra("url", m.url).putExtra("newTab", true))
                        finish()
                    }
                    "Delete" -> {
                        store.removeMemo(m.id)
                        reload()
                    }
                }
            }
            .show()
    }

    private fun memoText(m: Memo): String {
        val sb = StringBuilder()
        if (m.title.isNotBlank()) sb.append(m.title).append("\n\n")
        if (m.body.isNotBlank()) sb.append(m.body).append("\n")
        if (m.url.isNotBlank()) sb.append("\n").append(m.url)
        return sb.toString().trim()
    }
}
