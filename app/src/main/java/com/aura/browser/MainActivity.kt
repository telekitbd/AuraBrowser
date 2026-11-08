package com.aura.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.Settings
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
import android.text.format.Formatter
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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

private class BarItem(val res: Int, val color: Int, val action: () -> Unit)

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

        private val VIDEO_EXT = Regex("\\.(mp4|webm|m4v|mov|mkv|m3u8|mpd|flv)(\\?|#|$)", RegexOption.IGNORE_CASE)

        private const val LIGHT_JS =
            "var e=document.getElementById('aura-dark');if(e)e.remove();"

        private val SF_NAMES = listOf("Off", "Night (amber)", "Reading (paper)", "Eye care (green)", "Blue-light cut", "Dim (black)", "Red night")
        private val SF_COLORS = intArrayOf(0, 0xFF8C00, 0xC8A060, 0x4CAF50, 0xFFB300, 0x000000, 0xFF0000)
        private val TTS_LANGS = listOf(
            "Auto (page / device)" to "", "English" to "en-US", "Bangla" to "bn-BD", "Hindi" to "hi-IN",
            "Urdu" to "ur-PK", "Arabic" to "ar", "Spanish" to "es-ES", "French" to "fr-FR",
            "German" to "de-DE", "Russian" to "ru-RU", "Chinese" to "zh-CN", "Japanese" to "ja-JP"
        )
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
    private var popupLevel = 2
    private var ovlOn = true
    private var elemOn = true
    private var appExec = 0
    private var blockArea = false
    private var popAllow: Set<String> = emptySet()
    private var ovlAllow: Set<String> = emptySet()
    private var cosmetic = true
    private var adDomains: Set<String> = emptySet()
    private var adAllow: Set<String> = emptySet()
    private val pendingBlocked = AtomicLong(0)
    private var lastDark = false
    private var lastDesktop = false
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var speech: SpeechController? = null
    private var readBar: LinearLayout? = null
    private var readPlay: TextView? = null
    private var filterView: View? = null
    private var oneHand = false
    private var handPanel: View? = null

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
        Doh.endpoint = store.getStr("dns_url", "")
        AdLists.load(applicationContext)
        if (store.getInt("vpn_mode", 0) != 0) {
            VpnProxy.apply(applicationContext, store) { msg -> runOnUiThread { toast(msg) } }
        }
        applyScreenFilter()
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
        updateUi()
    }

    override fun onPause() {
        store.addBlocked(pendingBlocked.getAndSet(0))
        super.onPause()
    }

    override fun onDestroy() {
        speech?.shutdown()
        speech = null
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
        if (oneHand) {
            exitOneHand()
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
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(wv, AdBlock.TAP_JS, setOf("*"))
                if (store.getBool("adblock", true)) {
                    WebViewCompat.addDocumentStartJavaScript(
                        wv, AdBlock.YT_JS,
                        setOf("https://www.youtube.com", "https://m.youtube.com")
                    )
                }
            }
        } catch (_: Exception) {
        }
        wv.setOnLongClickListener { longPress(wv) }
        wv.setOnTouchListener { _, _ ->
            tab.lastTouch = System.currentTimeMillis()
            false
        }

        wv.setDownloadListener { url, ua, cd, mime, _ -> startDownload(url, ua, cd, mime) }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val scheme = u.scheme ?: return false
                val pageHostNav = normHost(hostOf(tab.url))
                if (scheme == "http" || scheme == "https") {
                    val navHost = u.host ?: return false
                    if (adblockOn && !adAllow.contains(pageHostNav) && isAdHost(navHost)) {
                        tab.blocked++
                        pendingBlocked.incrementAndGet()
                        store.logBlocked(u.toString())
                        return true
                    }
                    // page redirecting on its own (no tap) to another site = ad redirect
                    if (popupLevel == 2 && request.isForMainFrame && !request.hasGesture() &&
                        !request.isRedirect && !tab.loading && pageHostNav.isNotEmpty() &&
                        !popAllow.contains(pageHostNav) &&
                        !AdBlock.sameSite(pageHostNav, normHost(navHost)) &&
                        !AdBlock.isTrustedPopup(navHost) &&
                        System.currentTimeMillis() - tab.lastTouch > 5000
                    ) {
                        tab.blocked++
                        pendingBlocked.incrementAndGet()
                        store.logBlocked(u.toString())
                        toast("Auto-redirect blocked")
                        return true
                    }
                    return false
                }
                if (scheme == "about" || scheme == "file" || scheme == "data" ||
                    scheme == "blob" || scheme == "javascript"
                ) return false
                val plain = scheme == "tel" || scheme == "mailto" || scheme == "sms" || scheme == "smsto"
                if (plain) {
                    if (request.hasGesture()) launchExternal(u)
                    return true
                }
                when (appExec) {
                    2 -> launchExternal(u)
                    1 -> AlertDialog.Builder(this@MainActivity)
                        .setTitle("Open another app?")
                        .setMessage(u.toString().take(120))
                        .setPositiveButton("Open") { _, _ -> launchExternal(u) }
                        .setNegativeButton("Block", null)
                        .show()
                    else -> {
                        store.logBlocked(u.toString())
                        toast("App launch blocked")
                    }
                }
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                val u = request.url
                val host = u.host ?: return null
                if (adBlocked(tab.url, u, request.isForMainFrame)) {
                    tab.blocked++
                    pendingBlocked.incrementAndGet()
                    return emptyResponse()
                }
                if (!request.isForMainFrame && VIDEO_EXT.containsMatchIn(u.toString())) {
                    addVideo(tab, u.toString(), "")
                }
                if (Doh.endpoint.isNotEmpty() && Doh.isBlocked(host)) {
                    return if (request.isForMainFrame) {
                        WebResourceResponse(
                            "text/html", "utf-8",
                            ByteArrayInputStream(dnsBlockedPage(host).toByteArray())
                        )
                    } else emptyResponse()
                }
                return null
            }

            override fun onSafeBrowsingHit(
                view: WebView, request: WebResourceRequest, threatType: Int,
                callback: android.webkit.SafeBrowsingResponse
            ) {
                callback.proceed(false)
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                applyHide(tab)
                injectAnti(tab)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tab.url = url ?: ""
                tab.loading = true
                tab.progress = 10
                tab.blocked = 0
                tab.videos.clear()
                runOnUiThread { updateVideoBtn() }
                injectAnti(tab)
                if (tab === cur) updateUi()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tab.loading = false
                tab.progress = 100
                tab.url = url ?: tab.url
                tab.title = view.title ?: tab.url
                if (!tab.incognito) store.addHistory(tab.title, tab.url)
                if (store.getBool("dark", false)) view.evaluateJavascript(DARK_JS, null)
                applyHide(tab)
                injectAnti(tab)
                if (tab === cur) updateUi()
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    val ec = error.errorCode
                    if (store.getInt("vpn_mode", 0) == 1 && VpnProxy.active.isNotEmpty() &&
                        (ec == WebViewClient.ERROR_CONNECT || ec == WebViewClient.ERROR_TIMEOUT || ec == WebViewClient.ERROR_IO ||
                            ec == WebViewClient.ERROR_PROXY_AUTHENTICATION || ec == WebViewClient.ERROR_UNKNOWN) &&
                        VpnProxy.shouldRetry()
                    ) {
                        toast("Server not responding - switching...")
                        VpnProxy.findFree(store) { msg ->
                            runOnUiThread {
                                toast(msg)
                                if (VpnProxy.active.isNotEmpty()) view.reload()
                            }
                        }
                    }
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
                val hit = view.hitTestResult.type
                val fromLink = hit == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                    hit == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                val level = if (popAllow.contains(normHost(hostOf(tab.url)))) 0 else popupLevel
                if (level == 0 || fromLink) {
                    // a real link tap (or blocker turned off): open normally
                    val nt = newTab(tab.incognito)
                    nt.isHome = false
                    (resultMsg.obj as WebView.WebViewTransport).webView = nt.webView
                    resultMsg.sendToTarget()
                    selectTab(nt)
                    return true
                }
                // Script-opened window (typical pop-up / pop-under ad that fires on the first tap
                // on a video). Look at the target first and only open it when it is the same
                // site or a login pop-up.
                val probe = WebView(this@MainActivity)
                val decided = java.util.concurrent.atomic.AtomicBoolean(false)
                fun decide(url: String) {
                    if (url.isBlank() || url.startsWith("about:")) return
                    if (!decided.compareAndSet(false, true)) return
                    val h = Uri.parse(url).host ?: ""
                    val pageHost = normHost(hostOf(tab.url))
                    val cross = h.isNotEmpty() && pageHost.isNotEmpty() &&
                        !AdBlock.sameSite(pageHost, normHost(h))
                    val bad = adblockOn && (isAdHost(h) || AdBlock.looksLikeAd(url))
                    val allow = !bad && (AdBlock.isTrustedPopup(h) || (level == 1 && !cross))
                    runOnUiThread {
                        try {
                            probe.stopLoading()
                            probe.destroy()
                        } catch (_: Exception) {
                        }
                        if (allow) {
                            val nt = newTab(tab.incognito)
                            nt.isHome = false
                            selectTab(nt)
                            go(url)
                        } else {
                            tab.blocked++
                            pendingBlocked.incrementAndGet()
                            store.logBlocked(url)
                            toast("Pop-up blocked")
                        }
                    }
                }
                probe.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        decide(request.url.toString())
                        return true
                    }

                    override fun shouldInterceptRequest(
                        v: WebView, request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (request.isForMainFrame) decide(request.url.toString())
                        return emptyResponse()
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = probe
                resultMsg.sendToTarget()
                probe.postDelayed({
                    if (decided.compareAndSet(false, true)) {
                        try {
                            probe.destroy()
                        } catch (_: Exception) {
                        }
                    }
                }, 8000)
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

            override fun getDefaultVideoPoster(): Bitmap? =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCb = callback
                view.setBackgroundColor(Color.BLACK)
                val wrap = GestureFrame(this@MainActivity)
                wrap.addView(
                    view, 0, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                customWrap = wrap
                fsBrightness = window.attributes.screenBrightness
                (window.decorView as FrameLayout).addView(
                    wrap, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                setBars(true)
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    private fun launchExternal(u: Uri) {
        try {
            val i = if (u.scheme == "intent") {
                val x = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                x.addCategory(Intent.CATEGORY_BROWSABLE)
                x.component = null
                x.selector = null
                x
            } else Intent(Intent.ACTION_VIEW, u)
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun hideCustom() {
        val v = customView ?: return
        val w = customWrap
        if (w != null) {
            w.removeAllViews()
            (window.decorView as FrameLayout).removeView(w)
        } else {
            (window.decorView as FrameLayout).removeView(v)
        }
        customWrap = null
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
        setBars(fullscreen)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val lp = window.attributes
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        lp.screenBrightness = fsBrightness
        window.attributes = lp
        if (!store.getBool("keepon", false)) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applySettings(t: BTab) {
        val st = t.webView.settings
        st.userAgentString = if (store.getBool("desktop", false)) DESKTOP_UA else mobileUa
        st.blockNetworkImage = store.getBool("blockimg", false)
        st.textZoom = store.getInt("zoom", 100)
        st.javaScriptEnabled = store.getBool("js", true)
        st.setSafeBrowsingEnabled(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(t.webView, !store.getBool("block3p", false))
    }

    private fun selectTab(t: BTab) {
        cur?.let { captureThumb(it) }
        cur = t
        showContent()
        updateUi()
        updateVideoBtn()
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
        return when (store.getInt("engine", 0)) {
            1 -> "https://www.bing.com/search?q=$q"
            2 -> "https://duckduckgo.com/?q=$q"
            3 -> "https://search.brave.com/search?q=$q"
            4 -> "https://search.yahoo.com/search?p=$q"
            else -> "https://www.google.com/search?q=$q"
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
        grid.setPadding(dp(4), dp(10), dp(4), dp(14))
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
        add(R.drawable.ic_image_viewer, "Image viewer") { imageViewer() }
        add(R.drawable.ic_save_images, "Save all images") { saveAllImages() }

        add(R.drawable.ic_fullscreen, "Full screen") {
            setFullscreen(true)
            toast("Press back to exit full screen")
        }
        add(R.drawable.ic_moon, "Dark mode", store.getBool("dark", false)) { toggleDark() }
        add(R.drawable.ic_translate, "Translate") { translate() }
        add(R.drawable.ic_read_aloud, "Read aloud", speech?.playing == true) { readAloud() }
        add(R.drawable.ic_tts, "Text to Speech") { ttsDialog() }

        add(R.drawable.ic_playlist_add, "Add current page") { addCurrentPage() }
        add(R.drawable.ic_share, "Share URL") { shareUrl() }
        add(R.drawable.ic_search, "Find in page") { openFind() }
        add(R.drawable.ic_save_page, "Save page") { savePage() }
        add(R.drawable.ic_print, "Print / PDF") { printPage() }

        add(R.drawable.ic_dns, "DNS", Doh.endpoint.isNotEmpty()) { dnsDialog() }
        add(R.drawable.ic_shield, "Ad blocker", adblockOn) { adBlockDialog() }
        add(R.drawable.ic_pick, "Block area") { startPicker() }
        add(R.drawable.ic_image, "Block images", store.getBool("blockimg", false)) {
            val v = !store.getBool("blockimg", false)
            store.setBool("blockimg", v)
            for (t in tabs) applySettings(t)
            val c = cur
            if (c != null && !c.isHome) c.webView.reload()
            toast(if (v) "Images blocked" else "Images allowed")
        }
        add(R.drawable.ic_desktop, "Desktop", store.getBool("desktop", false)) { toggleDesktop() }

        add(R.drawable.ic_screenshot, "Screenshot") { screenshot() }
        add(R.drawable.ic_screen_filter, "Screen filter", store.getInt("sf_mode", 0) != 0) { screenFilterDialog() }
        add(R.drawable.ic_brightness, "Brightness") { brightnessDialog() }
        add(R.drawable.ic_text_size, "Text size") { textSizeDialog() }
        add(R.drawable.ic_text_zoom, "Text zoom in") { textZoomIn() }

        add(R.drawable.ic_image_editor, "Image editor") { imageEditor() }
        add(R.drawable.ic_one_hand, "One-handed mode", oneHand) { toggleOneHand() }
        add(R.drawable.ic_code, "Page source") { pageSource() }
        add(R.drawable.ic_memo, "Memo") { memo() }
        add(R.drawable.ic_delete, "Clear data") { clearData() }

        add(R.drawable.ic_refresh, "Update ad lists") { updateAdLists() }
        add(R.drawable.ic_lock, "VPN", store.getInt("vpn_mode", 0) != 0) { vpnDialog() }
        add(R.drawable.ic_mic, "Voice search") { voiceSearch() }
        add(R.drawable.ic_copy, "Copy link") { copyLink() }

        fun bar(items: List<BarItem>): LinearLayout {
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(4), dp(14), dp(4))
            for (item in items) {
                val act = item.action
                val b = iconButton(item.res, 56, 48, 24, item.color)
                b.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
                b.setOnClickListener {
                    dlg.dismiss()
                    act()
                }
                row.addView(b)
            }
            return row
        }

        fun divider(): View {
            val d = View(this)
            d.setBackgroundColor(0xFF333333.toInt())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            lp.setMargins(dp(14), 0, dp(14), 0)
            d.layoutParams = lp
            return d
        }

        val header = bar(
            listOf(
                BarItem(R.drawable.ic_refresh, Color.WHITE) {
                    val c = cur
                    if (c != null && !c.isHome) c.webView.reload() else toast("Open a page first")
                },
                BarItem(R.drawable.ic_shield, if (adblockOn) on else Color.WHITE) { adBlockDialog() },
                BarItem(R.drawable.ic_tabs, Color.WHITE) { showTabs() },
                BarItem(R.drawable.ic_settings, Color.WHITE) { openSettings() }
            )
        )
        val footer = bar(
            listOf(
                BarItem(R.drawable.ic_incognito, Color.WHITE) { selectTab(newTab(true)) },
                BarItem(R.drawable.ic_moon, if (store.getBool("dark", false)) on else Color.WHITE) { toggleDark() },
                BarItem(R.drawable.ic_bookmark, Color.WHITE) { openItems("bookmark") },
                BarItem(R.drawable.ic_reader, Color.WHITE) { readerMode() },
                BarItem(R.drawable.ic_power, Color.WHITE) { finishAffinity() }
            )
        )

        val sv = ScrollView(this)
        sv.addView(grid)
        val sheet = LinearLayout(this)
        sheet.orientation = LinearLayout.VERTICAL
        val sheetBg = GradientDrawable()
        sheetBg.setColor(0xFF1F1F1F.toInt())
        val r = dp(28).toFloat()
        sheetBg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        sheet.background = sheetBg
        sheet.addView(header)
        sheet.addView(divider())
        sheet.addView(
            sv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.62f).toInt()
            )
        )
        sheet.addView(divider())
        sheet.addView(footer)
        dlg.setContentView(sheet)
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

    private fun textSizeDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(20), dp(24), dp(8))
        val label = tv("Text size: " + store.getInt("zoom", 100) + "%", 15f)
        box.addView(label)
        val sb = SeekBar(this)
        sb.max = 50
        sb.progress = ((store.getInt("zoom", 100) - 50) / 5).coerceIn(0, 50)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val z = 50 + p * 5
                store.setInt("zoom", z)
                label.text = "Text size: $z%"
                for (t in tabs) applySettings(t)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(sb)
        AlertDialog.Builder(this)
            .setTitle("Text size")
            .setView(box)
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset") { _, _ ->
                store.setInt("zoom", 100)
                for (t in tabs) applySettings(t)
                toast("Text size 100%")
            }
            .show()
    }

    private fun textZoomIn() {
        val z = minOf(300, store.getInt("zoom", 100) + 10)
        store.setInt("zoom", z)
        for (t in tabs) applySettings(t)
        toast("Text zoom $z%")
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

    // ------------------------------------------------------------------ images

    private fun collectImages(cb: (List<String>) -> Unit) {
        val t = cur
        if (t == null || t.isHome) {
            toast("Open a page first")
            return
        }
        t.webView.evaluateJavascript(PageJs.IMAGES) { r ->
            val list = mutableListOf<String>()
            try {
                val arr = JSONArray(JSONArray("[$r]").getString(0))
                for (i in 0 until arr.length()) list.add(arr.getString(i))
            } catch (_: Exception) {
            }
            val clean = list
            ImageHolder.referer = t.url
            ImageHolder.ua = t.webView.settings.userAgentString ?: ""
            cb(clean)
        }
    }

    private fun imageViewer() {
        collectImages { l ->
            if (l.isEmpty()) {
                toast("No images found on this page")
            } else {
                ImageHolder.urls = l
                startActivity(Intent(this, ImagesActivity::class.java))
            }
        }
    }

    private fun saveAllImages() {
        collectImages { l ->
            if (l.isEmpty()) {
                toast("No images found on this page")
                return@collectImages
            }
            AlertDialog.Builder(this)
                .setTitle("Save all images")
                .setMessage("Save " + l.size + " images to Pictures/AuraBrowser?")
                .setPositiveButton("Save") { _, _ ->
                    val pd = AlertDialog.Builder(this)
                        .setTitle("Saving images")
                        .setMessage("0 / " + l.size)
                        .setCancelable(false)
                        .show()
                    ImgNet.saveAll(this, l, { n, total ->
                        pd.setMessage("$n / $total")
                    }, { ok, total ->
                        pd.dismiss()
                        toast("Saved $ok of $total images")
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun imageEditor() {
        val t = cur
        val labels = mutableListOf("Choose from gallery")
        if (t != null && !t.isHome) labels.add("Choose from this page's images")
        AlertDialog.Builder(this)
            .setTitle("Image editor")
            .setItems(labels.toTypedArray()) { _, w ->
                if (w == 0) {
                    startActivity(Intent(this, ImageEditorActivity::class.java))
                } else {
                    toast("Open an image and tap Edit")
                    imageViewer()
                }
            }
            .show()
    }

    // ------------------------------------------------------------------ read aloud / text to speech

    private fun ensureSpeech(): SpeechController {
        var s = speech
        if (s == null) {
            s = SpeechController(this) { playing -> updateReadBar(playing) }
            s.setRate(store.getInt("tts_rate", 100) / 100f)
            s.setPitch(store.getInt("tts_pitch", 100) / 100f)
            speech = s
        }
        return s
    }

    private fun applyVoiceLang(s: SpeechController, pageLang: String) {
        val chosen = store.getStr("tts_lang", "")
        val use = if (chosen.isNotEmpty()) chosen else pageLang
        if (use.isEmpty()) {
            s.setLanguage(null)
        } else if (!s.setLanguage(use)) {
            toast("Voice for '$use' is not installed; using default")
            s.setLanguage(null)
        }
    }

    private fun readAloud() {
        val t = cur
        if (t == null || t.isHome) {
            toast("Open a page first")
            return
        }
        t.webView.evaluateJavascript(PageJs.TEXT) { r ->
            try {
                val j = JSONObject(JSONArray("[$r]").getString(0))
                val text = j.optString("t").trim()
                if (text.length < 2) {
                    toast("Nothing to read on this page")
                    return@evaluateJavascript
                }
                val s = ensureSpeech()
                applyVoiceLang(s, j.optString("l"))
                s.speak(text)
                showReadBar()
                toast(if (j.optBoolean("s")) "Reading selected text" else "Reading page")
            } catch (e: Exception) {
                toast("Read aloud failed")
            }
        }
    }

    private fun showReadBar() {
        if (readBar != null) return
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(8), dp(4), dp(8), dp(4))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(28).toFloat()
        bg.setColor(0xF0262630.toInt())
        bar.background = bg
        bar.elevation = dp(8).toFloat()

        fun b(text: String, action: () -> Unit): TextView {
            val t = tv(text, 14f)
            t.setPadding(dp(14), dp(10), dp(14), dp(10))
            t.setOnClickListener { action() }
            bar.addView(t)
            return t
        }

        readPlay = b("Pause") {
            val s = speech
            if (s != null) {
                if (s.playing) s.pause() else s.resume()
            }
        }
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        var speedBtn: TextView? = null
        speedBtn = b(String.format("%.2fx", speech?.rate ?: 1f)) {
            val s = speech
            if (s != null) {
                val i = speeds.indexOfFirst { Math.abs(it - s.rate) < 0.01f }
                val nx = speeds[(i + 1) % speeds.size]
                val was = s.playing
                if (was) s.pause()
                s.setRate(nx)
                store.setInt("tts_rate", (nx * 100).toInt())
                speedBtn?.text = String.format("%.2fx", nx)
                if (was) s.resume()
            }
        }
        b("Stop") {
            speech?.stop()
            hideReadBar()
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        lp.bottomMargin = dp(16)
        content.addView(bar, lp)
        readBar = bar
    }

    private fun hideReadBar() {
        readBar?.let { content.removeView(it) }
        readBar = null
        readPlay = null
    }

    private fun updateReadBar(playing: Boolean) {
        readPlay?.text = if (playing) "Pause" else "Play"
    }

    private fun ttsDialog() {
        val t = cur
        if (t != null && !t.isHome) {
            t.webView.evaluateJavascript(PageJs.SELECTION) { r ->
                val sel = try {
                    JSONArray("[$r]").getString(0)
                } catch (e: Exception) {
                    ""
                }
                openTts(if (sel == "null") "" else sel)
            }
        } else {
            openTts("")
        }
    }

    private fun openTts(initial: String) {
        var text = initial
        if (text.isBlank()) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        }
        val s = ensureSpeech()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(10), dp(20), 0)
        val input = EditText(this)
        input.hint = "Type or paste text to speak"
        input.setText(text)
        input.minLines = 4
        input.maxLines = 8
        input.gravity = Gravity.TOP
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        box.addView(input)

        val rateLabel = tv("Speed: " + String.format("%.2fx", store.getInt("tts_rate", 100) / 100f), 13f, 0xFFBBBBBB.toInt())
        rateLabel.setPadding(0, dp(12), 0, 0)
        box.addView(rateLabel)
        val rateBar = SeekBar(this)
        rateBar.max = 150
        rateBar.progress = (store.getInt("tts_rate", 100) - 50).coerceIn(0, 150)
        rateBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 50 + p
                store.setInt("tts_rate", v)
                s.setRate(v / 100f)
                rateLabel.text = "Speed: " + String.format("%.2fx", v / 100f)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        box.addView(rateBar)

        val pitchLabel = tv("Pitch: " + String.format("%.2f", store.getInt("tts_pitch", 100) / 100f), 13f, 0xFFBBBBBB.toInt())
        box.addView(pitchLabel)
        val pitchBar = SeekBar(this)
        pitchBar.max = 150
        pitchBar.progress = (store.getInt("tts_pitch", 100) - 50).coerceIn(0, 150)
        pitchBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 50 + p
                store.setInt("tts_pitch", v)
                s.setPitch(v / 100f)
                pitchLabel.text = "Pitch: " + String.format("%.2f", v / 100f)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        box.addView(pitchBar)

        val langLabel = tv("Language", 13f, 0xFFBBBBBB.toInt())
        langLabel.setPadding(0, dp(8), 0, 0)
        box.addView(langLabel)
        val spin = Spinner(this)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, TTS_LANGS.map { it.first })
        val curLang = store.getStr("tts_lang", "")
        spin.setSelection(TTS_LANGS.indexOfFirst { it.second == curLang }.coerceAtLeast(0))
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                store.setStr("tts_lang", TTS_LANGS[pos].second)
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        box.addView(spin)

        val row = LinearLayout(this)
        row.setPadding(0, dp(12), 0, dp(4))
        fun btn(label: String, action: () -> Unit) {
            val b = tv(label, 14f)
            val bg = GradientDrawable()
            bg.cornerRadius = dp(20).toFloat()
            bg.setColor(0xFF2F3140.toInt())
            b.background = bg
            b.gravity = Gravity.CENTER
            b.setPadding(dp(8), dp(10), dp(8), dp(10))
            b.setOnClickListener { action() }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(6)
            row.addView(b, lp)
        }
        btn("Speak") {
            val x = input.text.toString().trim()
            if (x.isEmpty()) {
                toast("Enter some text")
            } else {
                applyVoiceLang(s, "")
                s.speak(x)
            }
        }
        btn("Stop") { s.stop() }
        btn("Save audio") {
            val x = input.text.toString().trim()
            if (x.isEmpty()) {
                toast("Enter some text")
            } else {
                applyVoiceLang(s, "")
                toast("Creating audio…")
                s.synthToFile(x) { f ->
                    if (f == null) {
                        toast("Could not create audio")
                    } else {
                        val ok = FileSaver.audioToMusic(this, "aura_tts_" + System.currentTimeMillis() + ".wav", f)
                        f.delete()
                        toast(if (ok) "Saved to Music/AuraBrowser" else "Could not save audio")
                    }
                }
            }
        }
        box.addView(row)

        val sv = ScrollView(this)
        sv.addView(box)
        AlertDialog.Builder(this)
            .setTitle("Text to Speech")
            .setView(sv)
            .setPositiveButton("Close", null)
            .show()
    }

    // ------------------------------------------------------------------ save page

    private fun savePage() {
        val t = cur
        if (t == null || t.isHome || !t.url.startsWith("http")) {
            toast("Open a page first")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Save page")
            .setItems(arrayOf("Web archive (.mht, single file)", "HTML (.html)", "PDF (via print)")) { _, w ->
                when (w) {
                    0 -> saveArchive(t)
                    1 -> saveHtml(t)
                    else -> printPage()
                }
            }
            .show()
    }

    private fun saveArchive(t: BTab) {
        val name = FileSaver.safeName(t.title.ifBlank { hostOf(t.url) })
        val f = java.io.File(cacheDir, "page_" + System.currentTimeMillis() + ".mht")
        t.webView.saveWebArchive(f.absolutePath, false) { p ->
            if (p == null) {
                toast("Could not save page")
                return@saveWebArchive
            }
            Thread {
                val ok = FileSaver.fileToDownloads(applicationContext, "$name.mht", "multipart/related", f)
                f.delete()
                runOnUiThread { toast(if (ok) "Saved to Downloads/AuraBrowser" else "Save failed") }
            }.start()
        }
    }

    private fun saveHtml(t: BTab) {
        val name = FileSaver.safeName(t.title.ifBlank { hostOf(t.url) })
        val pageUrl = t.url
        t.webView.evaluateJavascript("'<!DOCTYPE html>'+document.documentElement.outerHTML") { r ->
            var html = try {
                JSONArray("[$r]").getString(0)
            } catch (e: Exception) {
                ""
            }
            if (html.length < 30) {
                toast("Could not read the page")
                return@evaluateJavascript
            }
            val m = Regex("(?i)<head[^>]*>").find(html)
            if (m != null) {
                val at = m.range.last + 1
                html = html.substring(0, at) + "<base href=\"" + pageUrl + "\">" + html.substring(at)
            }
            val data = html.toByteArray()
            Thread {
                val ok = FileSaver.toDownloads(applicationContext, "$name.html", "text/html", data)
                runOnUiThread { toast(if (ok) "Saved to Downloads/AuraBrowser" else "Save failed") }
            }.start()
        }
    }

    // ------------------------------------------------------------------ DNS

    private fun dnsBlockedPage(host: String): String =
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='background:#000;color:#eee;font-family:sans-serif;padding:32px;text-align:center'>" +
            "<h2>Blocked by DNS filter</h2><p>" + escHtml(host) +
            "</p><p style='color:#888'>Your DNS provider blocks this site. Change it from Menu &gt; DNS.</p></body></html>"

    private fun dnsDialog() {
        val names = listOf(
            "Off (system DNS)",
            "Cloudflare Security (malware)",
            "Cloudflare Family (malware + adult)",
            "AdGuard DNS (ads + trackers)",
            "Google Public DNS (no filtering)",
            "Custom DoH (JSON) URL…"
        )
        val urls = listOf(
            "",
            "https://security.cloudflare-dns.com/dns-query",
            "https://family.cloudflare-dns.com/dns-query",
            "https://dns.adguard-dns.com/resolve",
            "https://dns.google/resolve",
            ""
        )
        val saved = store.getStr("dns_url", "")
        var checked = urls.indexOf(saved).let { if (it < 0 || (it == 0 && saved.isNotEmpty())) 5 else it }
        if (saved.isEmpty()) checked = 0

        fun applyDns(u: String, label: String) {
            store.setStr("dns_url", u)
            Doh.endpoint = u
            Doh.clear()
            toast(if (u.isEmpty()) "DNS filter off" else "DNS filter: $label")
        }

        AlertDialog.Builder(this)
            .setTitle("DNS filter")
            .setSingleChoiceItems(names.toTypedArray(), checked) { d, which ->
                d.dismiss()
                if (which == 5) {
                    val e = EditText(this)
                    e.hint = "https://example.com/resolve"
                    e.setText(if (saved.isNotEmpty() && !urls.contains(saved)) saved else "")
                    e.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                    val bx = LinearLayout(this)
                    bx.setPadding(dp(20), dp(8), dp(20), 0)
                    bx.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    AlertDialog.Builder(this)
                        .setTitle("Custom DoH URL")
                        .setView(bx)
                        .setPositiveButton("OK") { _, _ ->
                            val v = e.text.toString().trim()
                            if (v.isNotEmpty()) applyDns(normalizeUrl(v), "custom")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    applyDns(urls[which], names[which])
                }
            }
            .setNeutralButton("System DNS") { _, _ ->
                toast("Open Network & internet > Private DNS")
                try {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            }
            .setNegativeButton("Test") { _, _ ->
                val ep = Doh.endpoint
                if (ep.isEmpty()) {
                    toast("Choose a DNS provider first")
                } else {
                    Thread {
                        val r = Doh.test(ep)
                        runOnUiThread { toast(r) }
                    }.start()
                }
            }
            .show()
    }

    // ------------------------------------------------------------------ screen filter

    private fun screenFilterDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(16), dp(24), dp(8))
        val spin = Spinner(this)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, SF_NAMES)
        spin.setSelection(store.getInt("sf_mode", 0).coerceIn(0, SF_NAMES.size - 1))
        box.addView(spin)
        val label = tv("Strength: " + store.getInt("sf_level", 40) + "%", 13f, 0xFFBBBBBB.toInt())
        label.setPadding(0, dp(14), 0, 0)
        box.addView(label)
        val sb = SeekBar(this)
        sb.max = 100
        sb.progress = store.getInt("sf_level", 40)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                store.setInt("sf_level", p)
                label.text = "Strength: $p%"
                applyScreenFilter()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(sb)
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                store.setInt("sf_mode", pos)
                applyScreenFilter()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        AlertDialog.Builder(this)
            .setTitle("Screen filter")
            .setView(box)
            .setPositiveButton("OK", null)
            .setNeutralButton("Turn off") { _, _ ->
                store.setInt("sf_mode", 0)
                applyScreenFilter()
            }
            .show()
    }

    private fun applyScreenFilter() {
        val mode = store.getInt("sf_mode", 0).coerceIn(0, SF_COLORS.size - 1)
        val level = store.getInt("sf_level", 40)
        val host = findViewById<ViewGroup>(android.R.id.content)
        var v = filterView
        if (mode == 0) {
            if (v != null) {
                host.removeView(v)
                filterView = null
            }
            return
        }
        if (v == null) {
            v = View(this)
            v.isClickable = false
            v.isFocusable = false
            host.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            filterView = v
        }
        val a = (level / 100f * 0.7f * 255f).toInt().coerceIn(0, 255)
        v.setBackgroundColor((a shl 24) or (SF_COLORS[mode] and 0xFFFFFF))
    }

    // ------------------------------------------------------------------ one-handed mode

    private fun toggleOneHand() {
        if (oneHand) exitOneHand() else enterOneHand()
    }

    private fun enterOneHand() {
        val host = findViewById<ViewGroup>(android.R.id.content)
        val root = host.getChildAt(0)
        oneHand = true
        placePivot(root)
        root.animate().scaleX(0.76f).scaleY(0.76f).setDuration(200).start()

        val panel = LinearLayout(this)
        panel.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.cornerRadius = dp(26).toFloat()
        bg.setColor(0xFF262630.toInt())
        panel.background = bg
        panel.setPadding(dp(6), dp(4), dp(6), dp(4))
        fun b(text: String, action: () -> Unit) {
            val t = tv(text, 14f)
            t.setPadding(dp(16), dp(10), dp(16), dp(10))
            t.setOnClickListener { action() }
            panel.addView(t)
        }
        b("Switch side") {
            store.setInt("oh_side", if (store.getInt("oh_side", 1) == 1) 0 else 1)
            placePivot(root)
            root.invalidate()
        }
        b("Exit") { exitOneHand() }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        lp.topMargin = dp(90)
        host.addView(panel, lp)
        handPanel = panel
        toast("One-handed mode on")
    }

    private fun placePivot(root: View) {
        val right = store.getInt("oh_side", 1) == 1
        root.pivotX = if (right) root.width.toFloat() else 0f
        root.pivotY = root.height.toFloat()
    }

    private fun exitOneHand() {
        val host = findViewById<ViewGroup>(android.R.id.content)
        val root = host.getChildAt(0)
        oneHand = false
        root.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        handPanel?.let { host.removeView(it) }
        handPanel = null
    }

    // ------------------------------------------------------------------ memo

    private fun memo() {
        val t = cur
        if (t == null || t.isHome || !t.url.startsWith("http")) {
            itemsLauncher.launch(Intent(this, MemoActivity::class.java))
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Memo")
            .setItems(arrayOf("New memo for this page", "All memos")) { _, w ->
                if (w == 1) {
                    itemsLauncher.launch(Intent(this, MemoActivity::class.java))
                } else {
                    t.webView.evaluateJavascript(PageJs.SELECTION) { r ->
                        val sel = try {
                            JSONArray("[$r]").getString(0)
                        } catch (e: Exception) {
                            ""
                        }
                        val i = Intent(this, MemoActivity::class.java)
                        i.putExtra("new", true)
                        i.putExtra("newTitle", t.title)
                        i.putExtra("newUrl", t.url)
                        i.putExtra("newText", if (sel == "null") "" else sel)
                        itemsLauncher.launch(i)
                    }
                }
            }
            .show()
    }

    // ------------------------------------------------------------------ add current page

    private fun addCurrentPage() {
        val t = cur
        if (t == null || t.isHome || !t.url.startsWith("http")) {
            addToDial()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Add current page")
            .setItems(arrayOf("Home shortcuts", "Bookmarks", "Phone home screen")) { _, w ->
                when (w) {
                    0 -> addShortcutDialog(t.title, t.url)
                    1 -> {
                        if (store.isBookmarked(t.url)) {
                            toast("Already in bookmarks")
                        } else {
                            store.addBookmark(t.title, t.url)
                            toast("Bookmarked")
                            updateUi()
                        }
                    }
                    else -> pinToLauncher(t)
                }
            }
            .show()
    }

    private fun pinToLauncher(t: BTab) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            toast("Your launcher does not support pinned shortcuts")
            return
        }
        val host = hostOf(t.url)
        val fav = FaviconCache.get(this, host)
        val size = dp(96)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF2A2A33.toInt()
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        if (fav != null) {
            val inner = (size * 0.6f).toInt()
            val sc = Bitmap.createScaledBitmap(fav, inner, inner, true)
            c.drawBitmap(sc, (size - inner) / 2f, (size - inner) / 2f, null)
        } else {
            p.color = Color.WHITE
            p.textSize = size * 0.5f
            p.textAlign = Paint.Align.CENTER
            val ch = (t.title.firstOrNull { it.isLetterOrDigit() } ?: host.firstOrNull() ?: 'A').uppercaseChar().toString()
            c.drawText(ch, size / 2f, size / 2f + p.textSize * 0.35f, p)
        }
        val intent = Intent(this, MainActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.data = Uri.parse(t.url)
        val info = ShortcutInfoCompat.Builder(this, "pg" + t.url.hashCode())
            .setShortLabel(t.title.ifBlank { host }.take(24))
            .setIcon(IconCompat.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, info, null)
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
        popupLevel = store.getInt("pop_level", if (store.getBool("popup", true)) 2 else 0)
        ovlOn = store.getBool("overlay", true)
        elemOn = store.getBool("elem", true)
        appExec = store.getInt("appexec", 0)
        blockArea = store.getBool("blockarea", false)
        popAllow = store.strings("popallow").toHashSet()
        ovlAllow = store.strings("ovlallow").toHashSet()
        cosmetic = store.getBool("cosmetic", true)
        adDomains = store.strings("addom").toHashSet()
        adAllow = store.strings("adallow").toHashSet()
    }

    private fun normHost(h: String): String = h.lowercase().removePrefix("www.")

    private fun emptyResponse() =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun escHtml(t: String) =
        t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

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

    private fun adBlocked(pageUrl: String, u: Uri, mainFrame: Boolean): Boolean {
        if (!adblockOn) return false
        val host = u.host ?: return false
        if (AdBlock.isYtAd(host, u.path ?: "")) return true
        val pageHost = normHost(hostOf(pageUrl))
        if (adAllow.contains(pageHost)) return false
        val third = pageHost.isNotEmpty() && !AdBlock.sameSite(pageHost, normHost(host))
        return isAdHost(host) || (third && !mainFrame && AdBlock.looksLikeAd(u.toString()))
    }

    // ------------------------------------------------------------------ video download button

    private var videoPanel: LinearLayout? = null
    private var videoFab: ImageView? = null
    private val hidePanel = Runnable { videoPanel?.visibility = View.GONE }

    private class VRow(val label: String, val type: String, val url: String, val hls: Boolean)

    private class VGroup(val rows: MutableList<VRow>, val keys: MutableSet<String>, var seen: Long)
    private var customWrap: ViewGroup? = null
    private var fsBrightness = -1f

    /**
     * Wraps the full-screen video: vertical swipe on the left side changes brightness, on the
     * right side changes volume. Taps and other gestures still reach the video controls.
     */
    inner class GestureFrame(ctx: android.content.Context) : FrameLayout(ctx) {
        private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop * 2
        private var sx = 0f
        private var sy = 0f
        private var mode = 0
        private var startVal = 0f
        private val am = ctx.getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        private val hud = TextView(ctx)
        private val hideHud = Runnable { hud.visibility = View.GONE }

        init {
            hud.textSize = 18f
            hud.setTextColor(Color.WHITE)
            hud.setPadding(dp(18), dp(10), dp(18), dp(10))
            val bg = GradientDrawable()
            bg.cornerRadius = dp(20).toFloat()
            bg.setColor(0xCC000000.toInt())
            hud.background = bg
            hud.visibility = View.GONE
            addView(hud, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

        private fun brightNow(): Float {
            val b = this@MainActivity.window.attributes.screenBrightness
            if (b >= 0f) return b
            return try {
                android.provider.Settings.System.getInt(
                    contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (_: Exception) {
                0.5f
            }
        }

        override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    sx = e.x
                    sy = e.y
                    mode = 0
                }
                android.view.MotionEvent.ACTION_MOVE -> if (mode == 0 && e.pointerCount == 1) {
                    val dx = Math.abs(e.x - sx)
                    val dy = Math.abs(e.y - sy)
                    if (dy > slop && dy > dx * 1.5f) {
                        mode = if (sx < width * 0.4f) 1 else if (sx > width * 0.6f) 2 else -1
                        if (mode == 1) startVal = brightNow()
                        if (mode == 2) startVal = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                    }
                }
            }
            return mode > 0
        }

        override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
            if (mode <= 0) return super.onTouchEvent(e)
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_MOVE -> {
                    val frac = (sy - e.y) / maxOf(height, 1) * 1.3f
                    if (mode == 1) {
                        val v = (startVal + frac).coerceIn(0.02f, 1f)
                        val a = this@MainActivity.window.attributes
                        a.screenBrightness = v
                        this@MainActivity.window.attributes = a
                        show("Brightness  " + Math.round(v * 100) + "%")
                    } else {
                        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        val v = Math.round(startVal + frac * max).coerceIn(0, max)
                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v, 0)
                        show("Volume  $v / $max")
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    mode = 0
                    hud.removeCallbacks(hideHud)
                    hud.postDelayed(hideHud, 700)
                }
            }
            return true
        }

        private fun show(t: String) {
            hud.text = t
            hud.visibility = View.VISIBLE
            hud.removeCallbacks(hideHud)
        }
    }

    private fun guessQuality(url: String): String {
        val m = Regex("(2160|1440|1080|720|480|360|240|144)p?").find(url)
        return if (m != null) m.groupValues[1] + "p" else ""
    }

    private fun addVideo(tab: BTab, url: String, label: String) {
        if (!url.startsWith("http")) return
        if (adBlocked(tab.url, Uri.parse(url), false)) return
        val clean = url.substringBefore('#')
        val old = tab.videos.firstOrNull { it.url == clean }
        if (old != null) {
            old.time = System.currentTimeMillis()
            if (old.label.isEmpty() && label.isNotEmpty()) old.label = label
        } else {
            if (tab.videos.size >= 30) return
            tab.videos.add(VideoSrc(clean, label.ifEmpty { guessQuality(clean) }))
        }
        runOnUiThread { if (tab === cur) updateVideoBtn() }
    }

    private fun roundBtn(icon: Int, action: () -> Unit): ImageView {
        val b = ImageView(this)
        b.setImageResource(icon)
        b.setColorFilter(Color.WHITE)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xFF5A5A5A.toInt())
        b.background = bg
        b.setPadding(dp(12), dp(12), dp(12), dp(12))
        b.setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(dp(50), dp(50))
        lp.setMargins(dp(8), 0, dp(8), 0)
        b.layoutParams = lp
        return b
    }

    /**
     * One small floating button; tapping it opens the panel with three actions
     * (picture-in-picture, full screen, download).
     */
    private fun updateVideoBtn() {
        val t = cur
        val show = t != null && !t.isHome && t.videos.isNotEmpty()
        var fab = videoFab
        if (!show) {
            fab?.visibility = View.GONE
            videoPanel?.visibility = View.GONE
            return
        }
        if (fab == null) {
            fab = ImageView(this)
            fab.setImageResource(R.drawable.ic_download)
            fab.setColorFilter(Color.WHITE)
            val fbg = GradientDrawable()
            fbg.shape = GradientDrawable.OVAL
            fbg.setColor(0xFF7C6FE0.toInt())
            fab.background = fbg
            fab.setPadding(dp(12), dp(12), dp(12), dp(12))
            fab.elevation = dp(8).toFloat()
            val flp = FrameLayout.LayoutParams(dp(48), dp(48))
            flp.gravity = Gravity.TOP or Gravity.END
            flp.setMargins(0, dp(150), dp(10), 0)
            content.addView(fab, flp)
            // tap = open / close the panel, drag = move the button anywhere
            val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
            var downX = 0f
            var downY = 0f
            var startTx = 0f
            var startTy = 0f
            var moved = false
            fab.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startTx = v.translationX
                        startTy = v.translationY
                        moved = false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val mx = e.rawX - downX
                        val my = e.rawY - downY
                        if (!moved && (Math.abs(mx) > slop || Math.abs(my) > slop)) {
                            moved = true
                            videoPanel?.visibility = View.GONE
                        }
                        if (moved) {
                            val minX = -(content.width - dp(48) - dp(10)).toFloat()
                            val minY = -dp(150).toFloat()
                            val maxY = (content.height - dp(150) - dp(48)).toFloat()
                            v.translationX = (startTx + mx).coerceIn(minOf(minX, 0f), 0f)
                            v.translationY = (startTy + my).coerceIn(minY, maxOf(minY, maxY))
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> if (!moved) toggleVideoPanel()
                }
                true
            }
            videoFab = fab

            val p = LinearLayout(this)
            p.orientation = LinearLayout.HORIZONTAL
            p.gravity = Gravity.CENTER_VERTICAL
            p.setPadding(dp(8), dp(10), dp(8), dp(10))
            val bg = GradientDrawable()
            bg.cornerRadius = dp(30).toFloat()
            bg.setColor(0xF0000000.toInt())
            bg.setStroke(dp(1), 0xFF555555.toInt())
            p.background = bg
            p.elevation = dp(8).toFloat()
            p.addView(roundBtn(R.drawable.ic_pip) { p.visibility = View.GONE; videoPip() })
            p.addView(roundBtn(R.drawable.ic_fullscreen) { p.visibility = View.GONE; videoFullscreen() })
            p.addView(roundBtn(R.drawable.ic_download) { p.visibility = View.GONE; videoSheet() })
            p.visibility = View.GONE
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.TOP or Gravity.END
            lp.setMargins(0, dp(150) + dp(56), dp(2), 0)
            content.addView(p, lp)
            videoPanel = p
        }
        fab.visibility = View.VISIBLE
        fab.bringToFront()
    }

    /** Opens the three-button panel right under the media button (above it when the button is low). */
    private fun toggleVideoPanel() {
        val p = videoPanel ?: return
        val fab = videoFab ?: return
        if (p.visibility == View.VISIBLE) {
            p.visibility = View.GONE
            return
        }
        val ph = if (p.height > 0) p.height else dp(70)
        val below = fab.translationY + dp(150) + dp(48) + ph + dp(16) < content.height
        p.translationX = fab.translationX
        p.translationY = if (below) fab.translationY else fab.translationY - dp(56) - ph - dp(8)
        p.visibility = View.VISIBLE
        p.bringToFront()
        p.removeCallbacks(hidePanel)
        p.postDelayed(hidePanel, 7000)
    }

    private fun videoFullscreen() {
        val t = cur ?: return
        val js = "(function(){var vs=document.querySelectorAll('video'),v=null;" +
            "for(var i=0;i<vs.length;i++){if(!vs[i].paused){v=vs[i];break;}}" +
            "if(!v)v=vs[0];" +
            "if(!v){var f=document.querySelector('iframe');if(f&&f.requestFullscreen){f.requestFullscreen();return 'ok';}return 'no';}" +
            "var r=v.requestFullscreen||v.webkitRequestFullscreen;if(r){r.call(v);return 'ok';}return 'no';})()"
        t.webView.evaluateJavascript(js) { r ->
            if (r != null && r.contains("no")) toast("Use the player's own full screen button")
        }
    }

    private fun videoPip() {
        videoFullscreen()
        content.postDelayed({
            try {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9)).build()
                )
            } catch (e: Exception) {
                toast("Picture-in-picture is not available")
            }
        }, 500)
    }

    private fun fileBase(title: String, label: String): String {
        var n = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").replace(Regex("\\s+"), " ").trim()
        if (n.length > 70) n = n.substring(0, 70).trim()
        if (n.isEmpty()) n = "video"
        val l = label.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
        return if (l.isEmpty()) n else "$n - $l"
    }

    private fun sheetBox(): Pair<com.google.android.material.bottomsheet.BottomSheetDialog, LinearLayout> {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        val r = dp(28).toFloat()
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bg.setColor(0xFF202020.toInt())
        box.background = bg
        return Pair(sheet, box)
    }

    private fun showSheet(sheet: com.google.android.material.bottomsheet.BottomSheetDialog, box: View) {
        sheet.setContentView(box)
        try {
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        } catch (_: Exception) {
        }
        sheet.show()
    }

    private fun qh(label: String): Int {
        Regex("\\d+x(\\d+)").find(label)?.let { return it.groupValues[1].toIntOrNull() ?: 0 }
        Regex("(\\d+)p").find(label)?.let { return it.groupValues[1].toIntOrNull() ?: 0 }
        return 0
    }

    private fun extOf(url: String): String {
        val p = (Uri.parse(url).path ?: "").lowercase()
        return p.substringAfterLast('.', "mp4").uppercase().take(4)
    }

    /** Groups the sniffed links into videos: the one playing now, and all others. Blocking. */
    private fun buildGroups(t: BTab, ua: String, ref: String): List<VGroup> {
        val srcs = t.videos.toList()
        val playing = t.playing.toSet()
        val out = mutableListOf<VGroup>()
        val direct = mutableListOf<VideoSrc>()
        val rest = mutableListOf<VideoSrc>()
        for (v in srcs) {
            val low = v.url.lowercase()
            if (low.contains(".mpd")) continue
            if (low.contains(".m3u8")) rest.add(v)
            else if (playing.contains(v.url)) direct.add(v)
            else rest.add(v)
        }
        if (direct.isNotEmpty()) {
            val g = VGroup(mutableListOf(), mutableSetOf(), 0L)
            for (v in direct) {
                g.rows.add(VRow(v.label, extOf(v.url), v.url, false))
                g.keys.add(v.url)
                g.seen = maxOf(g.seen, v.time)
            }
            out.add(g)
        }
        for (v in rest) {
            if (v.url.lowercase().contains(".m3u8")) {
                val vs = try {
                    VideoDl.variants(v.url, ua, ref)
                } catch (e: Exception) {
                    listOf(VideoDl.Variant(v.label.ifEmpty { "Stream" }, v.url, 0))
                }
                val g = VGroup(mutableListOf(), mutableSetOf(v.url), v.time)
                for (x in vs) {
                    g.rows.add(VRow(x.label, "HLS", x.url, true))
                    g.keys.add(x.url)
                }
                out.add(g)
            } else {
                out.add(VGroup(mutableListOf(VRow(v.label, extOf(v.url), v.url, false)), mutableSetOf(v.url), v.time))
            }
        }
        // a media playlist that is just one quality of a master playlist belongs to that master
        val masters = out.filter { it.keys.size > 1 }
        val kept = out.filter { g ->
            if (g.keys.size == 1) {
                val m = masters.firstOrNull { it !== g && it.keys.contains(g.rows[0].url) }
                if (m != null) {
                    m.seen = maxOf(m.seen, g.seen)
                    false
                } else true
            } else true
        }
        for (g in kept) g.rows.sortByDescending { qh(it.label) }
        return kept
    }

    private fun makeRow(
        sheet: BottomSheetDialog, list: LinearLayout, r: VRow, idx: Int, ua: String, ref: String, title: String
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(20), dp(6), dp(8), dp(6))
        val num = tv(idx.toString(), 18f)
        num.gravity = Gravity.CENTER
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val lab = tv(r.label.ifEmpty { "Video" }, 19f)
        val sub = tv("", 12f, 0xFF999999.toInt())
        sub.visibility = View.GONE
        col.addView(lab)
        col.addView(sub)
        val typ = tv(r.type, 16f, 0xFFCCCCCC.toInt())
        typ.setPadding(0, 0, dp(8), 0)
        val info = iconButton(R.drawable.ic_info, 44, 52)
        val play = iconButton(R.drawable.ic_play, 44, 52)
        row.addView(num, LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(16) })
        row.addView(typ)
        row.addView(info)
        row.addView(play)
        val go = {
            sheet.dismiss()
            startVideoDownload(r.hls, r.url, lab.text.toString().takeIf { it != "Video" && it != "Stream" } ?: "", title, ua, ref)
        }
        row.setOnClickListener { go() }
        play.setOnClickListener { go() }
        info.setOnClickListener { videoInfo(r.url) }
        list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val line = View(this)
        line.setBackgroundColor(0xFF333333.toInt())
        list.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
        })
        if (!r.hls) {
            Thread {
                val p = VideoDl.probe(r.url, ua, ref)
                runOnUiThread {
                    if (p.w > 0 && p.h > 0) lab.text = p.w.toString() + "x" + p.h
                    if (p.size > 0) {
                        sub.text = Formatter.formatShortFileSize(this, p.size)
                        sub.visibility = View.VISIBLE
                    }
                }
            }.start()
        }
    }

    /**
     * Download sheet: preview + title of the video that is playing, its qualities,
     * and a "Master" row that opens every other video found on the page.
     */
    private fun videoSheet() {
        val t = cur ?: return
        if (t.videos.isEmpty()) return
        val ua = t.webView.settings.userAgentString ?: DESKTOP_UA
        val ref = t.url
        val title = t.title
        val (sheet, box) = sheetBox()

        // --- preview header
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(20), dp(18), dp(20), dp(14))
        val thumb = ImageView(this)
        thumb.scaleType = ImageView.ScaleType.CENTER_CROP
        val tbg = GradientDrawable()
        tbg.cornerRadius = dp(12).toFloat()
        tbg.setColor(0xFF000000.toInt())
        thumb.background = tbg
        thumb.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
            }
        }
        thumb.clipToOutline = true
        val tcol = LinearLayout(this)
        tcol.orientation = LinearLayout.VERTICAL
        val ttl = tv(title.ifBlank { hostOf(ref) }, 16f)
        ttl.maxLines = 2
        ttl.ellipsize = android.text.TextUtils.TruncateAt.END
        val tinfo = tv("Playing now", 13f, 0xFF999999.toInt())
        tcol.addView(ttl)
        tcol.addView(tinfo)
        head.addView(thumb, LinearLayout.LayoutParams(dp(128), dp(72)))
        head.addView(tcol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(14) })
        box.addView(head)
        val hline = View(this)
        hline.setBackgroundColor(0xFF333333.toInt())
        box.addView(hline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
        })

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, dp(4), 0, dp(12))
        val loading = tv("Looking for qualities...", 14f, 0xFF999999.toInt())
        loading.setPadding(dp(24), dp(18), dp(24), dp(18))
        list.addView(loading)
        val sv = ScrollView(this)
        sv.addView(list)
        box.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        showSheet(sheet, box)

        // --- live preview frame of the playing video
        val js = "(function(){var vs=document.querySelectorAll('video'),v=null;" +
            "for(var i=0;i<vs.length;i++){if(!vs[i].paused&&vs[i].readyState>=2){v=vs[i];break;}}" +
            "if(!v)v=vs[0];if(!v)return '';var img='';" +
            "try{var w=320,h=Math.round(w*(v.videoHeight||9)/(v.videoWidth||16));var c=document.createElement('canvas');" +
            "c.width=w;c.height=h;c.getContext('2d').drawImage(v,0,0,w,h);img=c.toDataURL('image/jpeg',0.7);}catch(e){img=v.poster||'';}" +
            "return JSON.stringify({i:img,d:isFinite(v.duration)?v.duration:0,w:v.videoWidth,h:v.videoHeight,p:!v.paused});})()"
        t.webView.evaluateJavascript(js) { r ->
            try {
                val s = org.json.JSONTokener(r).nextValue() as? String
                if (!s.isNullOrEmpty()) {
                    val o = JSONObject(s)
                    val img = o.optString("i")
                    val d = o.optDouble("d", 0.0).toInt()
                    val parts = mutableListOf<String>()
                    parts.add(if (o.optBoolean("p")) "Playing now" else "Paused")
                    if (o.optInt("w") > 0) parts.add(o.optInt("w").toString() + "x" + o.optInt("h"))
                    if (d > 0) parts.add(String.format(java.util.Locale.US, "%d:%02d", d / 60, d % 60))
                    tinfo.text = parts.joinToString("  •  ")
                    if (img.startsWith("data:image")) {
                        val bytes = android.util.Base64.decode(img.substringAfter(','), android.util.Base64.DEFAULT)
                        thumb.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    } else if (img.startsWith("http")) {
                        Thread {
                            try {
                                val c = VideoDl.openConn(img, ua, ref)
                                val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
                                c.disconnect()
                                runOnUiThread { thumb.setImageBitmap(bmp) }
                            } catch (_: Exception) {
                            }
                        }.start()
                    }
                }
            } catch (_: Exception) {
            }
        }

        // --- qualities
        Thread {
            val groups = buildGroups(t, ua, ref)
            runOnUiThread {
                list.removeView(loading)
                if (groups.isEmpty()) {
                    list.addView(loading)
                    loading.text = "No downloadable video found on this page"
                    return@runOnUiThread
                }
                val playing = t.playing.toSet()
                val current = groups.firstOrNull { g -> g.keys.any { playing.contains(it) } }
                    ?: groups.maxByOrNull { it.seen }!!
                var n = 0
                for (r in current.rows) {
                    n++
                    makeRow(sheet, list, r, n, ua, ref, title)
                }
                val others = groups.filter { it !== current }.sortedByDescending { it.seen }
                if (others.isNotEmpty()) {
                    val m = LinearLayout(this)
                    m.orientation = LinearLayout.HORIZONTAL
                    m.gravity = Gravity.CENTER_VERTICAL
                    m.setPadding(dp(20), 0, dp(12), 0)
                    val mt = tv("Master", 19f)
                    val mc = tv("   " + others.size + " other video" + (if (others.size > 1) "s" else ""), 13f, 0xFF999999.toInt())
                    m.addView(mt)
                    m.addView(mc, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    m.addView(iconView(R.drawable.ic_arrow_forward, 24))
                    m.setOnClickListener { masterSheet(others, ua, ref, title) }
                    list.addView(m, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
                }
            }
        }.start()
    }

    /** Every other video found on the page, grouped. */
    private fun masterSheet(groups: List<VGroup>, ua: String, ref: String, title: String) {
        val (sheet, box) = sheetBox()
        val h = tv("Master  •  other videos on this page", 16f, 0xFFCCCCCC.toInt())
        h.setPadding(dp(20), dp(20), dp(20), dp(10))
        box.addView(h)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(12))
        for (g in groups) {
            val first = g.rows.firstOrNull()?.url ?: continue
            val tail = (Uri.parse(first).lastPathSegment ?: "").take(40)
            val gh = tv(hostOf(first) + (if (tail.isNotEmpty()) "  •  $tail" else ""), 13f, 0xFF999999.toInt())
            gh.setPadding(dp(20), dp(12), dp(20), dp(4))
            gh.maxLines = 1
            gh.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            list.addView(gh)
            var n = 0
            for (r in g.rows) {
                n++
                makeRow(sheet, list, r, n, ua, ref, title)
            }
        }
        val sv = ScrollView(this)
        sv.addView(list)
        box.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        showSheet(sheet, box)
    }

    /** Source details: full link with open / share / copy. */
    private fun videoInfo(url: String) {
        val (sheet, box) = sheetBox()
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(14), dp(8), dp(10), dp(8))
        val help = iconButton(R.drawable.ic_help, 48, 48)
        help.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Video source")
                .setMessage("This is the direct address of the video.\n\n• Arrows: open in another player\n• Share: send the link\n• Link: copy it")
                .setPositiveButton("OK", null)
                .show()
        }
        val open = iconButton(R.drawable.ic_open_with, 48, 48)
        open.setOnClickListener {
            try {
                val i = Intent(Intent.ACTION_VIEW)
                i.setDataAndType(Uri.parse(url), "video/*")
                startActivity(Intent.createChooser(i, "Open with"))
            } catch (e: Exception) {
                toast("No player found")
            }
        }
        val share = iconButton(R.drawable.ic_share, 48, 48)
        share.setOnClickListener {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, url)
            startActivity(Intent.createChooser(i, "Share link"))
        }
        val copy = iconButton(R.drawable.ic_link, 48, 48)
        copy.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("video", url))
            toast("Link copied")
        }
        val gap = View(this)
        top.addView(help)
        top.addView(gap, LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(open)
        top.addView(share)
        top.addView(copy)
        val line = View(this)
        line.setBackgroundColor(0xFF333333.toInt())
        val link = tv(url, 18f)
        link.setTextIsSelectable(true)
        link.setPadding(dp(20), dp(24), dp(20), dp(24))
        val line2 = View(this)
        line2.setBackgroundColor(0xFF333333.toInt())
        val ok = tv("OK", 18f)
        ok.gravity = Gravity.CENTER
        ok.setOnClickListener { sheet.dismiss() }
        box.addView(top)
        box.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
        })
        val sv = ScrollView(this)
        sv.addView(link)
        box.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(line2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
        })
        box.addView(ok, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        showSheet(sheet, box)
    }

    private fun startVideoDownload(hls: Boolean, url: String, label: String, title: String, ua: String, ref: String) {
        val base = fileBase(title, label)
        if (hls) {
            VideoDl.downloadHls(applicationContext, store, url, base, ua, ref) { m ->
                runOnUiThread { toast(m) }
            }
        } else {
            val path = (Uri.parse(url).path ?: "").lowercase()
            val ext = listOf("mp4", "webm", "m4v", "mov", "mkv", "flv").firstOrNull { path.endsWith(".$it") } ?: "mp4"
            startDownload(url, ua, null, null, ref, "$base.$ext")
        }
    }

    private fun startDownload(url: String, ua: String?, cd: String?, mime: String?, referer: String? = null, fileName: String? = null) {
        if (!url.startsWith("http")) {
            toast("This link type cannot be downloaded")
            return
        }
        try {
            val req = DownloadManager.Request(Uri.parse(url))
            if (mime != null) req.setMimeType(mime)
            if (ua != null) req.addRequestHeader("User-Agent", ua)
            if (referer != null && referer.startsWith("http")) req.addRequestHeader("Referer", referer)
            val ck = CookieManager.getInstance().getCookie(url)
            if (ck != null) req.addRequestHeader("Cookie", ck)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val name = fileName ?: URLUtil.guessFileName(url, cd, mime)
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
        if (blockArea && r.type == WebView.HitTestResult.UNKNOWN_TYPE) {
            startPicker()
            return true
        }
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
        labels.add("Update ad lists (" + AdLists.count + " extra domains)")
        acts.add { updateAdLists() }
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
        fun isBlocked(url: String): Boolean = try {
            adBlocked(tab.url, Uri.parse(url), false)
        } catch (_: Exception) {
            false
        }

        @JavascriptInterface
        fun videoQuality(h: Int) {
            if (h <= 0) return
            tab.quality = h.toString() + "p"
            tab.videos.lastOrNull { it.label.isEmpty() }?.label = tab.quality
            runOnUiThread { if (tab === cur) updateVideoBtn() }
        }

        @JavascriptInterface
        fun videoPlaying(json: String) {
            try {
                val a = JSONArray(json)
                val urls = mutableListOf<String>()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val u = o.optString("u").substringBefore('#')
                    if (u.startsWith("http")) urls.add(u)
                    addVideo(tab, u, o.optString("l"))
                }
                tab.playing = urls
            } catch (_: Exception) {
            }
        }

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
        t.webView.evaluateJavascript(AdBlock.TAP_JS, null)
        if (store.getBool("blockimg", false)) sb.append("img,picture{display:none!important}\n")
        if (adblockOn && cosmetic && !adAllow.contains(host)) sb.append(AdBlock.COSMETIC_CSS).append('\n')
        for (r in store.getHideRules(host)) sb.append(r).append("{display:none!important}\n")
        if (sb.isNotEmpty()) t.webView.evaluateJavascript(AdBlock.styleJs(sb.toString()), null)
    }

    // ------------------------------------------------------------------ adult block / pin

    private fun isAdHost(host: String): Boolean =
        AdBlock.matches(host, AdBlock.SET) || AdBlock.matches(host, AdLists.set) ||
            AdBlock.matches(host, adDomains)

    private fun injectAnti(t: BTab) {
        if (!t.url.startsWith("http")) return
        val host = normHost(hostOf(t.url))
        val ads = adblockOn && !adAllow.contains(host)
        val pop = if (popAllow.contains(host)) 0 else popupLevel
        val ovl = ovlOn && !ovlAllow.contains(host)
        val cfg = "window.__auraCfg={pop:" + pop + ",ovl:" + ovl + ",elem:" + elemOn + ",ads:" + ads + "};"
        t.webView.evaluateJavascript(cfg + AdBlock.ANTI_JS, null)
        t.webView.evaluateJavascript(AdBlock.VIDEO_JS, null)
        if (ads) {
            t.webView.evaluateJavascript(
                "window.__auraAdSel=" + JSONObject.quote(AdBlock.COSMETIC_SEL) + ";" + AdBlock.COLLAPSE_JS, null
            )
        }
        if (ads && (host == "youtube.com" || host.endsWith(".youtube.com"))) {
            t.webView.evaluateJavascript(AdBlock.YT_JS, null)
        }
    }

    private fun startVpn(mode: Int) {
        store.setInt("vpn_mode", mode)
        if (mode == 2 && VpnProxy.normalize(store.getStr("vpn_custom", "")).isEmpty()) {
            toast("Set your proxy in Settings first")
            openSettings()
            return
        }
        toast(if (mode == 1) "Looking for a free server - up to 45 seconds..." else "Connecting...")
        VpnProxy.apply(applicationContext, store) { msg ->
            runOnUiThread {
                toast(msg)
                val c = cur
                if (c != null && !c.isHome && VpnProxy.active.isNotEmpty()) c.webView.reload()
            }
        }
    }

    private fun vpnDialog() {
        val mode = store.getInt("vpn_mode", 0)
        val labels = mutableListOf<String>()
        val acts = mutableListOf<() -> Unit>()
        if (mode != 0) {
            labels.add("Turn VPN off")
            acts.add {
                store.setInt("vpn_mode", 0)
                VpnProxy.apply(applicationContext, store) { msg ->
                    runOnUiThread {
                        toast(msg)
                        val c = cur
                        if (c != null && !c.isHome) c.webView.reload()
                    }
                }
            }
        }
        if (mode != 1) {
            labels.add("Turn on - free servers")
            acts.add { vpnWarn(store) { startVpn(1) } }
        } else {
            labels.add("Find a new free server")
            acts.add {
                toast("Looking for a free server - up to 45 seconds...")
                VpnProxy.findFree(store) { msg ->
                    runOnUiThread {
                        toast(msg)
                        val c = cur
                        if (c != null && !c.isHome && VpnProxy.active.isNotEmpty()) c.webView.reload()
                    }
                }
            }
        }
        if (mode != 2) {
            labels.add("Turn on - my own proxy")
            acts.add { startVpn(2) }
        }
        labels.add("VPN settings")
        acts.add { openSettings() }
        val status = if (VpnProxy.active.isEmpty()) "not connected" else "via " + VpnProxy.active.substringAfter("://")
        AlertDialog.Builder(this)
            .setTitle("VPN - $status")
            .setItems(labels.toTypedArray()) { _, w -> acts[w]() }
            .show()
    }

    private fun updateAdLists() {
        toast("Updating ad lists...")
        AdLists.update(applicationContext) { n ->
            runOnUiThread {
                toast(if (n > 0) "Ad lists updated: $n domains" else "Update failed - check internet")
            }
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
