package com.aura.browser

import android.content.Context
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

data class Item(val title: String, val url: String, val time: Long)

class BTab(val webView: WebView, val incognito: Boolean) {
    var isHome = true
    var title = "Home"
    var url = ""
    var loading = false
    var progress = 0
    var thumb: android.graphics.Bitmap? = null
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

    // ---- settings ----
    fun getBool(key: String, def: Boolean): Boolean = sp.getBoolean("s_$key", def)
    fun setBool(key: String, v: Boolean) {
        sp.edit().putBoolean("s_$key", v).apply()
    }

    fun getInt(key: String, def: Int): Int = sp.getInt("s_$key", def)
    fun setInt(key: String, v: Int) {
        sp.edit().putInt("s_$key", v).apply()
    }
}
