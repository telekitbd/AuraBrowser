package com.aura.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.text.format.Formatter
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.webkit.MimeTypeMap
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.Executors

class DownloadsActivity : AppCompatActivity() {

    private class Info(val status: Int, val total: Long, val done: Long)

    private companion object {
        val FILTERS = listOf("All", "Page", "Image", "Video", "Audio", "Document", "Others")
        val PAGE = setOf("html", "htm", "mht", "mhtml", "xhtml")
        val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "avif")
        val VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "flv", "ts", "wmv")
        val AUDIO = setOf("mp3", "m4a", "wav", "aac", "ogg", "flac", "opus", "wma")
        val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "epub", "odt")
    }

    private lateinit var store: Store
    private lateinit var dm: DownloadManager
    private var all: List<DlItem> = emptyList()
    private var shown: List<DlItem> = emptyList()
    private var infos: Map<Long, Info> = emptyMap()

    private var filter = 0
    private var sortBy = 0
    private var detail = true
    private var singleLine = true

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var searchBox: EditText
    private lateinit var filterText: TextView
    private lateinit var adapter: Adapter

    private val thumbs = LruCache<String, Bitmap>(60)
    private val thumbFail = HashSet<String>()
    private val thumbBusy = HashSet<String>()
    private val uriCache = HashMap<Long, Uri>()
    private val pool = Executors.newFixedThreadPool(2)

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }
    private val dateFmt = SimpleDateFormat("MM/dd/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), 0, dp(4), 0)

        val back = iconButton(R.drawable.ic_arrow_back, 48, 56)
        back.setOnClickListener { finish() }

        val filterBox = LinearLayout(this)
        filterBox.orientation = LinearLayout.HORIZONTAL
        filterBox.gravity = Gravity.CENTER_VERTICAL
        filterBox.setPadding(dp(8), 0, dp(8), 0)
        filterText = tv("All", 22f)
        val arrow = iconView(R.drawable.ic_arrow_down, 24)
        filterBox.addView(filterText)
        filterBox.addView(arrow)
        filterBox.setOnClickListener { chooseFilter(filterBox) }

        val btnRefresh = iconButton(R.drawable.ic_refresh, 48, 56)
        val btnSearch = iconButton(R.drawable.ic_search, 48, 56)
        val btnMore = iconButton(R.drawable.ic_more_vert, 48, 56)
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(filterBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        bar.addView(btnRefresh, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(btnSearch, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(btnMore, LinearLayout.LayoutParams(dp(48), dp(56)))

        searchBox = EditText(this)
        searchBox.hint = "Search"
        searchBox.setTextColor(Color.WHITE)
        searchBox.setHintTextColor(0xFF777777.toInt())
        searchBox.maxLines = 1
        searchBox.setPadding(dp(16), dp(8), dp(16), dp(8))
        searchBox.visibility = View.GONE
        searchBox.doAfterTextChanged { applyFilter() }

        listView = ListView(this)
        listView.divider = ColorDrawable(0xFF333333.toInt())
        listView.dividerHeight = 1
        val listBg = GradientDrawable()
        listBg.setColor(0xFF1E1E1E.toInt())
        listBg.cornerRadius = dp(24).toFloat()
        listView.background = listBg
        listView.clipToOutline = true
        adapter = Adapter()
        listView.adapter = adapter

        emptyView = tv("No downloads yet", 16f, 0xFF888888.toInt())
        emptyView.gravity = Gravity.CENTER
        emptyView.visibility = View.GONE

        val bottom = tv("Close", 18f)
        bottom.gravity = Gravity.CENTER
        bottom.setOnClickListener { finish() }

        // list and empty message share one rounded card
        val card = FrameLayout(this)
        card.addView(listView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        card.addView(emptyView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(bar)
        root.addView(searchBox)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        lp.setMargins(dp(10), dp(6), dp(10), dp(6))
        root.addView(card, lp)
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        setContentView(root)

        btnRefresh.setOnClickListener {
            thumbFail.clear()
            refresh()
            toast("Refreshed")
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

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        pool.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ menus

    private fun chooseFilter(anchor: View) {
        val pm = PopupMenu(this, anchor)
        for ((i, n) in FILTERS.withIndex()) {
            pm.menu.add(1, i, i, n).apply {
                isCheckable = true
                isChecked = i == filter
            }
        }
        pm.menu.setGroupCheckable(1, true, true)
        pm.setOnMenuItemClickListener { m ->
            filter = m.itemId
            filterText.text = FILTERS[filter]
            applyFilter()
            true
        }
        pm.show()
    }

    private fun showMore(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "Sort")
        pm.menu.add(0, 2, 1, "Show detail info").apply {
            isCheckable = true
            isChecked = detail
        }
        pm.menu.add(0, 3, 2, "Single line title").apply {
            isCheckable = true
            isChecked = singleLine
        }
        pm.menu.add(0, 4, 3, "Clear list")
        pm.menu.add(0, 5, 4, "Open system Downloads")
        pm.setOnMenuItemClickListener { m ->
            when (m.itemId) {
                1 -> AlertDialog.Builder(this)
                    .setTitle("Sort")
                    .setItems(arrayOf("Newest first", "Oldest first", "Name A–Z", "Largest first")) { _, w ->
                        sortBy = w
                        applyFilter()
                    }
                    .show()
                2 -> {
                    detail = !detail
                    adapter.notifyDataSetChanged()
                }
                3 -> {
                    singleLine = !singleLine
                    adapter.notifyDataSetChanged()
                }
                4 -> AlertDialog.Builder(this)
                    .setMessage("Clear the list? Files stay on your phone.")
                    .setPositiveButton("Clear") { _, _ ->
                        store.clearDownloads()
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                5 -> try {
                    startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                } catch (e: Exception) {
                    toast("Cannot open downloads")
                }
            }
            true
        }
        pm.show()
    }

    // ------------------------------------------------------------------ data

    private fun ext(d: DlItem): String = d.name.substringAfterLast('.', "").lowercase()

    private fun category(d: DlItem): Int {
        val e = ext(d)
        return when {
            e in PAGE -> 1
            e in IMAGE -> 2
            e in VIDEO -> 3
            e in AUDIO -> 4
            e in DOC -> 5
            else -> 6
        }
    }

    private fun refresh() {
        all = store.getDownloads()
        val m = HashMap<Long, Info>()
        val dmIds = all.filter { it.id >= 0 }.map { it.id }
        if (dmIds.isNotEmpty()) {
            try {
                val q = DownloadManager.Query().setFilterById(*dmIds.toLongArray())
                val c = dm.query(q)
                if (c != null) {
                    c.use {
                        val iId = it.getColumnIndex(DownloadManager.COLUMN_ID)
                        val iSt = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val iTot = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val iDone = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        while (it.moveToNext()) {
                            m[it.getLong(iId)] = Info(it.getInt(iSt), it.getLong(iTot), it.getLong(iDone))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        for (d in all) if (d.id < 0) localInfo(d)?.let { m[d.id] = it }
        infos = m
        applyFilter()
    }

    /** Status of a file saved by the in-app HLS downloader. */
    private fun localInfo(d: DlItem): Info? {
        val st = VideoDl.states[d.id]
        if (st != null && st.status == 0) return Info(DownloadManager.STATUS_RUNNING, st.total.toLong(), st.done.toLong())
        if (st != null && st.status == 2) return Info(DownloadManager.STATUS_FAILED, 0, 0)
        if (d.local.isEmpty()) return null
        try {
            val c = contentResolver.query(
                Uri.parse(d.local),
                arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.IS_PENDING), null, null, null
            )
            c?.use {
                if (it.moveToFirst()) {
                    val size = it.getLong(0)
                    val pending = it.getInt(1)
                    return if (pending == 0 && size > 0) Info(DownloadManager.STATUS_SUCCESSFUL, size, size)
                    else Info(DownloadManager.STATUS_FAILED, 0, 0)
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun sizeOf(d: DlItem): Long {
        val i = infos[d.id] ?: return 0
        return if (i.total > 0) i.total else i.done
    }

    private fun applyFilter() {
        val q = searchBox.text.toString().trim().lowercase()
        var l = all.filter { (filter == 0 || category(it) == filter) && (q.isEmpty() || it.name.lowercase().contains(q)) }
        l = when (sortBy) {
            0 -> l.sortedByDescending { it.time }
            1 -> l.sortedBy { it.time }
            2 -> l.sortedBy { it.name.lowercase() }
            else -> l.sortedByDescending { sizeOf(it) }
        }
        shown = l
        adapter.notifyDataSetChanged()
        emptyView.text = if (all.isEmpty()) "No downloads yet" else "Nothing found"
        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun sz(n: Long): String = Formatter.formatShortFileSize(this, if (n < 0) 0 else n)

    private fun isRunning(i: Info?): Boolean = i != null && (i.status == DownloadManager.STATUS_RUNNING ||
        i.status == DownloadManager.STATUS_PAUSED || i.status == DownloadManager.STATUS_PENDING)

    private fun statusText(d: DlItem, i: Info?): String {
        if (i == null) return "File removed"
        return when (i.status) {
            DownloadManager.STATUS_SUCCESSFUL ->
                dateFmt.format(Date(d.time)) + "    " + sz(if (i.total > 0) i.total else i.done)
            DownloadManager.STATUS_FAILED -> "Failed  •  tap ⋮ to retry or remove"
            DownloadManager.STATUS_PAUSED -> "Paused  •  " + sz(i.done)
            DownloadManager.STATUS_PENDING -> "Waiting…"
            else -> if (d.id < 0) {
                "Downloading " + (if (i.total > 0) i.done * 100 / i.total else 0) + "%"
            } else if (i.total > 0) {
                "Downloading ${i.done * 100 / i.total}%  •  ${sz(i.done)} / ${sz(i.total)}"
            } else "Downloading  •  ${sz(i.done)}"
        }
    }

    private fun uriOf(d: DlItem): Uri? {
        if (d.local.isNotEmpty()) return Uri.parse(d.local)
        uriCache[d.id]?.let { return it }
        return try {
            dm.getUriForDownloadedFile(d.id)?.also { uriCache[d.id] = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun mimeOf(d: DlItem): String {
        if (d.local.isEmpty()) {
            try {
                dm.getMimeTypeForDownloadedFile(d.id)?.let { return it }
            } catch (_: Exception) {
            }
        }
        if (ext(d) == "ts") return "video/mp2t"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(d)) ?: "*/*"
    }

    // ------------------------------------------------------------------ actions

    private fun openFile(d: DlItem) {
        val i = infos[d.id]
        if (i == null || i.status != DownloadManager.STATUS_SUCCESSFUL) {
            toast("Not ready")
            return
        }
        try {
            val uri = uriOf(d)
            if (uri == null) {
                toast("File not found")
                return
            }
            val it = Intent(Intent.ACTION_VIEW)
            it.setDataAndType(uri, mimeOf(d))
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(it)
        } catch (e: Exception) {
            toast("No app can open this file")
        }
    }

    private fun shareFile(d: DlItem) {
        val i = infos[d.id]
        if (i == null || i.status != DownloadManager.STATUS_SUCCESSFUL) {
            toast("Not ready")
            return
        }
        try {
            val uri = uriOf(d) ?: return
            val it = Intent(Intent.ACTION_SEND)
            it.type = mimeOf(d)
            it.putExtra(Intent.EXTRA_STREAM, uri)
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(it, "Share file"))
        } catch (e: Exception) {
            toast("Cannot share")
        }
    }

    private fun retry(d: DlItem) {
        try {
            val req = DownloadManager.Request(Uri.parse(d.url))
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, d.name)
            val nid = dm.enqueue(req)
            dm.remove(d.id)
            store.removeDownload(d.id)
            store.addDownload(nid, d.name, d.url)
            refresh()
        } catch (e: Exception) {
            toast("Retry failed")
        }
    }

    /** Stops a running download and/or deletes its file, then drops it from the list. */
    private fun removeItem(d: DlItem, deleteFile: Boolean) {
        if (d.id < 0) {
            VideoDl.states[d.id]?.cancel = true
            if (deleteFile && d.local.isNotEmpty()) {
                try {
                    contentResolver.delete(Uri.parse(d.local), null, null)
                } catch (_: Exception) {
                }
            }
        } else if (deleteFile) {
            try {
                dm.remove(d.id)
            } catch (_: Exception) {
            }
        }
        store.removeDownload(d.id)
        refresh()
    }

    // ------------------------------------------------------------------ thumbnails

    private fun loadThumb(key: String, uri: Uri, video: Boolean) {
        if (thumbs.get(key) != null || thumbFail.contains(key) || thumbBusy.contains(key)) return
        thumbBusy.add(key)
        try {
            pool.execute {
                var bmp: Bitmap? = null
                try {
                    if (video) {
                        val r = MediaMetadataRetriever()
                        try {
                            r.setDataSource(this, uri)
                            val f = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (f != null) bmp = Bitmap.createScaledBitmap(f, 160, maxOf(1, 160 * f.height / maxOf(1, f.width)), true)
                        } finally {
                            try {
                                r.release()
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        val o = BitmapFactory.Options()
                        o.inJustDecodeBounds = true
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                        var s = 1
                        while (o.outWidth / (s * 2) >= 200) s *= 2
                        val o2 = BitmapFactory.Options()
                        o2.inSampleSize = s
                        bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                    }
                } catch (_: Exception) {
                }
                val result = bmp
                runOnUiThread {
                    thumbBusy.remove(key)
                    if (result != null) {
                        thumbs.put(key, result)
                        adapter.notifyDataSetChanged()
                    } else {
                        thumbFail.add(key)
                    }
                }
            }
        } catch (_: Exception) {
            thumbBusy.remove(key)
        }
    }

    private fun thumbView(d: DlItem, ok: Boolean): View {
        val size = dp(56)
        val cat = category(d)
        val box = FrameLayout(this)
        val lp = LinearLayout.LayoutParams(size, size)
        lp.rightMargin = dp(14)
        box.layoutParams = lp
        var bmp: Bitmap? = null
        if (ok && (cat == 2 || cat == 3)) {
            val uri = uriOf(d)
            if (uri != null) {
                val key = uri.toString()
                bmp = thumbs.get(key)
                if (bmp == null) loadThumb(key, uri, cat == 3)
            }
        }
        if (bmp != null) {
            val iv = ImageView(this)
            iv.setImageBitmap(bmp)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                }
            }
            iv.clipToOutline = true
            box.addView(iv, FrameLayout.LayoutParams(size, size))
        } else {
            val icon: View = when (cat) {
                3, 4 -> iconView(R.drawable.ic_play, 30)
                else -> letterIcon(d.name, 40, ext(d).uppercase().take(3).ifEmpty { "•" })
            }
            val ilp = FrameLayout.LayoutParams(
                if (cat == 3 || cat == 4) dp(30) else dp(40), if (cat == 3 || cat == 4) dp(30) else dp(40)
            )
            ilp.gravity = Gravity.CENTER
            box.addView(icon, ilp)
        }
        return box
    }

    // ------------------------------------------------------------------ list

    private inner class Adapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val d = shown[position]
            val info = infos[d.id]
            val ok = info != null && info.status == DownloadManager.STATUS_SUCCESSFUL
            val row = LinearLayout(this@DownloadsActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(10), dp(4), dp(10))

            val col = LinearLayout(this@DownloadsActivity)
            col.orientation = LinearLayout.VERTICAL
            val name = tv(d.name, 16f)
            name.maxLines = if (singleLine) 1 else 3
            name.ellipsize = TextUtils.TruncateAt.END
            col.addView(name)
            val running = isRunning(info)
            if (detail || !ok) {
                val sub = tv(statusText(d, info), 13f, 0xFF999999.toInt())
                sub.maxLines = 1
                sub.ellipsize = TextUtils.TruncateAt.END
                col.addView(sub)
            }
            if (running) {
                val pb = ProgressBar(this@DownloadsActivity, null, android.R.attr.progressBarStyleHorizontal)
                pb.max = 100
                pb.progress = if (info!!.total > 0) (info.done * 100 / info.total).toInt() else 0
                pb.isIndeterminate = info.total <= 0
                pb.progressTintList = ColorStateList.valueOf(0xFF7C8CFF.toInt())
                val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
                plp.topMargin = dp(6)
                col.addView(pb, plp)
            }

            val more = iconButton(R.drawable.ic_more_vert, 44, 44)
            more.setOnClickListener {
                val pm = PopupMenu(this@DownloadsActivity, more)
                if (ok) {
                    pm.menu.add(0, 1, 0, "Open")
                    pm.menu.add(0, 2, 0, "Share")
                }
                pm.menu.add(0, 3, 0, "Copy link")
                if (d.id >= 0 && info != null && info.status == DownloadManager.STATUS_FAILED) pm.menu.add(0, 4, 0, "Retry")
                if (running) pm.menu.add(0, 5, 0, "Cancel download")
                pm.menu.add(0, 6, 0, "Remove from list")
                if (ok) pm.menu.add(0, 7, 0, "Delete file")
                pm.setOnMenuItemClickListener { m ->
                    when (m.itemId) {
                        1 -> openFile(d)
                        2 -> shareFile(d)
                        3 -> {
                            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("url", d.url))
                            toast("Link copied")
                        }
                        4 -> retry(d)
                        5 -> removeItem(d, true)
                        6 -> removeItem(d, false)
                        7 -> removeItem(d, true)
                    }
                    true
                }
                pm.show()
            }

            row.addView(thumbView(d, ok))
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(more)
            row.setOnClickListener { openFile(d) }
            return row
        }
    }
}
