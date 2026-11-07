package com.aura.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ItemsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private var mode = "bookmark"
    private var shown: List<Item> = emptyList()
    private var sortBy = 0
    private var detail = true
    private var singleLine = true

    private lateinit var listView: ListView
    private lateinit var searchBox: EditText
    private lateinit var emptyView: TextView
    private lateinit var adapter: ItemsAdapter

    private val dateFmt = SimpleDateFormat("MM/dd/yyyy", Locale.US)

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
            if (uri != null) doExport(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doImport(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        mode = intent.getStringExtra("mode") ?: "bookmark"
        val isBm = mode == "bookmark"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), 0, dp(4), 0)

        val back = tv("←", 24f)
        back.gravity = Gravity.CENTER
        back.setOnClickListener { finish() }
        val title = tv(if (isBm) "Bookmark" else "History", 22f)
        title.setPadding(dp(8), 0, 0, 0)
        val btnAdd = tv("＋", 26f)
        btnAdd.gravity = Gravity.CENTER
        val btnSearch = tv("🔍", 20f)
        btnSearch.gravity = Gravity.CENTER
        val btnMore = tv("⋮", 26f)
        btnMore.gravity = Gravity.CENTER

        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (isBm) bar.addView(btnAdd, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(btnSearch, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(btnMore, LinearLayout.LayoutParams(dp(48), dp(56)))

        searchBox = EditText(this)
        searchBox.hint = "Search"
        searchBox.setTextColor(Color.WHITE)
        searchBox.setHintTextColor(0xFF777777.toInt())
        searchBox.maxLines = 1
        searchBox.setPadding(dp(16), dp(8), dp(16), dp(8))
        searchBox.visibility = View.GONE
        searchBox.doAfterTextChanged { refresh() }

        listView = ListView(this)
        listView.divider = ColorDrawable(0xFF333333.toInt())
        listView.dividerHeight = 1
        listView.setBackgroundColor(0xFF1C1C1C.toInt())
        adapter = ItemsAdapter()
        listView.adapter = adapter

        emptyView = tv("Nothing here yet", 16f, 0xFF888888.toInt())
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(0, dp(40), 0, 0)
        emptyView.visibility = View.GONE

        val bottom = tv("Close", 18f)
        bottom.gravity = Gravity.CENTER
        bottom.setOnClickListener { finish() }

        root.addView(bar)
        root.addView(searchBox)
        root.addView(emptyView)
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        setContentView(root)

        btnAdd.setOnClickListener {
            twoFieldDialog("Add bookmark", "Title", "URL", "", "https://") { t, u ->
                if (u.isNotBlank() && u != "https://") {
                    store.addBookmark(t, normalizeUrl(u))
                    refresh()
                }
            }
        }
        btnSearch.setOnClickListener {
            if (searchBox.visibility == View.VISIBLE) {
                searchBox.setText("")
                searchBox.visibility = View.GONE
            } else {
                searchBox.visibility = View.VISIBLE
                searchBox.requestFocus()
            }
        }
        btnMore.setOnClickListener { showMore(btnMore) }

        refresh()
    }

    private fun refresh() {
        val all = if (mode == "bookmark") store.getBookmarks() else store.getHistory()
        val q = searchBox.text.toString().trim().lowercase()
        var l: List<Item> = if (q.isEmpty()) all else all.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
        l = when (sortBy) {
            0 -> l.sortedByDescending { it.time }
            1 -> l.sortedBy { it.time }
            2 -> l.sortedBy { it.title.lowercase() }
            else -> l.sortedBy { it.url.lowercase() }
        }
        shown = l
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showMore(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "Sort")
        if (mode == "bookmark") {
            pm.menu.add(0, 2, 0, "Show detail info").apply {
                isCheckable = true
                isChecked = detail
            }
            pm.menu.add(0, 3, 0, "Single line title").apply {
                isCheckable = true
                isChecked = singleLine
            }
            pm.menu.add(0, 4, 0, "Import from HTML")
            pm.menu.add(0, 5, 0, "Export to HTML")
        } else {
            pm.menu.add(0, 6, 0, "Clear all history")
        }
        pm.setOnMenuItemClickListener { m ->
            when (m.itemId) {
                1 -> AlertDialog.Builder(this)
                    .setTitle("Sort")
                    .setItems(arrayOf("Newest first", "Oldest first", "Title A–Z", "URL A–Z")) { _, w ->
                        sortBy = w
                        refresh()
                    }
                    .show()
                2 -> {
                    detail = !detail
                    refresh()
                }
                3 -> {
                    singleLine = !singleLine
                    refresh()
                }
                4 -> importLauncher.launch(arrayOf("text/html", "*/*"))
                5 -> exportLauncher.launch("aura_bookmarks.html")
                6 -> AlertDialog.Builder(this)
                    .setMessage("Clear all history?")
                    .setPositiveButton("Clear") { _, _ ->
                        store.clearHistory()
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
        }
        pm.show()
    }

    private fun finishWith(url: String, newTab: Boolean) {
        setResult(RESULT_OK, Intent().putExtra("url", url).putExtra("newTab", newTab))
        finish()
    }

    private fun esc(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun unesc(s: String) =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")

    private fun doExport(uri: Uri) {
        try {
            val sb = StringBuilder()
            sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
            sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
            sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
            for (b in store.getBookmarks()) {
                sb.append("    <DT><A HREF=\"${esc(b.url)}\" ADD_DATE=\"${b.time / 1000}\">${esc(b.title)}</A>\n")
            }
            sb.append("</DL><p>\n")
            contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
            toast("Exported")
        } catch (e: Exception) {
            toast("Export failed")
        }
    }

    private fun doImport(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val re = Regex(
                "<A\\s[^>]*?HREF=\"([^\"]+)\"[^>]*>(.*?)</A>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            var n = 0
            for (m in re.findAll(text)) {
                val u = unesc(m.groupValues[1])
                if (u.startsWith("http")) {
                    store.addBookmark(unesc(m.groupValues[2]).trim(), u)
                    n++
                }
            }
            toast("Imported $n bookmarks")
            refresh()
        } catch (e: Exception) {
            toast("Import failed")
        }
    }

    inner class ItemsAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = shown[position]
            val row = LinearLayout(this@ItemsActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(10), dp(4), dp(10))

            val icon = letterIcon(hostOf(item.url), 40)
            (icon.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(14)

            val col = LinearLayout(this@ItemsActivity)
            col.orientation = LinearLayout.VERTICAL
            val t = tv(item.title.ifBlank { item.url }, 16f)
            t.maxLines = if (singleLine) 1 else 3
            t.ellipsize = TextUtils.TruncateAt.END
            col.addView(t)
            if (detail) {
                val sub = tv(
                    dateFmt.format(Date(item.time)) + "    " + hostOf(item.url),
                    13f, 0xFF999999.toInt()
                )
                sub.maxLines = 1
                sub.ellipsize = TextUtils.TruncateAt.END
                col.addView(sub)
            }

            val more = tv("⋮", 22f)
            more.setPadding(dp(14), dp(8), dp(14), dp(8))
            more.setOnClickListener {
                val pm = PopupMenu(this@ItemsActivity, more)
                pm.menu.add(0, 1, 0, "Open in new tab")
                pm.menu.add(0, 2, 0, "Copy link")
                pm.menu.add(0, 3, 0, "Delete")
                pm.setOnMenuItemClickListener { m ->
                    when (m.itemId) {
                        1 -> finishWith(item.url, true)
                        2 -> {
                            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("url", item.url))
                            toast("Link copied")
                        }
                        3 -> {
                            if (mode == "bookmark") store.removeBookmark(item.url)
                            else store.removeHistory(item.url)
                            refresh()
                        }
                    }
                    true
                }
                pm.show()
            }

            row.addView(icon)
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(more)
            row.setOnClickListener { finishWith(item.url, false) }
            return row
        }
    }
}
