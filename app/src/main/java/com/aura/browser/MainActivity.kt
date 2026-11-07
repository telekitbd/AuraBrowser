package com.aura.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ImageView
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
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Message
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap

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
    private lateinit var btnStar: ImageView
    private lateinit var btnRefresh: ImageView

    private var fullscreen = false
    private var adblockOn = true
    private var popupBlock = true
    private var adultOn = true
    private var cosmetic = true
    private var adDomains: Set<String> = emptySet()
    private var adultDomains: Set<String> = emptySet()
    private var adAllow: Set<String> = emptySet()
    private val pendingBlocked = AtomicLong(0)
    private var lastDark = false
    private var lastDesktop = false
    private lateinit var homeStatus: TextView
    private lateinit var homeSearch: LinearLayout
    private lateinit var homeMic: ImageView
    private lateinit var quickRow: LinearLayout
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

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val list = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = list?.firstOrNull()
            if (r.resultCode == RESULT_OK && !text.isNullOrBlank()) {
                if (cur == null) selectTab(newTab())
                go(text)
            }
        }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        loadPrefs()
        lastDark = store.getBool("dark", false)
        lastDesktop = store.getBool("desktop", false)
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
        homeStatus = findViewById(R.id.homeStatus)
        homeSearch = findViewById(R.id.homeSearch)
        homeMic = findViewById(R.id.homeMic)
        quickRow = findViewById(R.id.quickRow)

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
        setupHome()

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
        loadPrefs()
        for (t in tabs) applySettings(t)
        val dk = store.getBool("dark", false)
        if (dk != lastDark) {
            lastDark = dk
            for (t in tabs) if (!t.isHome) t.webView.evaluateJavascript(if (dk) DARK_JS else LIGHT_JS, null)
        }
        val ds = store.getBool("desktop", false)
        if (ds != lastDesktop) {
            lastDesktop = ds
            for (t in tabs) if (!t.isHome) t.webView.reload()
        }
        if (store.getBool("keepon", false)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateHomeStatus()
        updateUi()
    }

    override fun onPause() {
        store.addBlocked(pendingBlocked.getAndSet(0))
        super.onPause()
    }

    override fun onDestroy() {
        store.addBlocked(pendingBlocked.getAndSet(0))
        if (isFinishing && store.getBool("clearexit", false)) wipeData()
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
        s.setSupportMultipleWindows(true)
        s.allowFileAccess = false
        applySettings(tab)
        wv.addJavascriptInterface(PickBridge(tab), "AuraBridge")
        wv.setOnLongClickListener { longPress(wv) }

        wv.setDownloadListener { url, ua, cd, mime, _ -> startDownload(url, ua, cd, mime) }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (adultOn && AdultFilter.isBlocked(u, adultDomains)) {
                    val bh = u.host ?: ""
                    view.loadDataWithBaseURL(null, AdultFilter.blockedPage(bh), "text/html", "utf-8", null)
                    return true
                }
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
                val u = request.url
                val host = u.host ?: return null
                if (adultOn && AdultFilter.isBlocked(u, adultDomains)) {
                    return if (request.isForMainFrame) {
                        WebResourceResponse(
                            "text/html", "utf-8",
                            ByteArrayInputStream(AdultFilter.blockedPage(host).toByteArray())
                        )
                    } else emptyResponse()
                }
                if (adblockOn && !adAllow.contains(normHost(hostOf(tab.url))) &&
                    (AdBlock.matches(host, AdBlock.SET) || AdBlock.matches(host, adDomains))
                ) {
                    tab.blocked++
                    pendingBlocked.incrementAndGet()
                    return emptyResponse()
                }
                return null
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                applyHide(tab)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tab.url = url ?: ""
                tab.loading = true
                tab.progress = 10
                tab.blocked = 0
                if (tab === cur) updateUi()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tab.loading = false
                tab.progress = 100
                tab.url = url ?: tab.url
                tab.title = view.title ?: tab.url
                val blockedPage = adultOn && AdultFilter.isBlocked(tab.url, adultDomains)
                if (blockedPage) tab.title = "Blocked"
                if (!tab.incognito && !blockedPage) store.addHistory(tab.title, tab.url)
                if (store.getBool("dark", false) && !blockedPage) view.evaluateJavascript(DARK_JS, null)
                applyHide(tab)
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
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
            ): Boolean {
                if (popupBlock && !isUserGesture) {
                    toast("Pop-up blocked")
                    return false
                }
                val nt = newTab(tab.incognito)
                nt.isHome = false
                (resultMsg.obj as WebView.WebViewTransport).webView = nt.webView
                resultMsg.sendToTarget()
                selectTab(nt)
                return true
            }

            override fun onCloseWindow(window: WebView) {
                val t = tabs.firstOrNull { it.webView === window } ?: return
                if (tabs.size > 1) closeTab(t) else goHome()
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
                if (tab === cur) progress.progress = newProgress
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                val u = view.url ?: return
                if (icon != null && u.startsWith("http")) {
                    FaviconCache.save(this@MainActivity, hostOf(u), icon)
                    if (cur?.isHome == true) renderDial()
                }
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
        val st = t.webView.settings
        st.userAgentString = if (store.getBool("desktop", false)) DESKTOP_UA else mobileUa
        st.blockNetworkImage = store.getBool("blockimg", false)
        st.textZoom = store.getInt("zoom", 100)
        st.javaScriptEnabled = store.getBool("js", true)
        st.setSafeBrowsingEnabled(store.getBool("safebrowsing", true))
        CookieManager.getInstance().setAcceptThirdPartyCookies(t.webView, !store.getBool("block3p", false))
    }

    private fun selectTab(t: BTab) {
        cur?.let { captureThumb(it) }
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
        btnStar.setImageResource(if (marked) R.drawable.ic_star else R.drawable.ic_star_border)
        btnStar.setColorFilter(if (marked) 0xFFFFC107.toInt() else Color.WHITE)
        btnRefresh.setImageResource(if (t.loading && !t.isHome) R.drawable.ic_close else R.drawable.ic_refresh)
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
        return searchUrl(Uri.encode(t))
    }

    private fun searchUrl(q: String): String {
        val safe = adultOn
        return when (store.getInt("engine", 0)) {
            1 -> "https://www.bing.com/search?q=$q" + if (safe) "&adlt=strict" else ""
            2 -> "https://duckduckgo.com/?q=$q" + if (safe) "&kp=1" else ""
            3 -> "https://search.brave.com/search?q=$q" + if (safe) "&safesearch=strict" else ""
            4 -> "https://search.yahoo.com/search?p=$q" + if (safe) "&vm=r" else ""
            else -> "https://www.google.com/search?q=$q" + if (safe) "&safe=active" else ""
        }
    }

    private fun upgrade(u: String): String =
        if (store.getBool("httpsonly", false) && u.startsWith("http://")) "https://" + u.removePrefix("http://") else u

    private fun go(input: String) {
        val t = cur ?: return
        if (input.isBlank()) return
        val u = upgrade(resolve(input))
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
            cell.addView(siteIcon(d.title, d.url, 52))
            FaviconCache.ensure(this, hostOf(d.url)) { renderDial() }
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

    private fun captureThumb(t: BTab) {
        try {
            if (t.isHome || t.webView.width <= 0 || t.webView.height <= 0) return
            val w = t.webView.width / 3
            val h = t.webView.height / 3
            if (w <= 0 || h <= 0) return
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(1f / 3f, 1f / 3f)
            t.webView.draw(c)
            t.thumb = bmp
        } catch (_: Exception) {
        }
    }

    private fun showTabs() {
        cur?.let { captureThumb(it) }
        var priv = cur?.incognito == true
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF1A1A1A.toInt())

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.setPadding(0, dp(16), 0, dp(4))

        fun tabBtn(res: Int): LinearLayout {
            val l = LinearLayout(this)
            l.gravity = Gravity.CENTER
            l.setPadding(0, dp(12), 0, dp(12))
            l.addView(iconView(res, 26))
            return l
        }

        val tNormal = tabBtn(R.drawable.ic_face)
        val tPriv = tabBtn(R.drawable.ic_incognito)
        head.addView(tNormal, LinearLayout.LayoutParams(0, wrap, 1f))
        head.addView(tPriv, LinearLayout.LayoutParams(0, wrap, 1f))

        val grid = GridLayout(this)
        grid.columnCount = 2
        grid.setPadding(dp(10), dp(8), dp(10), dp(8))
        val sv = ScrollView(this)
        sv.addView(grid)
        val cardW = (resources.displayMetrics.widthPixels - dp(20)) / 2

        val foot = LinearLayout(this)
        foot.orientation = LinearLayout.HORIZONTAL
        foot.setBackgroundColor(0xFF1A1A1A.toInt())
        val bDel = tv("Delete all", 17f)
        bDel.gravity = Gravity.CENTER
        bDel.setPadding(0, dp(18), 0, dp(18))
        val bNew = tv("New tab", 17f)
        bNew.gravity = Gravity.CENTER
        bNew.setPadding(0, dp(18), 0, dp(18))
        foot.addView(bDel, LinearLayout.LayoutParams(0, wrap, 1f))
        foot.addView(bNew, LinearLayout.LayoutParams(0, wrap, 1f))

        root.addView(head)
        root.addView(sv, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(foot)

        fun rebuild() {
            tNormal.alpha = if (priv) 0.4f else 1f
            tPriv.alpha = if (priv) 1f else 0.4f
            tNormal.setBackgroundColor(if (priv) Color.TRANSPARENT else 0xFF2A2A3A.toInt())
            tPriv.setBackgroundColor(if (priv) 0xFF2A2A3A.toInt() else Color.TRANSPARENT)
            grid.removeAllViews()
            for (t in tabs.filter { it.incognito == priv }) {
                val outer = LinearLayout(this)
                outer.orientation = LinearLayout.VERTICAL
                outer.setPadding(dp(4), dp(4), dp(4), dp(4))
                val lp = GridLayout.LayoutParams()
                lp.width = cardW
                lp.height = wrap
                outer.layoutParams = lp

                val card = LinearLayout(this)
                card.orientation = LinearLayout.VERTICAL
                card.setPadding(dp(6), dp(6), dp(6), dp(6))
                val bg = GradientDrawable()
                bg.setColor(0xFF2B2B2B.toInt())
                bg.cornerRadius = dp(16).toFloat()
                if (t === cur) bg.setStroke(dp(3), 0xFF9FA8FF.toInt())
                card.background = bg

                val header = LinearLayout(this)
                header.orientation = LinearLayout.HORIZONTAL
                header.gravity = Gravity.CENTER_VERTICAL
                val fav: View = if (t.isHome) iconView(R.drawable.ic_home, 18) else siteIcon(t.title, t.url, 20)
                val ttl = tv(if (t.isHome) "Home" else t.title.ifBlank { t.url }, 13f)
                ttl.maxLines = 1
                ttl.ellipsize = TextUtils.TruncateAt.END
                ttl.setPadding(dp(8), 0, dp(4), 0)
                val x = iconButton(R.drawable.ic_close, 32, 32, 18)
                x.setOnClickListener {
                    closeTab(t)
                    rebuild()
                }
                header.addView(fav)
                header.addView(ttl, LinearLayout.LayoutParams(0, wrap, 1f))
                header.addView(x)

                val thumb = ImageView(this)
                val tbg = GradientDrawable()
                tbg.setColor(0xFF000000.toInt())
                tbg.cornerRadius = dp(12).toFloat()
                thumb.background = tbg
                thumb.clipToOutline = true
                val bmp = t.thumb
                if (bmp != null && !t.isHome) {
                    thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                    thumb.setImageBitmap(bmp)
                } else {
                    thumb.scaleType = ImageView.ScaleType.CENTER
                    thumb.setImageResource(if (t.isHome) R.drawable.ic_home else R.drawable.ic_desktop)
                    thumb.setColorFilter(0xFF555555.toInt())
                }
                val tlp = LinearLayout.LayoutParams(match, dp(190))
                tlp.topMargin = dp(6)

                card.addView(header)
                card.addView(thumb, tlp)
                card.setOnClickListener {
                    selectTab(t)
                    dlg.dismiss()
                }
                outer.addView(card)
                grid.addView(outer)
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
        grid.setPadding(dp(4), dp(16), dp(4), dp(20))
        val w = resources.displayMetrics.widthPixels / 5 - 1
        val on = 0xFF9FA8FF.toInt()

        fun add(icon: Int, label: String, active: Boolean = false, action: () -> Unit) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(dp(2), dp(12), dp(2), dp(12))
            val i = iconView(icon, 26, if (active) on else Color.WHITE)
            val l = tv(label, 12f, if (active) on else 0xFFDDDDDD.toInt())
            l.gravity = Gravity.CENTER
            l.maxLines = 2
            l.setPadding(0, dp(8), 0, 0)
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

        add(R.drawable.ic_history, "History") { openItems("history") }
        add(R.drawable.ic_download, "Downloads") { openDownloads() }
        add(R.drawable.ic_add_box, "New tab") { selectTab(newTab()) }
        add(R.drawable.ic_incognito, "Private tab") { selectTab(newTab(true)) }
        add(R.drawable.ic_settings, "Settings") { openSettings() }
        add(R.drawable.ic_shield, "Ad blocker", adblockOn) { adBlockDialog() }
        add(R.drawable.ic_pick, "Hide element") { startPicker() }
        add(R.drawable.ic_block, "Adult block", adultOn) { toggleAdult() }
        add(R.drawable.ic_reader, "Reader mode") { readerMode() }
        add(R.drawable.ic_mic, "Voice search") { voiceSearch() }
        add(R.drawable.ic_fullscreen, "Full screen") {
            setFullscreen(true)
            toast("Press back to exit full screen")
        }
        add(R.drawable.ic_moon, "Dark mode", store.getBool("dark", false)) { toggleDark() }
        add(R.drawable.ic_translate, "Translate") { translate() }
        add(R.drawable.ic_share, "Share URL") { shareUrl() }
        add(R.drawable.ic_copy, "Copy link") { copyLink() }
        add(R.drawable.ic_search, "Find in page") { openFind() }
        add(R.drawable.ic_print, "Print / PDF") { printPage() }
        add(R.drawable.ic_playlist_add, "Add to home") { addToDial() }
        add(R.drawable.ic_image, "Block images", store.getBool("blockimg", false)) {
            val v = !store.getBool("blockimg", false)
            store.setBool("blockimg", v)
            for (t in tabs) applySettings(t)
            toast(if (v) "Images blocked" else "Images allowed")
        }
        add(R.drawable.ic_desktop, "Desktop", store.getBool("desktop", false)) { toggleDesktop() }
        add(R.drawable.ic_screenshot, "Screenshot") { screenshot() }
        add(R.drawable.ic_brightness, "Brightness") { brightnessDialog() }
        add(R.drawable.ic_text_size, "Text size") { cycleTextSize() }
        add(R.drawable.ic_code, "Page source") { pageSource() }
        add(R.drawable.ic_delete, "Clear data") { clearData() }
        add(R.drawable.ic_power, "Exit") { finishAffinity() }

        val sv = ScrollView(this)
        val sheetBg = GradientDrawable()
        sheetBg.setColor(0xFF1F1F1F.toInt())
        val r = dp(28).toFloat()
        sheetBg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        sv.background = sheetBg
        sv.addView(grid)
        dlg.setContentView(sv)
        dlg.show()
        dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun toggleDark() {
        val v = !store.getBool("dark", false)
        store.setBool("dark", v)
        lastDark = v
        for (t in tabs) {
            if (!t.isHome) t.webView.evaluateJavascript(if (v) DARK_JS else LIGHT_JS, null)
        }
        toast(if (v) "Dark mode on" else "Dark mode off")
    }

    private fun toggleDesktop() {
        val v = !store.getBool("desktop", false)
        store.setBool("desktop", v)
        lastDesktop = v
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
                wipeData()
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

    // ------------------------------------------------------------------ prefs / home

    private fun loadPrefs() {
        adblockOn = store.getBool("adblock", true)
        popupBlock = store.getBool("popup", true)
        adultOn = store.getBool("adult", true)
        cosmetic = store.getBool("cosmetic", true)
        adDomains = store.strings("addom").toHashSet()
        adultDomains = store.strings("adultdom").toHashSet()
        adAllow = store.strings("adallow").toHashSet()
    }

    private fun normHost(h: String): String = h.lowercase().removePrefix("www.")

    private fun emptyResponse() =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun escHtml(t: String) =
        t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun setupHome() {
        homeSearch.setOnClickListener {
            urlBar.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT)
        }
        homeMic.setOnClickListener { voiceSearch() }
        quickRow.removeAllViews()
        fun q(icon: Int, label: String, action: () -> Unit) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            val circle = GradientDrawable()
            circle.shape = GradientDrawable.OVAL
            circle.setColor(0xFF1E1E24.toInt())
            val ic = iconButton(icon, 52, 52, 24)
            ic.background = circle
            val l = tv(label, 12f, 0xFFAAAAAA.toInt())
            l.gravity = Gravity.CENTER
            l.maxLines = 1
            l.setPadding(0, dp(6), 0, 0)
            cell.addView(ic)
            cell.addView(l)
            cell.setOnClickListener { action() }
            quickRow.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        q(R.drawable.ic_download, "Downloads") { openDownloads() }
        q(R.drawable.ic_bookmark, "Bookmarks") { openItems("bookmark") }
        q(R.drawable.ic_history, "History") { openItems("history") }
        q(R.drawable.ic_shield, "Ad block") { adBlockDialog() }
        q(R.drawable.ic_settings, "Settings") { openSettings() }
        updateHomeStatus()
    }

    private fun updateHomeStatus() {
        val total = store.getBlockedTotal() + pendingBlocked.get()
        homeStatus.text = "Ads: " + (if (adblockOn) "ON" else "OFF") +
            "   •   Adult block: " + (if (adultOn) "ON" else "OFF") +
            "   •   " + total + " blocked"
    }

    private fun openDownloads() {
        startActivity(Intent(this, DownloadsActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openList(mode: String) {
        startActivity(Intent(this, ManageActivity::class.java).putExtra("mode", mode))
    }

    // ------------------------------------------------------------------ downloads

    private fun startDownload(url: String, ua: String?, cd: String?, mime: String?) {
        if (!url.startsWith("http")) {
            toast("This link type cannot be downloaded")
            return
        }
        if (adultOn && AdultFilter.isBlocked(url, adultDomains)) {
            toast("Blocked")
            return
        }
        try {
            val req = DownloadManager.Request(Uri.parse(url))
            if (mime != null) req.setMimeType(mime)
            if (ua != null) req.addRequestHeader("User-Agent", ua)
            val ck = CookieManager.getInstance().getCookie(url)
            if (ck != null) req.addRequestHeader("Cookie", ck)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val name = URLUtil.guessFileName(url, cd, mime)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            store.addDownload(id, name, url)
            toast("Downloading $name")
        } catch (e: Exception) {
            toast("Download failed")
        }
    }

    // ------------------------------------------------------------------ long press menu

    private fun longPress(wv: WebView): Boolean {
        val r = wv.hitTestResult
        val extra = r.extra ?: return false
        if (!extra.startsWith("http")) return false
        return when (r.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                linkMenu(extra, false)
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                linkMenu(extra, true)
                true
            }
            else -> false
        }
    }

    private fun linkMenu(url: String, image: Boolean) {
        val labels = if (image)
            arrayOf("Open image in new tab", "Download image", "Copy image link", "Share image link")
        else
            arrayOf("Open in new tab", "Open in private tab", "Copy link", "Share link", "Download link")
        AlertDialog.Builder(this)
            .setTitle(hostOf(url))
            .setItems(labels) { _, w ->
                if (image) {
                    when (w) {
                        0 -> {
                            selectTab(newTab(cur?.incognito == true))
                            go(url)
                        }
                        1 -> {
                            val ext = MimeTypeMap.getFileExtensionFromUrl(url)
                            val mt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
                            startDownload(url, null, null, mt)
                        }
                        2 -> copyText(url)
                        3 -> shareText(url)
                    }
                } else {
                    when (w) {
                        0 -> {
                            selectTab(newTab(cur?.incognito == true))
                            go(url)
                        }
                        1 -> {
                            selectTab(newTab(true))
                            go(url)
                        }
                        2 -> copyText(url)
                        3 -> shareText(url)
                        4 -> startDownload(url, null, null, null)
                    }
                }
            }
            .show()
    }

    private fun copyText(t: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", t))
        toast("Copied")
    }

    private fun shareText(t: String) {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, t)
        startActivity(Intent.createChooser(i, "Share"))
    }

    private fun copyLink() {
        val t = cur ?: return
        if (t.isHome || t.url.isEmpty()) {
            toast("Open a page first")
            return
        }
        copyText(t.url)
    }

    // ------------------------------------------------------------------ ad blocker (manual + auto)

    private fun adBlockDialog() {
        val t = cur
        val host = if (t != null && !t.isHome && t.url.startsWith("http")) normHost(hostOf(t.url)) else null
        val labels = mutableListOf<String>()
        val acts = mutableListOf<() -> Unit>()

        labels.add(if (adblockOn) "Turn ad blocker OFF" else "Turn ad blocker ON")
        acts.add {
            adblockOn = !adblockOn
            store.setBool("adblock", adblockOn)
            updateHomeStatus()
            toast(if (adblockOn) "Ad blocker on" else "Ad blocker off")
        }
        labels.add("Hide an element on this page")
        acts.add { startPicker() }
        if (host != null) {
            val allowed = adAllow.contains(host)
            labels.add(if (allowed) "Block ads on $host again" else "Allow ads on $host")
            acts.add {
                if (allowed) store.removeString("adallow", host) else store.addString("adallow", host)
                adAllow = store.strings("adallow").toHashSet()
                t?.webView?.reload()
            }
        }
        labels.add("Hidden elements (" + store.strings("hide").size + ")")
        acts.add { openList("hide") }
        labels.add("Blocked domains (" + store.strings("addom").size + ")")
        acts.add { openList("addomains") }
        val here = t?.blocked ?: 0
        labels.add("Blocked here: $here  •  total " + (store.getBlockedTotal() + pendingBlocked.get()))
        acts.add { }

        AlertDialog.Builder(this)
            .setTitle("Ad blocker")
            .setItems(labels.toTypedArray()) { _, w -> acts[w]() }
            .show()
    }

    private fun startPicker() {
        val t = cur
        if (t == null || t.isHome) {
            toast("Open a page first")
            return
        }
        t.picking = true
        t.webView.evaluateJavascript(AdBlock.PICKER_JS, null)
    }

    inner class PickBridge(private val tab: BTab) {
        @JavascriptInterface
        fun picked(selector: String) {
            runOnUiThread {
                if (!tab.picking) return@runOnUiThread
                tab.picking = false
                val sel = selector.trim()
                if (sel.isEmpty() || sel.length > 600 || sel.contains("\n")) return@runOnUiThread
                val host = normHost(hostOf(tab.url))
                if (host.isEmpty()) return@runOnUiThread
                store.addHideRule(host, sel)
                toast("Element hidden")
            }
        }

        @JavascriptInterface
        fun cancelled() {
            runOnUiThread { tab.picking = false }
        }
    }

    private fun applyHide(t: BTab) {
        val url = t.url
        if (!url.startsWith("http")) return
        val host = normHost(hostOf(url))
        val sb = StringBuilder()
        if (adblockOn && cosmetic && !adAllow.contains(host)) sb.append(AdBlock.COSMETIC_CSS).append('\n')
        for (r in store.getHideRules(host)) sb.append(r).append("{display:none!important}\n")
        if (sb.isNotEmpty()) t.webView.evaluateJavascript(AdBlock.styleJs(sb.toString()), null)
    }

    // ------------------------------------------------------------------ adult block / pin

    private fun toggleAdult() {
        if (adultOn) {
            askPin {
                adultOn = false
                store.setBool("adult", false)
                updateHomeStatus()
                toast("Adult site block OFF")
            }
        } else {
            adultOn = true
            store.setBool("adult", true)
            updateHomeStatus()
            toast("Adult site block ON")
        }
    }

    // ------------------------------------------------------------------ reader / voice / wipe

    private fun readerMode() {
        val t = cur
        if (t == null || t.isHome) {
            toast("Open a page first")
            return
        }
        t.webView.evaluateJavascript(AdBlock.READER_JS) { r ->
            try {
                val json = JSONObject(JSONArray("[$r]").getString(0))
                val body = json.optString("body")
                if (body.length < 200) {
                    toast("Reader mode is not available for this page")
                    return@evaluateJavascript
                }
                val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<style>body{margin:auto;padding:18px;max-width:720px;background:#121212;color:#e6e6e6;" +
                    "font:18px/1.7 serif}h1,h2,h3{font-family:sans-serif;line-height:1.3}" +
                    "img{max-width:100%;height:auto;border-radius:8px}" +
                    "blockquote{border-left:3px solid #7C8CFF;margin:0;padding-left:14px;color:#bbb}" +
                    "</style></head><body><h1>" + escHtml(json.optString("title")) + "</h1>" + body +
                    "</body></html>"
                t.webView.loadDataWithBaseURL(t.url, html, "text/html", "utf-8", t.url)
            } catch (e: Exception) {
                toast("Reader mode failed")
            }
        }
    }

    private fun voiceSearch() {
        try {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Search or say a website")
            voiceLauncher.launch(i)
        } catch (e: Exception) {
            toast("Voice search is not available")
        }
    }

    private fun wipeData() {
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        for (t in tabs) {
            try {
                t.webView.clearCache(true)
                t.webView.clearHistory()
                t.webView.clearFormData()
            } catch (_: Exception) {
            }
        }
        store.clearHistory()
    }
}
