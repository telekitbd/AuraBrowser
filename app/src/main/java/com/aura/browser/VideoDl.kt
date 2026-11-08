package com.aura.browser

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
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

    class Variant(val label: String, val url: String, val height: Int, val audio: String? = null)

    class Probe(val size: Long, val w: Int, val h: Int, val type: String = "")

    /** What is really inside a stream (checked on its first segment). */
    class Info(
        val hasVideo: Boolean, val hasAudio: Boolean, val w: Int, val h: Int,
        val live: Boolean, val enc: Boolean, val ok: Boolean, val thumb: Bitmap? = null
    )

    class Pl(val init: String?, val segs: List<String>, val encrypted: Boolean, val live: Boolean)

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
        val audioMap = HashMap<String, String>()
        for (l in lines) {
            if (l.startsWith("#EXT-X-MEDIA") && l.contains("TYPE=AUDIO")) {
                val u = Regex("URI=\"([^\"]+)\"").find(l)?.groupValues?.get(1) ?: continue
                val gid = Regex("GROUP-ID=\"([^\"]+)\"").find(l)?.groupValues?.get(1) ?: ""
                val abs = try {
                    base.resolve(u).toString()
                } catch (_: Exception) {
                    continue
                }
                if (!audioMap.containsKey(gid) || l.contains("DEFAULT=YES")) audioMap[gid] = abs
            }
        }
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
                        val ag = Regex("[,:]AUDIO=\"([^\"]+)\"").find(l)?.groupValues?.get(1)
                        out.add(Variant(label, base.resolve(lines[j].trim()).toString(), h, ag?.let { audioMap[it] }))
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
        var type = ""
        try {
            val c = openConn(url, ua, referer)
            c.requestMethod = "HEAD"
            size = c.contentLengthLong
            type = (c.contentType ?: "").lowercase()
            c.disconnect()
        } catch (_: Exception) {
        }
        if (size <= 0) {
            // some servers refuse HEAD: ask for the first byte and read the total from Content-Range
            try {
                val c = openConn(url, ua, referer)
                c.setRequestProperty("Range", "bytes=0-0")
                val cr = c.getHeaderField("Content-Range")
                size = cr?.substringAfter('/')?.trim()?.toLongOrNull() ?: c.contentLengthLong
                if (type.isEmpty()) type = (c.contentType ?: "").lowercase()
                c.disconnect()
            } catch (_: Exception) {
            }
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
        return Probe(size, w, h, type)
    }

    fun parsePlaylist(url: String, ua: String, referer: String): Pl {
        val txt = text(url, ua, referer)
        val enc = txt.contains("#EXT-X-KEY") && !txt.contains("METHOD=NONE")
        val live = !txt.contains("#EXT-X-ENDLIST")
        val base = URI(url)
        val init = Regex("#EXT-X-MAP:.*URI=\"([^\"]+)\"").find(txt)?.groupValues?.get(1)
            ?.let { base.resolve(it).toString() }
        val segs = txt.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { base.resolve(it).toString() }
        return Pl(init, segs, enc, live)
    }

    private fun pullOne(u: String, ua: String, referer: String, out: java.io.OutputStream) {
        // the segment is fetched fully (with retries) before it is written, so a retry never duplicates data
        var err: Exception? = null
        for (attempt in 1..3) {
            val c = openConn(u, ua, referer)
            try {
                val data = c.inputStream.use { it.readBytes() }
                out.write(data)
                return
            } catch (e: Exception) {
                err = e
                Thread.sleep(700L * attempt)
            } finally {
                c.disconnect()
            }
        }
        throw err ?: Exception("segment failed")
    }

    private fun pullSegs(
        pl: Pl, f: File, maxSegs: Int, ua: String, referer: String, cancel: () -> Boolean, tick: () -> Unit
    ) {
        f.outputStream().use { out ->
            if (pl.init != null) pullOne(pl.init, ua, referer, out)
            var n = 0
            for (sg in pl.segs) {
                if (cancel()) throw Exception("cancelled")
                pullOne(sg, ua, referer, out)
                tick()
                n++
                if (maxSegs > 0 && n >= maxSegs) break
            }
        }
    }

    private class Src(val ex: MediaExtractor, val track: Int) {
        var mt = -1
        var t0 = 0L
    }

    /** Joins the video (and the separate audio, if any) into one clean MP4. False when it cannot be done. */
    fun mux(video: File, audio: File?, out: File): Boolean {
        val srcs = mutableListOf<Src>()
        var mx: MediaMuxer? = null
        var started = false
        try {
            val ve = MediaExtractor()
            ve.setDataSource(video.absolutePath)
            var vt = -1
            for (i in 0 until ve.trackCount) {
                val m = ve.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("video/")) {
                    vt = i
                    break
                }
            }
            if (vt < 0) {
                ve.release()
                return false
            }
            srcs.add(Src(ve, vt))
            val ae = MediaExtractor()
            ae.setDataSource((audio ?: video).absolutePath)
            var at = -1
            for (i in 0 until ae.trackCount) {
                val m = ae.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) {
                    at = i
                    break
                }
            }
            if (at >= 0) srcs.add(Src(ae, at)) else ae.release()
            val mm = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mx = mm
            for (sr in srcs) {
                sr.ex.selectTrack(sr.track)
                sr.mt = mm.addTrack(sr.ex.getTrackFormat(sr.track))
                sr.t0 = sr.ex.sampleTime
            }
            val base = srcs.map { it.t0 }.filter { it >= 0 }.minOrNull() ?: 0L
            mm.start()
            started = true
            val buf = java.nio.ByteBuffer.allocate(3 * 1024 * 1024)
            val bi = MediaCodec.BufferInfo()
            while (true) {
                var best: Src? = null
                var bt = Long.MAX_VALUE
                for (sr in srcs) {
                    val t = sr.ex.sampleTime
                    if (t >= 0 && t < bt) {
                        bt = t
                        best = sr
                    }
                }
                val sr = best ?: break
                buf.clear()
                val n = sr.ex.readSampleData(buf, 0)
                if (n < 0) break
                bi.set(0, n, maxOf(0L, bt - base), sr.ex.sampleFlags)
                mm.writeSampleData(sr.mt, buf, bi)
                sr.ex.advance()
            }
            mm.stop()
            started = false
            return out.length() > 0
        } catch (_: Throwable) {
            out.delete()
            return false
        } finally {
            try {
                if (started) mx?.stop()
            } catch (_: Throwable) {
            }
            try {
                mx?.release()
            } catch (_: Throwable) {
            }
            for (sr in srcs) {
                try {
                    sr.ex.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    /** Looks inside a media playlist: has video / audio, size, live, encrypted, plus a still frame. Blocking. */
    fun inspectHls(ctx: Context, url: String, ua: String, referer: String): Info {
        var f: File? = null
        try {
            val pl = parsePlaylist(url, ua, referer)
            if (pl.encrypted) return Info(false, false, 0, 0, pl.live, true, true)
            if (pl.segs.isEmpty()) return Info(false, false, 0, 0, pl.live, false, false)
            f = File(ctx.cacheDir, "insp_" + System.nanoTime())
            pullSegs(pl, f, 1, ua, referer, { false }, {})
            val ex = MediaExtractor()
            try {
                ex.setDataSource(f.absolutePath)
                var v = false
                var a = false
                var w = 0
                var h = 0
                for (i in 0 until ex.trackCount) {
                    val fmt = ex.getTrackFormat(i)
                    val m = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (m.startsWith("video/")) {
                        v = true
                        if (fmt.containsKey(MediaFormat.KEY_WIDTH)) w = fmt.getInteger(MediaFormat.KEY_WIDTH)
                        if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    } else if (m.startsWith("audio/")) a = true
                }
                var th: Bitmap? = null
                if (v) {
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(f.absolutePath)
                        val b = r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        r.release()
                        if (b != null && b.width > 0) th = Bitmap.createScaledBitmap(b, 160, maxOf(1, 160 * b.height / b.width), true)
                    } catch (_: Throwable) {
                    }
                }
                return Info(v, a, w, h, pl.live, false, v || a, th)
            } finally {
                ex.release()
            }
        } catch (_: Throwable) {
            return Info(false, false, 0, 0, false, false, false)
        } finally {
            try {
                f?.delete()
            } catch (_: Throwable) {
            }
        }
    }

    /** First few seconds of the exact thing that would be saved, as a local MP4 for the preview player. */
    fun makePreview(ctx: Context, url: String, audioUrl: String?, ua: String, referer: String): File? {
        try {
            ctx.cacheDir.listFiles()?.filter { it.name.startsWith("pv_") }?.forEach { it.delete() }
            val vp = parsePlaylist(url, ua, referer)
            if (vp.encrypted || vp.segs.isEmpty()) return null
            val tag = System.nanoTime()
            val v = File(ctx.cacheDir, "pv_$tag.v")
            val a = File(ctx.cacheDir, "pv_$tag.a")
            val o = File(ctx.cacheDir, "pv_$tag.mp4")
            pullSegs(vp, v, 3, ua, referer, { false }, {})
            var haveA = false
            if (audioUrl != null) {
                try {
                    val ap = parsePlaylist(audioUrl, ua, referer)
                    if (!ap.encrypted && ap.segs.isNotEmpty()) {
                        pullSegs(ap, a, 3, ua, referer, { false }, {})
                        haveA = true
                    }
                } catch (_: Exception) {
                }
            }
            val ok = mux(v, if (haveA) a else null, o)
            v.delete()
            a.delete()
            if (!ok) {
                o.delete()
                return null
            }
            return o
        } catch (_: Throwable) {
            return null
        }
    }

    /**
     * Downloads an (unencrypted) HLS stream into the public Downloads folder as one MP4 (video and the
     * separate audio are joined). Shows up in the Downloads page with live progress.
     */
    fun downloadHls(
        ctx: Context, store: Store, mediaUrl: String, audioUrl: String?, baseName: String,
        ua: String, referer: String, msg: (String) -> Unit
    ) {
        val id = -System.currentTimeMillis()
        Thread {
            var uri: Uri? = null
            var st: State? = null
            val tmpV = File(ctx.cacheDir, "dlv_" + (-id))
            val tmpA = File(ctx.cacheDir, "dla_" + (-id))
            val tmpO = File(ctx.cacheDir, "dlo_" + (-id) + ".mp4")
            try {
                val vp = parsePlaylist(mediaUrl, ua, referer)
                if (vp.encrypted) {
                    msg("This stream is encrypted and cannot be saved")
                    return@Thread
                }
                if (vp.segs.isEmpty()) {
                    msg("No video data found")
                    return@Thread
                }
                var ap: Pl? = null
                if (audioUrl != null) {
                    try {
                        val x = parsePlaylist(audioUrl, ua, referer)
                        if (!x.encrypted && x.segs.isNotEmpty()) ap = x
                    } catch (_: Exception) {
                    }
                }
                val name = baseName + ".mp4"
                val cv = ContentValues()
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name)
                cv.put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                cv.put(MediaStore.Downloads.IS_PENDING, 1)
                val target = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: throw Exception("no file")
                uri = target
                val s = State(vp.segs.size + (ap?.segs?.size ?: 0))
                st = s
                states[id] = s
                store.addDownload(id, name, mediaUrl, target.toString())
                msg("Download started")
                pullSegs(vp, tmpV, 0, ua, referer, { s.cancel }, { s.done++ })
                if (ap != null) pullSegs(ap, tmpA, 0, ua, referer, { s.cancel }, { s.done++ })
                val joined = mux(tmpV, if (ap != null) tmpA else null, tmpO)
                val src = if (joined) tmpO else tmpV
                if (!joined && vp.init == null) {
                    val rn = ContentValues()
                    rn.put(MediaStore.Downloads.DISPLAY_NAME, baseName + ".ts")
                    rn.put(MediaStore.Downloads.MIME_TYPE, "video/mp2t")
                    ctx.contentResolver.update(target, rn, null, null)
                }
                ctx.contentResolver.openOutputStream(target)!!.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                val done = ContentValues()
                done.put(MediaStore.Downloads.IS_PENDING, 0)
                ctx.contentResolver.update(target, done, null, null)
                s.status = 1
                msg(if (joined) "Saved: $name" else "Saved (could not convert): $baseName")
            } catch (e: Exception) {
                st?.status = 2
                try {
                    uri?.let { ctx.contentResolver.delete(it, null, null) }
                } catch (_: Exception) {
                }
                if (st != null && !st.cancel) msg("Video download failed")
            } finally {
                tmpV.delete()
                tmpA.delete()
                tmpO.delete()
            }
        }.start()
    }

    /** Saves the pieces copied from a MediaSource player (video and maybe separate audio) as one MP4. */
    fun saveMse(
        ctx: Context, store: Store, files: Map<Int, File>, mimes: Map<Int, String>,
        baseName: String, url: String, msg: (String) -> Unit
    ) {
        Thread {
            val id = -System.currentTimeMillis()
            val tmpO = File(ctx.cacheDir, "mse_out_" + (-id) + ".mp4")
            var uri: Uri? = null
            try {
                fun isAudio(m: String) = m.lowercase().startsWith("audio/")
                // look at what is really inside each captured file
                val hasV = HashMap<Int, Boolean>()
                val hasA = HashMap<Int, Boolean>()
                for ((k, f) in files) {
                    var v = false
                    var a = false
                    try {
                        val ex = MediaExtractor()
                        ex.setDataSource(f.absolutePath)
                        for (i in 0 until ex.trackCount) {
                            val m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                            if (m.startsWith("video/")) v = true
                            if (m.startsWith("audio/")) a = true
                        }
                        ex.release()
                    } catch (_: Throwable) {
                        v = !isAudio(mimes[k] ?: "")
                        a = isAudio(mimes[k] ?: "")
                    }
                    hasV[k] = v
                    hasA[k] = a
                }
                val vk = files.filter { (k, f) -> hasV[k] == true && f.length() > 0 }.entries
                    .sortedByDescending { it.value.length() }.firstOrNull()?.key
                if (vk == null) {
                    msg("No video data captured")
                    return@Thread
                }
                val vf = files[vk]!!
                var af: File? = null
                if (hasA[vk] != true) {
                    af = files.filter { (k, f) -> k != vk && hasA[k] == true && hasV[k] != true && f.length() > 0 }.entries
                        .sortedByDescending { it.value.length() }.firstOrNull()?.value
                }
                val withAudio = hasA[vk] == true || af != null
                val joined = try {
                    mux(vf, af, tmpO)
                } catch (_: Exception) {
                    false
                }
                val src = if (joined) tmpO else vf
                val name = baseName + ".mp4"
                val cv = ContentValues()
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name)
                cv.put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                cv.put(MediaStore.Downloads.IS_PENDING, 1)
                val target = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: throw Exception("no file")
                uri = target
                ctx.contentResolver.openOutputStream(target)!!.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                val done = ContentValues()
                done.put(MediaStore.Downloads.IS_PENDING, 0)
                ctx.contentResolver.update(target, done, null, null)
                val s = State(1)
                s.done = 1
                s.status = 1
                states[id] = s
                store.addDownload(id, name, url, target.toString())
                msg(
                    "Saved: $name" + when {
                        !joined -> "  (could not convert - video may be silent / not seekable)"
                        !withAudio -> "  (no audio found on this page's player)"
                        else -> ""
                    }
                )
            } catch (e: Exception) {
                try {
                    uri?.let { ctx.contentResolver.delete(it, null, null) }
                } catch (_: Exception) {
                }
                msg("Could not save the video")
            } finally {
                for (f in files.values) f.delete()
                tmpO.delete()
            }
        }.start()
    }
}
