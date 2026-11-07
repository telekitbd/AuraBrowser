package com.aura.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private const val DARK_JS =
            "(function(){var id='aura-dark';if(document.getElementById(id))return;" +
                "var s=document.createElement('style');s.id=id;" +
                "s.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#fff}" +
                "img,video,picture,canvas,iframe{filter:invert(1) hue-rotate(180deg)!important}';" +
                "document.head.appendChild(s);})();"

        private const val LIGHT_JS =
            "var e=document.getElementById('aura-dark');if(e)e.remove();"

        private val BLOCKED = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "taboola.com", "outbrain.com", "popads.net", "popcash.net",
            "propellerads.com", "exoclick.com", "adsterra.com", "onclickads.net",
            "ad-maven.com", "mgid.com", "criteo.com", "scorecardresearch.com",
            "moatads.com", "adsco.re", "juicyads.com", "trafficjunky.com",
            "clickadu.com", "hilltopads.net", "rubiconproject.com", "pubmatic.com"
        )

        private val ZOOMS = listOf(80, 100, 125, 150, 175)
    }

    private lateinit var store: Store
    private val tabs = mutableListOf<BTab>()
    private var cur: BTab? = null

    private lateinit var content: FrameLayout
    private lateinit var homeView: ScrollView
    private lateinit var homeNote: TextView
    private lateinit var dialGrid: GridLayout
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var tabCount: TextView
    private lateinit var btnStar: TextView
    private lateinit var btnRefresh: TextView

    private var fullscreen = false
    private var adblockOn = true
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val mobileUa: String by lazy {
        WebSettings.getDefaultUserAgent(this).replace("; wv", "")
    }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val res = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
            fileCallback?.onReceiveValue(res)
            fileCallback = null
        }

    private val itemsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val d = r.data
            if (r.resultCode == RESULT_OK && d != null) {
                d.getStringExtra("url")?.let { u ->
                    if (d.getBooleanExtra("newTab", false)) {
                        selectTab(newTab())
                    }
                    go(u)
                }
            }
        }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        adblockOn = store.getBool("adblock", true)
        setContentView(R.layout.activity_main)

        content = findViewById(R.id.content)
        homeView = findViewById(R.id.homeView)
        homeNote = findViewById(R.id.homeNote)
        dialGrid = findViewById(R.id.dialGrid)
        urlBar = findViewById(R.id.urlBar)
        progress = findViewById(R.id.progress)
        findBar = findViewById(R.id.findBar)
        findInput = findViewById(R.id.findInput)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        tabCount = findViewById(R.id.tabCount)
        btnStar = findViewById(R.id.btnStar)
        btnRefresh = findViewById(R.id.btnRefresh)

        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                go(urlBar.text.toString())
                true
            } else false
        }

        btnStar.setOnClickListener { toggleBookmark() }
        findViewById<View>(R.id.btnDesktop).setOnClickListener { toggleDesktop() }
        btnRefresh.setOnClickListener {
            val t = cur
            if (t != null && !t.isHome) {
                if (t.loading) t.webView.stopLoading() else t.webView.reload()
            }
        }

        findViewById<View>(R.id.navBack).setOnClickListener { navBack() }
        findViewById<View>(R.id.navFwd).setOnClickListener { navForward() }
        findViewById<View>(R.id.navHome).setOnClickListener { goHome() }
        findViewById<View>(R.id.navBookmarks).setOnClickListener { openItems("bookmark") }
        findViewById<View>(R.id.navTabs).setOnClickListener { showTabs() }
        findViewById<View>(R.id.navMenu).setOnClickListener { showMenu() }

        findInput.doAfterTextChanged { s ->
            val q = s?.toString() ?: ""
            if (q.isEmpty()) cur?.webView?.clearMatches() else cur?.webView?.findAllAsync(q)
        }
        findViewById<View>(R.id.findNext).setOnClickListener { cur?.webView?.findNext(true) }
        findViewById<View>(R.id.findPrev).setOnClickListener { cur?.webView?.findNext(false) }
        findViewById<View>(R.id.findClose).setOnClickListener { closeFind() }

        CookieManager.getInstance().setAcceptCookie(true)
        renderDial()

        selectTab(newTab())
        val d = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && d != null) go(d.toString())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val d = intent.data
        if (intent.action == Intent.ACTION_VIEW && d != null) {
            selectTab(newTab())
            go(d.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onDestroy() {
        for (t in tabs) {
            try {
                t.webView.destroy()
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    private fun onBack() {
        if (customView != null) {
            hideCustom()
            return
        }
        if (findBar.visibility == View.VISIBLE) {
            closeFind()
            return
        }
        if (fullscreen) {
            setFullscreen(false)
            return
        }
        val t = cur
        if (t != null && !t.isHome && t.webView.canGoBack()) t.webView.goBack()
        else if (t != null && !t.isHome) goHome()
        else if (t != null && tabs.size > 1) closeTab(t)
        else finish()
    }

    // ------------------------------------------------------------------ tabs

    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(incognito: Boolean = false): BTab {
        val wv = WebView(this)
        val tab = BTab(wv, incognito)
        wv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.mediaPlaybackRequiresUserGesture = false
        s.javaScriptCanOpenWindowsAutomatically = true
        s.allowFileAccess = false
        applySettings(tab)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.setDownloadListener { url, ua, cd, mime, _ ->
            try {
                val req = DownloadManager.Request(Uri.parse(url))
                req.setMimeType(mime)
                req.addRequestHeader("User-Agent", ua)
                val ck = CookieManager.getInstance().getCookie(url)
                if (ck != null) req.addRequestHeader("Cookie", ck)
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val name = URLUtil.guessFileName(url, cd, mime)
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                toast("Downloading $name")
            } catch (e: Exception) {
                toast("Download failed")
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val scheme = u.scheme ?: return false
                if (scheme == "http" || scheme == "https" || scheme == "about" ||
                    scheme == "file" || scheme == "data" || scheme == "blob"
                ) return false
                try {
                    val i = if (scheme == "intent") {
                        val x = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                        x.addCategory(Intent.CATEGORY_BROWSABLE)
                        x.component = null
                        x.selector = null
                        x
                    } else Intent(Intent.ACTION_VIEW, u)
                    startActivity(i)
                } catch (e: Exception) {
                }
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                if (adblockOn) {
                    val h = request.url.host
                    if (h != null && BLOCKED.any { h == it || h.endsWith(".$it") }) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tab.url = url ?: ""
                tab.loading = true
                tab.progress = 10
                if (tab === cur) updateUi()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tab.loading = false
                tab.progress = 100
                tab.url = url ?: tab.url
                tab.title = view.title ?: tab.url
                if (!tab.incognito) store.addHistory(tab.title, tab.url)
                if (store.getBool("dark", false)) view.evaluateJavascript(DARK_JS, null)
                if (tab === cur) updateUi()
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    val html = "<html><body style='background:#111;color:#ddd;" +
                        "font-family:sans-serif;padding:24px'><h3>Can't open page</h3>" +
                        "<p>${error.description}</p><p style='color:#888'>${request.url}</p></body></html>"
                    view.loadDataWithBaseURL(request.url.toString(), html, "text/html", "utf-8", request.url.toString())
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
                if (tab === cur) progress.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title ?: tab.url
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    fileLauncher.launch(fileChooserParams.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }

            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCb = callback
                view.setBackgroundColor(Color.BLACK)
                (window.decorView as FrameLayout).addView(
                    view, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                setBars(true)
            }

            override fun onHideCustomView() {
                hideCustom()
            }
        }

        wv.visibility = View.GONE
        content.addView(wv)
        tabs.add(tab)
        return tab
    }

    private fun hideCustom() {
        val v = customView ?: return
        (window.decorView as FrameLayout).removeView(v)
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
        setBars(fullscreen)
    }

    private fun applySettings(t: BTab) {
        val s = t.webView.settings
        s.userAgentString = if (store.getBool("desktop", false)) DESKTOP_UA else mobileUa
        s.blockNetworkImage = store.getBool("blockimg", false)
        s.textZoom = store.getInt("zoom", 100)
    }

    private fun selectTab(t: BTab) {
        cur = t
        showContent()
        updateUi()
        if (findBar.visibility == View.VISIBLE) closeFind()
    }

    private fun closeTab(t: BTab) {
        val idx = tabs.indexOf(t)
        content.removeView(t.webView)
        t.webView.stopLoading()
        t.webView.destroy()
        tabs.remove(t)
        if (tabs.isEmpty()) {
            selectTab(newTab())
        } else if (cur === t) {
            selectTab(tabs[minOf(idx, tabs.size - 1)])
        } else {
            updateUi()
        }
    }

    private fun showContent() {
        val c = cur
        for (t in tabs) {
            t.webView.visibility = if (t === c && !t.isHome) View.VISIBLE else View.GONE
        }
        val home = c == null || c.isHome
        homeView.visibility = if (home) View.VISIBLE else View.GONE
        homeNote.visibility = if (c != null && c.incognito) View.VISIBLE else View.GONE
    }

    private fun updateUi() {
        val t = cur ?: return
        if (!urlBar.hasFocus()) urlBar.setText(if (t.isHome) "" else t.url)
        val marked = !t.isHome && store.isBookmarked(t.url)
        btnStar.text = if (marked) "★" else "☆"
        btnStar.setTextColor(if (marked) 0xFFFFC107.toInt() else Color.WHITE)
        btnRefresh.text = if (t.loading && !t.isHome) "✕" else "⟳"
        progress.visibility = if (t.loading && !t.isHome) View.VISIBLE else View.GONE
        progress.progress = t.progress
        tabCount.text = tabs.size.toString()
    }

    // ------------------------------------------------------------------ navigation

    private fun resolve(input: String): String {
        val t = input.trim()
        if (t.startsWith("http://") || t.startsWith("https://") ||
            t.startsWith("file://") || t.startsWith("about:")
        ) return t
        if (!t.contains(" ") && t.contains(".")) return "https://$t"
        return "https://www.google.com/search?q=" + Uri.encode(t)
    }

    private fun go(input: String) {
        val t = cur ?: return
        if (input.isBlank()) return
        val u = resolve(input)
        t.isHome = false
        t.url = u
        showContent()
        t.webView.loadUrl(u)
        hideKeyboard()
        updateUi()
    }

    private fun goHome() {
        val t = cur ?: return
        t.isHome = true
        showContent()
        hideKeyboard()
        updateUi()
    }

    private fun navBack() {
        val t = cur ?: return
        if (!t.isHome && t.webView.canGoBack()) t.webView.goBack()
        else if (!t.isHome) goHome()
    }

    private fun navForward() {
        val t = cur ?: return
        if (t.isHome && t.url.isNotEmpty()) {
            t.isHome = false
            showContent()
            updateUi()
        } else if (t.webView.canGoForward()) {
            t.webView.goForward()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
        urlBar.clearFocus()
    }

    private fun openItems(mode: String) {
        itemsLauncher.launch(Intent(this, ItemsActivity::class.java).putExtra("mode", mode))
    }

    // ------------------------------------------------------------------ home shortcuts

    private fun renderDial() {
        dialGrid.removeAllViews()
        val w = resources.displayMetrics.widthPixels / 5
        for (d in store.getDial()) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(0, dp(12), 0, dp(12))
            cell.addView(letterIcon(d.title, 52))
            val lbl = tv(d.title, 13f)
            lbl.maxLines = 1
            lbl.ellipsize = TextUtils.TruncateAt.END
            lbl.gravity = Gravity.CENTER
            lbl.setPadding(dp(2), dp(6), dp(2), 0)
            cell.addView(lbl)
            val lp = GridLayout.LayoutParams()
            lp.width = w
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            cell.layoutParams = lp
            cell.setOnClickListener { go(d.url) }
            cell.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(d.title)
                    .setMessage("Remove this shortcut?")
                    .setPositiveButton("Remove") { _, _ ->
                        store.removeDial(d.url)
                        renderDial()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            dialGrid.addView(cell)
        }

        val plus = LinearLayout(this)
        plus.orientation = LinearLayout.VERTICAL
        plus.gravity = Gravity.CENTER_HORIZONTAL
        plus.setPadding(0, dp(12), 0, dp(12))
        val ic = letterIcon("+", 52, "+")
        (ic.background as android.graphics.drawable.GradientDrawable).setColor(0xFF2A2A2A.toInt())
        plus.addView(ic)
        val plp = GridLayout.LayoutParams()
        plp.width = w
        plp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        plus.layoutParams = plp
        plus.setOnClickListener { addShortcutDialog("", "https://") }
        dialGrid.addView(plus)
    }

    private fun addShortcutDialog(title: String, url: String) {
        twoFieldDialog("Add shortcut", "Name", "URL", title, url) { n, u ->
            if (u.isNotBlank() && u != "https://") {
                store.addDial(n, normalizeUrl(u))
                renderDial()
            }
        }
    }

    private fun addToDial() {
        val t = cur ?: return
        if (t.isHome) addShortcutDialog("", "https://")
        else addShortcutDialog(t.title, t.url)
    }

    // ------------------------------------------------------------------ bookmarks

    private fun toggleBookmark() {
        val t = cur ?: return
        if (t.isHome || t.url.isEmpty()) {
            toast("Open a page first")
            return
        }
        if (store.isBookmarked(t.url)) {
            store.removeBookmark(t.url)
            toast("Bookmark removed")
        } else {
            store.addBookmark(t.title, t.url)
            toast("Bookmarked")
        }
        updateUi()
    }

    // ------------------------------------------------------------------ tabs dialog

    private fun showTabs() {
        var priv = cur?.incognito == true
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF222222.toInt())

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        val tNormal = tv("😀  Normal", 16f)
        tNormal.gravity = Gravity.CENTER
        tNormal.setPadding(0, dp(18), 0, dp(14))
        val tPriv = tv("🕶  Private", 16f)
        tPriv.gravity = Gravity.CENTER
        tPriv.setPadding(0, dp(18), 0, dp(14))
        head.addView(tNormal, LinearLayout.LayoutParams(0, wrap, 1f))
        head.addView(tPriv, LinearLayout.LayoutParams(0, wrap, 1f))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.addView(list)

        val foot = LinearLayout(this)
        foot.orientation = LinearLayout.HORIZONTAL
        val bDel = tv("Delete all", 18f)
        bDel.gravity = Gravity.CENTER
        bDel.setPadding(0, dp(18), 0, dp(18))
        val bNew = tv("New tab", 18f)
        bNew.gravity = Gravity.CENTER
        bNew.setPadding(0, dp(18), 0, dp(18))
        foot.addView(bDel, LinearLayout.LayoutParams(0, wrap, 1f))
        foot.addView(bNew, LinearLayout.LayoutParams(0, wrap, 1f))

        root.addView(head)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(foot)

        fun rebuild() {
            tNormal.alpha = if (priv) 0.45f else 1f
            tPriv.alpha = if (priv) 1f else 0.45f
            list.removeAllViews()
            for (t in tabs.filter { it.incognito == priv }) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(16), dp(10), dp(6), dp(10))
                if (t === cur) row.setBackgroundColor(0xFF3A3A60.toInt())

                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                val title = tv(if (t.isHome) "Home" else t.title.ifBlank { t.url }, 16f)
                title.maxLines = 1
                title.ellipsize = TextUtils.TruncateAt.END
                col.addView(title)
                if (!t.isHome) {
                    val sub = tv(hostOf(t.url), 12f, 0xFF999999.toInt())
                    sub.maxLines = 1
                    col.addView(sub)
                }

                val x = tv("✕", 20f)
                x.setPadding(dp(16), dp(10), dp(16), dp(10))
                x.setOnClickListener {
                    closeTab(t)
                    rebuild()
                }
                row.addView(col, LinearLayout.LayoutParams(0, wrap, 1f))
                row.addView(x)
                row.setOnClickListener {
                    selectTab(t)
                    dlg.dismiss()
                }
                list.addView(row)
            }
        }

        tNormal.setOnClickListener {
            priv = false
            rebuild()
        }
        tPriv.setOnClickListener {
            priv = true
            rebuild()
        }
        bDel.setOnClickListener {
            for (t in tabs.filter { it.incognito == priv }.toList()) closeTab(t)
            rebuild()
        }
        bNew.setOnClickListener {
            selectTab(newTab(priv))
            dlg.dismiss()
        }

        rebuild()
        dlg.setContentView(root)
        dlg.show()
    }

    // ------------------------------------------------------------------ tools menu

    private fun showMenu() {
        val dlg = BottomSheetDialog(this)
        val grid = GridLayout(this)
        grid.columnCount = 5
        grid.setPadding(dp(4), dp(12), dp(4), dp(12))
        val w = resources.displayMetrics.widthPixels / 5 - 1
        val on = 0xFF80CBC4.toInt()

        fun add(icon: String, label: String, active: Boolean = false, action: () -> Unit) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(dp(2), dp(10), dp(2), dp(10))
            val i = tv(icon, 24f, if (active) on else Color.WHITE)
            i.gravity = Gravity.CENTER
            val l = tv(label, 12f, if (active) on else 0xFFDDDDDD.toInt())
            l.gravity = Gravity.CENTER
            l.maxLines = 2
            cell.addView(i)
            cell.addView(l)
            val lp = GridLayout.LayoutParams()
            lp.width = w
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            cell.layoutParams = lp
            cell.setOnClickListener {
                dlg.dismiss()
                action()
            }
            grid.addView(cell)
        }

        add("🕘", "History") { openItems("history") }
        add("⬇", "Downloads") {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (e: Exception) {
                toast("Cannot open downloads")
            }
        }
        add("➕", "New tab") { selectTab(newTab()) }
        add("⛶", "Full screen") {
            setFullscreen(true)
            toast("Press back to exit full screen")
        }
        add("🌙", "Dark mode", store.getBool("dark", false)) { toggleDark() }
        add("🌐", "Translate") { translate() }
        add("🔗", "Share URL") { shareUrl() }
        add("🔍", "Find in page") { openFind() }
        add("🖨", "Print / PDF") { printPage() }
        add("📌", "Add to home") { addToDial() }
        add("🛡", "Ad blocker", adblockOn) {
            adblockOn = !adblockOn
            store.setBool("adblock", adblockOn)
            toast(if (adblockOn) "Ad blocker on" else "Ad blocker off")
        }
        add("🖼", "Block images", store.getBool("blockimg", false)) {
            val v = !store.getBool("blockimg", false)
            store.setBool("blockimg", v)
            for (t in tabs) applySettings(t)
            toast(if (v) "Images blocked" else "Images allowed")
        }
        add("🖥", "Desktop", store.getBool("desktop", false)) { toggleDesktop() }
        add("📸", "Screenshot") { screenshot() }
        add("🔆", "Brightness") { brightnessDialog() }
        add("🔠", "Text size") { cycleTextSize() }
        add("📄", "Page source") { pageSource() }
        add("🧹", "Clear data") { clearData() }
        add("⏻", "Exit") { finishAffinity() }

        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFF222222.toInt())
        sv.addView(grid)
        dlg.setContentView(sv)
        dlg.show()
        dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun toggleDark() {
        val v = !store.getBool("dark", false)
        store.setBool("dark", v)
        for (t in tabs) {
            if (!t.isHome) t.webView.evaluateJavascript(if (v) DARK_JS else LIGHT_JS, null)
        }
        toast(if (v) "Dark mode on" else "Dark mode off")
    }

    private fun toggleDesktop() {
        val v = !store.getBool("desktop", false)
        store.setBool("desktop", v)
        for (t in tabs) applySettings(t)
        val c = cur
        if (c != null && !c.isHome) c.webView.reload()
        toast(if (v) "Desktop site" else "Mobile site")
    }

    private fun translate() {
        val t = cur ?: return
        if (t.isHome) {
            toast("Open a page first")
            return
        }
        go("https://translate.google.com/translate?sl=auto&tl=bn&u=" + Uri.encode(t.url))
    }

    private fun shareUrl() {
        val t = cur ?: return
        if (t.isHome) {
            toast("Open a page first")
            return
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, t.url)
        startActivity(Intent.createChooser(i, "Share URL"))
    }

    private fun openFind() {
        val t = cur ?: return
        if (t.isHome) {
            toast("Open a page first")
            return
        }
        findBar.visibility = View.VISIBLE
        findInput.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeFind() {
        findBar.visibility = View.GONE
        findInput.setText("")
        cur?.webView?.clearMatches()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(findInput.windowToken, 0)
    }

    private fun printPage() {
        val t = cur ?: return
        if (t.isHome) {
            toast("Open a page first")
            return
        }
        val pm = getSystemService(PRINT_SERVICE) as PrintManager
        val name = t.title.ifBlank { "page" }
        pm.print(name, t.webView.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
    }

    private fun screenshot() {
        val t = cur
        if (t == null || t.isHome || t.webView.width == 0) {
            toast("Open a page first")
            return
        }
        try {
            val wv = t.webView
            val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
            wv.draw(Canvas(bmp))
            val cv = ContentValues()
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "aura_${System.currentTimeMillis()}.png")
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AuraBrowser")
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                toast("Saved to Pictures/AuraBrowser")
            } else {
                toast("Screenshot failed")
            }
        } catch (e: Exception) {
            toast("Screenshot failed")
        }
    }

    private fun brightnessDialog() {
        val box = LinearLayout(this)
        box.setPadding(dp(24), dp(24), dp(24), dp(8))
        val sb = SeekBar(this)
        sb.max = 100
        val b = window.attributes.screenBrightness
        sb.progress = if (b < 0) 50 else (b * 100).toInt()
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val a = window.attributes
                a.screenBrightness = maxOf(p, 5) / 100f
                window.attributes = a
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("Brightness")
            .setView(box)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun cycleTextSize() {
        val now = store.getInt("zoom", 100)
        val idx = ZOOMS.indexOf(now)
        val next = ZOOMS[(idx + 1) % ZOOMS.size]
        store.setInt("zoom", next)
        for (t in tabs) applySettings(t)
        toast("Text size $next%")
    }

    private fun pageSource() {
        val t = cur
        if (t == null || t.isHome) {
            toast("Open a page first")
            return
        }
        t.webView.evaluateJavascript("document.documentElement.outerHTML") { r ->
            var txt = try {
                JSONArray("[$r]").getString(0)
            } catch (e: Exception) {
                r ?: ""
            }
            if (txt.length > 200000) txt = txt.substring(0, 200000) + "\n… (truncated)"
            val view = tv(txt, 11f, 0xFFDDDDDD.toInt())
            view.typeface = android.graphics.Typeface.MONOSPACE
            view.setTextIsSelectable(true)
            view.setPadding(dp(12), dp(12), dp(12), dp(12))
            val sv = ScrollView(this)
            sv.addView(view)
            AlertDialog.Builder(this)
                .setTitle("Page source")
                .setView(sv)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun clearData() {
        AlertDialog.Builder(this)
            .setTitle("Clear data")
            .setMessage("Clear cookies, cache and history?")
            .setPositiveButton("Clear") { _, _ ->
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                for (t in tabs) {
                    t.webView.clearCache(true)
                    t.webView.clearHistory()
                    t.webView.clearFormData()
                }
                store.clearHistory()
                toast("Data cleared")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ full screen

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val vis = if (on) View.GONE else View.VISIBLE
        topBar.visibility = vis
        bottomBar.visibility = vis
        setBars(on)
    }

    private fun setBars(hide: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
