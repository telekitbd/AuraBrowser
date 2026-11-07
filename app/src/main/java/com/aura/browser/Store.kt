package com.aura.browser

import android.content.Context
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

data class Item(val title: String, val url: String, val time: Long)

data class DlItem(val id: Long, val name: String, val url: String, val time: Long)

data class Memo(val id: Long, val title: String, val body: String, val url: String, val time: Long)

class BTab(val webView: WebView, val incognito: Boolean) {
    var isHome = true
    var title = "Home"
    var url = ""
    var loading = false
    var progress = 0
    var thumb: android.graphics.Bitmap? = null
    var picking = false

    @Volatile
    var lastTouch = 0L

    @Volatile
    var blocked = 0
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("aura", Context.MODE_PRIVATE)

    private fun readList(key: String): MutableList<Item> {
        val out = mutableListOf<Item>()
        try {
            val arr = JSONArray(sp.getString(key, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Item(o.optString("t"), o.optString("u"), o.optLong("d")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun writeList(key: String, list: List<Item>) {
        val arr = JSONArray()
        for (x in list) {
            arr.put(JSONObject().put("t", x.title).put("u", x.url).put("d", x.time))
        }
        sp.edit().putString(key, arr.toString()).apply()
    }

    // ---- bookmarks ----
    fun getBookmarks(): MutableList<Item> = readList("bookmarks")

    fun isBookmarked(url: String): Boolean = getBookmarks().any { it.url == url }

    fun addBookmark(title: String, url: String) {
        val l = getBookmarks()
        if (l.any { it.url == url }) return
        l.add(0, Item(title.ifBlank { url }, url, System.currentTimeMillis()))
        writeList("bookmarks", l)
    }

    fun removeBookmark(url: String) {
        val l = getBookmarks()
        l.removeAll { it.url == url }
        writeList("bookmarks", l)
    }

    // ---- history ----
    fun getHistory(): MutableList<Item> = readList("history")

    fun addHistory(title: String, url: String) {
        if (!url.startsWith("http")) return
        val l = getHistory()
        l.removeAll { it.url == url }
        l.add(0, Item(title.ifBlank { url }, url, System.currentTimeMillis()))
        while (l.size > 500) l.removeAt(l.size - 1)
        writeList("history", l)
    }

    fun removeHistory(url: String) {
        val l = getHistory()
        l.removeAll { it.url == url }
        writeList("history", l)
    }

    fun clearHistory() {
        sp.edit().putString("history", "[]").apply()
    }

    // ---- home shortcuts ----
    fun getDial(): MutableList<Item> {
        if (!sp.contains("dial")) {
            return mutableListOf(
                Item("Google", "https://www.google.com", 0),
                Item("YouTube", "https://m.youtube.com", 0),
                Item("Facebook", "https://m.facebook.com", 0),
                Item("Instagram", "https://www.instagram.com", 0),
                Item("X", "https://x.com", 0)
            )
        }
        return readList("dial")
    }

    fun addDial(title: String, url: String) {
        val l = getDial()
        l.add(Item(title.ifBlank { hostOf(url) }, url, System.currentTimeMillis()))
        writeList("dial", l)
    }

    fun removeDial(url: String) {
        val l = getDial()
        l.removeAll { it.url == url }
        writeList("dial", l)
    }

    // ---- downloads ----
    fun getDownloads(): MutableList<DlItem> {
        val out = mutableListOf<DlItem>()
        try {
            val arr = JSONArray(sp.getString("downloads", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(DlItem(o.optLong("i"), o.optString("n"), o.optString("u"), o.optLong("d")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun writeDownloads(list: List<DlItem>) {
        val arr = JSONArray()
        for (x in list) {
            arr.put(JSONObject().put("i", x.id).put("n", x.name).put("u", x.url).put("d", x.time))
        }
        sp.edit().putString("downloads", arr.toString()).apply()
    }

    fun addDownload(id: Long, name: String, url: String) {
        val l = getDownloads()
        l.removeAll { it.id == id }
        l.add(0, DlItem(id, name, url, System.currentTimeMillis()))
        while (l.size > 300) l.removeAt(l.size - 1)
        writeDownloads(l)
    }

    fun removeDownload(id: Long) {
        val l = getDownloads()
        l.removeAll { it.id == id }
        writeDownloads(l)
    }

    fun clearDownloads() {
        sp.edit().putString("downloads", "[]").apply()
    }

    // ---- generic string lists (custom blocked domains, allow-list ...) ----
    fun strings(key: String): MutableList<String> {
        val out = mutableListOf<String>()
        try {
            val arr = JSONArray(sp.getString("l_$key", "[]"))
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (_: Exception) {
        }
        return out
    }

    private fun writeStrings(key: String, l: List<String>) {
        val arr = JSONArray()
        for (x in l) arr.put(x)
        sp.edit().putString("l_$key", arr.toString()).apply()
    }

    fun addString(key: String, v: String): Boolean {
        val l = strings(key)
        if (l.contains(v)) return false
        l.add(v)
        writeStrings(key, l)
        return true
    }

    /** Keeps the newest 150 blocked links (pop-ups, redirects, app launches) for the log screen. */
    fun logBlocked(v: String) {
        val l = strings("blocklog")
        l.remove(v)
        l.add(v)
        while (l.size > 150) l.removeAt(0)
        writeStrings("blocklog", l)
    }

    fun removeString(key: String, v: String) {
        val l = strings(key)
        l.remove(v)
        writeStrings(key, l)
    }

    // ---- hidden elements (manual ad blocker): rule = host + newline + css selector ----
    fun getHideRules(host: String): List<String> {
        val pre = host + "\n"
        return strings("hide").filter { it.startsWith(pre) }.map { it.substring(pre.length) }
    }

    fun addHideRule(host: String, selector: String) {
        addString("hide", host + "\n" + selector)
    }

    fun removeHideRule(raw: String) {
        removeString("hide", raw)
    }

    // ---- stats ----
    fun getBlockedTotal(): Long = sp.getLong("stat_blocked", 0L)

    fun addBlocked(n: Long) {
        if (n <= 0) return
        sp.edit().putLong("stat_blocked", getBlockedTotal() + n).apply()
    }

    // ---- settings ----
    fun getBool(key: String, def: Boolean): Boolean = sp.getBoolean("s_$key", def)
    fun setBool(key: String, v: Boolean) {
        sp.edit().putBoolean("s_$key", v).apply()
    }

    fun getStr(key: String, def: String): String = sp.getString("s_$key", def) ?: def
    fun setStr(key: String, v: String) {
        sp.edit().putString("s_$key", v).apply()
    }

    fun getInt(key: String, def: Int): Int = sp.getInt("s_$key", def)
    fun setInt(key: String, v: Int) {
        sp.edit().putInt("s_$key", v).apply()
    }

    // ---- memos ----
    fun getMemos(): MutableList<Memo> {
        val out = mutableListOf<Memo>()
        try {
            val arr = JSONArray(sp.getString("memos", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Memo(o.optLong("i"), o.optString("t"), o.optString("b"), o.optString("u"), o.optLong("d")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun writeMemos(list: List<Memo>) {
        val arr = JSONArray()
        for (m in list) {
            arr.put(JSONObject().put("i", m.id).put("t", m.title).put("b", m.body).put("u", m.url).put("d", m.time))
        }
        sp.edit().putString("memos", arr.toString()).apply()
    }

    fun saveMemo(m: Memo) {
        val l = getMemos()
        l.removeAll { it.id == m.id }
        l.add(0, m.copy(time = System.currentTimeMillis()))
        writeMemos(l)
    }

    fun removeMemo(id: Long) {
        val l = getMemos()
        l.removeAll { it.id == id }
        writeMemos(l)
    }
}
