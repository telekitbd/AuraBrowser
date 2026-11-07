package com.aura.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadsActivity : AppCompatActivity() {

    private class Info(val status: Int, val total: Long, val done: Long)

    private lateinit var store: Store
    private lateinit var dm: DownloadManager
    private var items: List<DlItem> = emptyList()
    private var infos: Map<Long, Info> = emptyMap()

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var adapter: Adapter

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }
    private val dateFmt = SimpleDateFormat("MM/dd/yyyy  HH:mm", Locale.US)

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
        val title = tv("Downloads", 22f)
        title.setPadding(dp(8), 0, 0, 0)
        val btnMore = iconButton(R.drawable.ic_more_vert, 48, 56)
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(btnMore, LinearLayout.LayoutParams(dp(48), dp(56)))

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
        emptyView.setPadding(0, dp(40), 0, 0)
        emptyView.visibility = View.GONE

        val bottom = tv("Close", 18f)
        bottom.gravity = Gravity.CENTER
        bottom.setOnClickListener { finish() }

        root.addView(bar)
        root.addView(emptyView)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        lp.setMargins(dp(10), dp(6), dp(10), dp(6))
        root.addView(listView, lp)
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        setContentView(root)

        btnMore.setOnClickListener {
            val pm = PopupMenu(this, btnMore)
            pm.menu.add(0, 1, 0, "Clear list")
            pm.menu.add(0, 2, 0, "Open system Downloads")
            pm.setOnMenuItemClickListener { m ->
                when (m.itemId) {
                    1 -> AlertDialog.Builder(this)
                        .setMessage("Clear the list? Files stay on your phone.")
                        .setPositiveButton("Clear") { _, _ ->
                            store.clearDownloads()
                            refresh()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    2 -> try {
                        startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                    } catch (e: Exception) {
                        toast("Cannot open downloads")
                    }
                }
                true
            }
            pm.show()
        }

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

    private fun refresh() {
        items = store.getDownloads()
        val m = HashMap<Long, Info>()
        if (items.isNotEmpty()) {
            try {
                val q = DownloadManager.Query().setFilterById(*items.map { it.id }.toLongArray())
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
        infos = m
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun sz(n: Long): String = Formatter.formatShortFileSize(this, if (n < 0) 0 else n)

    private fun statusText(d: DlItem, i: Info?): String {
        if (i == null) return "File removed"
        return when (i.status) {
            DownloadManager.STATUS_SUCCESSFUL ->
                "Completed  •  " + sz(if (i.total > 0) i.total else i.done) + "  •  " + dateFmt.format(Date(d.time))
            DownloadManager.STATUS_FAILED -> "Failed  •  tap ⋮ to retry"
            DownloadManager.STATUS_PAUSED -> "Paused  •  " + sz(i.done)
            DownloadManager.STATUS_PENDING -> "Waiting…"
            else -> if (i.total > 0)
                "Downloading ${i.done * 100 / i.total}%  •  ${sz(i.done)} / ${sz(i.total)}"
            else "Downloading  •  ${sz(i.done)}"
        }
    }

    private fun openFile(d: DlItem) {
        val i = infos[d.id]
        if (i == null || i.status != DownloadManager.STATUS_SUCCESSFUL) {
            toast("Not ready")
            return
        }
        try {
            val uri = dm.getUriForDownloadedFile(d.id)
            if (uri == null) {
                toast("File not found")
                return
            }
            val mime = dm.getMimeTypeForDownloadedFile(d.id) ?: "*/*"
            val it = Intent(Intent.ACTION_VIEW)
            it.setDataAndType(uri, mime)
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
            val uri = dm.getUriForDownloadedFile(d.id) ?: return
            val mime = dm.getMimeTypeForDownloadedFile(d.id) ?: "*/*"
            val it = Intent(Intent.ACTION_SEND)
            it.type = mime
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

    private inner class Adapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val d = items[position]
            val info = infos[d.id]
            val row = LinearLayout(this@DownloadsActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(10), dp(4), dp(10))

            val ext = d.name.substringAfterLast('.', "").uppercase().take(3).ifEmpty { "•" }
            val icon = letterIcon(d.name, 40, ext)
            (icon.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(14)

            val col = LinearLayout(this@DownloadsActivity)
            col.orientation = LinearLayout.VERTICAL
            val name = tv(d.name, 16f)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.MIDDLE
            col.addView(name)
            val sub = tv(statusText(d, info), 13f, 0xFF999999.toInt())
            sub.maxLines = 1
            sub.ellipsize = TextUtils.TruncateAt.END
            col.addView(sub)

            val running = info != null && (info.status == DownloadManager.STATUS_RUNNING ||
                info.status == DownloadManager.STATUS_PAUSED || info.status == DownloadManager.STATUS_PENDING)
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
                val ok = info != null && info.status == DownloadManager.STATUS_SUCCESSFUL
                if (ok) {
                    pm.menu.add(0, 1, 0, "Open")
                    pm.menu.add(0, 2, 0, "Share")
                }
                pm.menu.add(0, 3, 0, "Copy link")
                if (info != null && info.status == DownloadManager.STATUS_FAILED) pm.menu.add(0, 4, 0, "Retry")
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
                        5, 7 -> {
                            try {
                                dm.remove(d.id)
                            } catch (_: Exception) {
                            }
                            store.removeDownload(d.id)
                            refresh()
                        }
                        6 -> {
                            store.removeDownload(d.id)
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
            row.setOnClickListener { openFile(d) }
            return row
        }
    }
}
