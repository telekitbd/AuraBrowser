package com.aura.browser

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** Helpers for the in-page "download video" feature: HLS (m3u8) parsing, HLS download, size probing. */
object VideoDl {

    /** Live state of a running HLS download (key = negative id used in the downloads list). */
    class State(val total: Int) {
        @Volatile
        var done = 0

        /** 0 = running, 1 = finished, 2 = failed */
        @Volatile
        var status = 0

        @Volatile
        var cancel = false
    }

    val states = ConcurrentHashMap<Long, State>()

    class Variant(val label: String, val url: String, val height: Int)

    class Probe(val size: Long, val w: Int, val h: Int)

    fun openConn(url: String, ua: String, referer: String): HttpURLConnection {
        val c = java.net.URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", ua)
        if (referer.startsWith("http")) c.setRequestProperty("Referer", referer)
        try {
            CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
        } catch (_: Exception) {
        }
        return c
    }

    fun text(url: String, ua: String, referer: String): String {
        val c = openConn(url, ua, referer)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Qualities of an HLS playlist. A plain media playlist gives one entry (the playlist itself). */
    fun variants(url: String, ua: String, referer: String): List<Variant> {
        val txt = text(url, ua, referer)
        val out = mutableListOf<Variant>()
        if (!txt.contains("#EXT-X-STREAM-INF")) {
            out.add(Variant("Stream", url, 0))
            return out
        }
        val lines = txt.lines()
        val base = URI(url)
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                val rm = Regex("RESOLUTION=(\\d+)x(\\d+)").find(l)
                val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull()
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                if (j < lines.size) {
                    val h = rm?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    val label = if (rm != null) rm.groupValues[1] + "x" + rm.groupValues[2]
                    else if (bw != null) (bw / 1000).toString() + " kbps" else "Stream"
                    try {
                        out.add(Variant(label, base.resolve(lines[j].trim()).toString(), h))
                    } catch (_: Exception) {
                    }
                }
                i = j
            }
            i++
        }
        out.sortByDescending { it.height }
        return out
    }

    /** File size (HEAD) and, for plain video files, the real resolution. Blocking: call from a thread. */
    fun probe(url: String, ua: String, referer: String): Probe {
        var size = -1L
        try {
            val c = openConn(url, ua, referer)
            c.requestMethod = "HEAD"
            size = c.contentLengthLong
            c.disconnect()
        } catch (_: Exception) {
        }
        var w = 0
        var h = 0
        val r = MediaMetadataRetriever()
        try {
            val hd = HashMap<String, String>()
            hd["User-Agent"] = ua
            if (referer.startsWith("http")) hd["Referer"] = referer
            r.setDataSource(url, hd)
            w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
        return Probe(size, w, h)
    }

    /**
     * Downloads an (unencrypted) HLS media playlist into the public Downloads folder as one file.
     * Shows up in the Downloads page with live progress. [msg] gets short status texts.
     */
    fun downloadHls(
        ctx: Context, store: Store, mediaUrl: String, baseName: String,
        ua: String, referer: String, msg: (String) -> Unit
    ) {
        val id = -System.currentTimeMillis()
        Thread {
            var uri: Uri? = null
            var st: State? = null
            try {
                val txt = text(mediaUrl, ua, referer)
                if (txt.contains("#EXT-X-KEY") && !txt.contains("METHOD=NONE")) {
                    msg("This stream is encrypted and cannot be saved")
                    return@Thread
                }
                val base = URI(mediaUrl)
                val init = Regex("#EXT-X-MAP:.*URI=\"([^\"]+)\"").find(txt)?.groupValues?.get(1)
                val segs = txt.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { base.resolve(it).toString() }
                if (segs.isEmpty()) {
                    msg("No video data found")
                    return@Thread
                }
                val fmp4 = init != null
                val name = baseName + (if (fmp4) ".mp4" else ".ts")
                val cv = ContentValues()
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name)
                cv.put(MediaStore.Downloads.MIME_TYPE, if (fmp4) "video/mp4" else "video/mp2t")
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                cv.put(MediaStore.Downloads.IS_PENDING, 1)
                val target = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: throw Exception("no file")
                uri = target
                val s = State(segs.size)
                st = s
                states[id] = s
                store.addDownload(id, name, mediaUrl, target.toString())
                msg("Download started")
                ctx.contentResolver.openOutputStream(target)!!.use { out ->
                    fun pull(u: String) {
                        val c = openConn(u, ua, referer)
                        try {
                            c.inputStream.use { it.copyTo(out) }
                        } finally {
                            c.disconnect()
                        }
                    }
                    if (init != null) pull(base.resolve(init).toString())
                    for (sg in segs) {
                        if (s.cancel) throw Exception("cancelled")
                        pull(sg)
                        s.done++
                    }
                }
                val done = ContentValues()
                done.put(MediaStore.Downloads.IS_PENDING, 0)
                ctx.contentResolver.update(target, done, null, null)
                s.status = 1
                msg("Saved: $name")
            } catch (e: Exception) {
                st?.status = 2
                try {
                    uri?.let { ctx.contentResolver.delete(it, null, null) }
                } catch (_: Exception) {
                }
                if (st != null && !st.cancel) msg("Video download failed")
            }
        }.start()
    }
}
