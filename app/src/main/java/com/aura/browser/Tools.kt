package com.aura.browser

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.LruCache
import android.webkit.CookieManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Hand-over of the images found on a page to ImagesActivity (too big for an Intent). */
object ImageHolder {
    @Volatile
    var urls: List<String> = emptyList()

    @Volatile
    var referer: String = ""

    @Volatile
    var ua: String = ""
}

/** JavaScript used to collect every image on the current page. */
object PageJs {
    const val IMAGES = """
(function(){
  var seen={},out=[];
  function add(u){
    if(!u)return;
    try{u=new URL(u,document.baseURI).href}catch(e){return}
    if(!/^(https?:|data:image)/.test(u))return;
    if(u.length>3000000)return;
    if(seen[u])return;
    seen[u]=1;out.push(u);
  }
  var im=document.images;
  for(var i=0;i<im.length;i++){
    var e=im[i];
    if(e.naturalWidth>0&&e.naturalWidth<48&&e.naturalHeight<48)continue;
    add(e.currentSrc||e.src);
  }
  var vids=document.querySelectorAll('video[poster]');
  for(var k=0;k<vids.length;k++)add(vids[k].getAttribute('poster'));
  var all=document.querySelectorAll('*');
  for(var j=0;j<all.length&&j<4000;j++){
    var bg=getComputedStyle(all[j]).backgroundImage;
    if(bg&&bg.indexOf('url(')>=0){
      var re=/url\(["']?([^"')]+)["']?\)/g,m;
      while((m=re.exec(bg))!==null)add(m[1]);
    }
  }
  var links=document.querySelectorAll('a[href]');
  for(var q=0;q<links.length;q++){
    var h=links[q].getAttribute('href')||'';
    if(/\.(jpe?g|png|gif|webp|bmp)(\?|#|$)/i.test(h))add(h);
  }
  return JSON.stringify(out.slice(0,400));
})()
"""

    const val TEXT = """
(function(){
  var sel=(window.getSelection()||'').toString();
  var n=document.querySelector('article')||document.querySelector('main')||document.body;
  var t=sel&&sel.trim().length>0?sel:((n&&n.innerText)||'');
  return JSON.stringify({l:document.documentElement.lang||'',s:sel.trim().length>0,t:t.substring(0,200000)});
})()
"""

    const val SELECTION = "(window.getSelection()||'').toString()"
}

/** Downloads, caches and saves images (works for http(s) and data: URLs). */
object ImgNet {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(4)
    private val ui = Handler(Looper.getMainLooper())

    fun bytes(url: String, maxBytes: Int = 40 * 1024 * 1024): ByteArray? {
        try {
            if (url.startsWith("data:")) {
                val comma = url.indexOf(',')
                if (comma < 0) return null
                val meta = url.substring(0, comma)
                val data = url.substring(comma + 1)
                return if (meta.contains(";base64")) Base64.decode(data, Base64.DEFAULT)
                else Uri.decode(data).toByteArray()
            }
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.instanceFollowRedirects = true
            if (ImageHolder.ua.isNotEmpty()) c.setRequestProperty("User-Agent", ImageHolder.ua)
            if (ImageHolder.referer.isNotEmpty()) c.setRequestProperty("Referer", ImageHolder.referer)
            val ck = CookieManager.getInstance().getCookie(url)
            if (ck != null) c.setRequestProperty("Cookie", ck)
            if (c.responseCode !in 200..299) return null
            val ins: InputStream = c.inputStream
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) return null
                out.write(buf, 0, n)
            }
            ins.close()
            return out.toByteArray()
        } catch (e: Exception) {
            return null
        }
    }

    fun decode(data: ByteArray, maxPx: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(data, 0, data.size, o)
            var s = 1
            while (o.outWidth / s > maxPx * 2 || o.outHeight / s > maxPx * 2) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            BitmapFactory.decodeByteArray(data, 0, data.size, o2)
        } catch (e: Throwable) {
            null
        }
    }

    fun load(url: String, maxPx: Int, cb: (Bitmap?) -> Unit) {
        val key = "$maxPx|$url"
        cache.get(key)?.let {
            cb(it)
            return
        }
        pool.execute {
            val b = bytes(url)?.let { decode(it, maxPx) }
            if (b != null) cache.put(key, b)
            ui.post { cb(b) }
        }
    }

    private fun sniff(d: ByteArray): Pair<String, String> {
        if (d.size > 12) {
            if (d[0] == 0x89.toByte() && d[1] == 'P'.code.toByte()) return "image/png" to "png"
            if (d[0] == 'G'.code.toByte() && d[1] == 'I'.code.toByte()) return "image/gif" to "gif"
            if (d[0] == 'R'.code.toByte() && d[8] == 'W'.code.toByte() && d[9] == 'E'.code.toByte()) return "image/webp" to "webp"
            if (d[0] == 'B'.code.toByte() && d[1] == 'M'.code.toByte()) return "image/bmp" to "bmp"
        }
        return "image/jpeg" to "jpg"
    }

    private fun nameFor(url: String, ext: String): String {
        var base = if (url.startsWith("data:")) "image" else
            Uri.parse(url).lastPathSegment?.substringBeforeLast('.') ?: "image"
        base = base.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifEmpty { "image" }
        return base + "_" + (System.currentTimeMillis() % 1000000) + "." + ext
    }

    fun saveBytes(ctx: Context, data: ByteArray, name: String, mime: String): Uri? {
        return try {
            val cv = ContentValues()
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name)
            cv.put(MediaStore.Images.Media.MIME_TYPE, mime)
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AuraBrowser")
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            uri
        } catch (e: Exception) {
            null
        }
    }

    /** Blocking: call from a worker thread. */
    fun saveUrl(ctx: Context, url: String): Uri? {
        val d = bytes(url) ?: return null
        val (mime, ext) = sniff(d)
        return saveBytes(ctx, d, nameFor(url, ext), mime)
    }

    fun saveAll(ctx: Context, urls: List<String>, onProgress: (Int, Int) -> Unit, onDone: (Int, Int) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            var ok = 0
            var i = 0
            for (u in urls) {
                i++
                if (saveUrl(app, u) != null) ok++
                val n = i
                ui.post { onProgress(n, urls.size) }
            }
            ui.post { onDone(ok, urls.size) }
        }.start()
    }
}

/** Saves text / bytes into the public Downloads folder (Downloads/AuraBrowser). */
object FileSaver {
    fun toDownloads(ctx: Context, name: String, mime: String, data: ByteArray): Boolean {
        return try {
            val cv = ContentValues()
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name)
            cv.put(MediaStore.Downloads.MIME_TYPE, mime)
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AuraBrowser")
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun fileToDownloads(ctx: Context, name: String, mime: String, f: File): Boolean =
        toDownloads(ctx, name, mime, f.readBytes())

    fun audioToMusic(ctx: Context, name: String, f: File): Boolean {
        return try {
            val cv = ContentValues()
            cv.put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            cv.put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
            cv.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/AuraBrowser")
            val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(f.readBytes()) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun safeName(s: String): String =
        s.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(60).ifEmpty { "page" }
}

/**
 * DNS filtering. A WebView cannot change the system resolver, so every host is checked
 * against a DNS-over-HTTPS (JSON) resolver first; hosts that the resolver blocks
 * (NXDOMAIN / REFUSED / 0.0.0.0) are blocked inside the browser.
 */
object Doh {
    @Volatile
    var endpoint: String = ""

    private val cache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun clear() = cache.clear()

    fun isBlocked(host: String): Boolean {
        val ep = endpoint
        if (ep.isEmpty()) return false
        if (host.isEmpty() || host == "localhost" || host.contains(':') || ipv4.matches(host)) return false
        val now = System.currentTimeMillis()
        cache[host]?.let { if (now - it.second < 10 * 60 * 1000L) return it.first }
        val r = query(ep, host)
        if (r != null) cache[host] = Pair(r, now)
        return r ?: false
    }

    private fun query(ep: String, host: String): Boolean? {
        return try {
            val sep = if (ep.contains("?")) "&" else "?"
            val c = URL(ep + sep + "name=" + Uri.encode(host) + "&type=A").openConnection() as HttpURLConnection
            c.connectTimeout = 3000
            c.readTimeout = 3000
            c.setRequestProperty("accept", "application/dns-json")
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val st = j.optInt("Status", 0)
            if (st == 3 || st == 5) return true
            val ans = j.optJSONArray("Answer") ?: return false
            var any = false
            for (i in 0 until ans.length()) {
                val a = ans.getJSONObject(i)
                val t = a.optInt("type")
                if (t == 1 || t == 28) {
                    any = true
                    val d = a.optString("data")
                    if (d != "0.0.0.0" && d != "::") return false
                }
            }
            any
        } catch (e: Exception) {
            null
        }
    }

    /** Test a resolver; returns a short human readable result. Blocking. */
    fun test(ep: String): String {
        val t0 = System.currentTimeMillis()
        val r = query(ep, "example.com")
        val ms = System.currentTimeMillis() - t0
        return if (r == null) "Resolver not reachable" else "Resolver OK (" + ms + " ms)"
    }
}

/** Text-to-speech engine wrapper with pause / resume / speed. */
class SpeechController(ctx: Context, private val onState: (Boolean) -> Unit) : TextToSpeech.OnInitListener {
    private val ui = Handler(Looper.getMainLooper())
    private val app = ctx.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private var ready = false
    private var chunks: List<String> = emptyList()
    private var idx = 0
    private var wantStart = false

    var rate = 1f
        private set
    var pitch = 1f
        private set
    var playing = false
        private set
    var hasText = false
        private set
    var langWarning: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            ui.post { app.toast("Text-to-speech engine not available") }
            return
        }
        installListener()
        if (hasPendingLang) {
            hasPendingLang = false
            if (!setLanguage(pendingLang)) {
                ui.post { app.toast("Voice for this language is not installed; using default") }
                setLanguage(null)
            }
        }
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
        if (wantStart) {
            wantStart = false
            begin()
        }
    }

    private fun installListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val i = utteranceId?.removePrefix("c")?.toIntOrNull() ?: return
                idx = i
            }

            override fun onDone(utteranceId: String?) {
                val i = utteranceId?.removePrefix("c")?.toIntOrNull() ?: return
                if (i >= chunks.size - 1) {
                    ui.post {
                        playing = false
                        idx = 0
                        onState(false)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                ui.post {
                    playing = false
                    onState(false)
                }
            }
        })
    }

    private var pendingLang: String? = null
    private var hasPendingLang = false

    fun setLanguage(tag: String?): Boolean {
        val t = tts ?: return false
        if (!ready) {
            pendingLang = tag
            hasPendingLang = true
            return true
        }
        val loc = if (tag.isNullOrBlank()) Locale.getDefault() else Locale.forLanguageTag(tag)
        val r = t.setLanguage(loc)
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun setRate(r: Float) {
        rate = r
        tts?.setSpeechRate(r)
    }

    fun setPitch(p: Float) {
        pitch = p
        tts?.setPitch(p)
    }

    private fun split(text: String): List<String> {
        val max = try {
            minOf(TextToSpeech.getMaxSpeechInputLength() - 100, 3000)
        } catch (e: Throwable) {
            3000
        }
        val clean = text.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{2,}"), "\n").trim()
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (s in clean.split(Regex("(?<=[.!?\u0964\n])\\s*"))) {
            if (s.isBlank()) continue
            if (cur.length + s.length > max && cur.isNotEmpty()) {
                out.add(cur.toString())
                cur = StringBuilder()
            }
            if (s.length > max) {
                var k = 0
                while (k < s.length) {
                    out.add(s.substring(k, minOf(s.length, k + max)))
                    k += max
                }
            } else {
                cur.append(s).append(' ')
            }
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }

    fun speak(text: String) {
        chunks = split(text)
        idx = 0
        hasText = chunks.isNotEmpty()
        if (!hasText) {
            onState(false)
            return
        }
        if (!ready) {
            wantStart = true
            playing = true
            onState(true)
            return
        }
        begin()
    }

    private fun begin() {
        val t = tts ?: return
        t.stop()
        for (i in idx until chunks.size) {
            t.speak(chunks[i], TextToSpeech.QUEUE_ADD, null, "c$i")
        }
        playing = true
        onState(true)
    }

    fun pause() {
        if (!playing) return
        tts?.stop()
        playing = false
        onState(false)
    }

    fun resume() {
        if (playing || chunks.isEmpty()) return
        begin()
    }

    fun stop() {
        wantStart = false
        tts?.stop()
        playing = false
        idx = 0
        hasText = false
        chunks = emptyList()
        onState(false)
    }

    /** Writes the text to a WAV file in the cache folder; [done] runs on the main thread with the file or null. */
    fun synthToFile(text: String, done: (File?) -> Unit) {
        val t = tts
        if (t == null || !ready) {
            done(null)
            return
        }
        val f = File(app.cacheDir, "tts_" + System.currentTimeMillis() + ".wav")
        val id = "file" + System.currentTimeMillis()
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) ui.post {
                    installListener()
                    done(f)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) ui.post {
                    installListener()
                    done(null)
                }
            }
        })
        val r = t.synthesizeToFile(text.take(3900), null, f, id)
        if (r != TextToSpeech.SUCCESS) done(null)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
