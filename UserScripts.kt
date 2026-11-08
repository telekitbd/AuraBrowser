package com.aura.browser

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

class UScript(
    var id: Long,
    var name: String,
    var match: String,
    var code: String,
    var on: Boolean
)

/** Small Tampermonkey-style user scripts: run your own JavaScript on pages you choose. */
object UserScripts {
    private const val KEY = "userscripts"

    // a few of the common GM_* helpers so most simple scripts keep working
    private const val SHIM =
        "var GM_addStyle=function(c){var s=document.createElement('style');s.textContent=c;" +
            "(document.head||document.documentElement).appendChild(s);return s;};" +
            "var GM_log=function(){console.log.apply(console,arguments);};" +
            "var GM_getValue=function(k,d){try{var v=localStorage.getItem('aura_gm_'+k);" +
            "return v===null?d:JSON.parse(v);}catch(e){return d;}};" +
            "var GM_setValue=function(k,v){try{localStorage.setItem('aura_gm_'+k,JSON.stringify(v));}catch(e){}};" +
            "var GM_deleteValue=function(k){try{localStorage.removeItem('aura_gm_'+k);}catch(e){}};" +
            "var unsafeWindow=window;"

    fun list(store: Store): MutableList<UScript> {
        val out = mutableListOf<UScript>()
        try {
            val arr = JSONArray(store.getJson(KEY))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    UScript(
                        o.optLong("i"), o.optString("n"), o.optString("m"),
                        o.optString("c"), o.optBoolean("o", true)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun write(store: Store, l: List<UScript>) {
        val arr = JSONArray()
        for (s in l) {
            arr.put(
                JSONObject().put("i", s.id).put("n", s.name).put("m", s.match)
                    .put("c", s.code).put("o", s.on)
            )
        }
        store.putJson(KEY, arr.toString())
    }

    fun save(store: Store, s: UScript) {
        val l = list(store)
        val i = l.indexOfFirst { it.id == s.id }
        if (i >= 0) l[i] = s else l.add(0, s)
        write(store, l)
    }

    fun delete(store: Store, id: Long) {
        val l = list(store)
        l.removeAll { it.id == id }
        write(store, l)
    }

    /** Reads @name and @match / @include lines from a pasted Tampermonkey / Greasemonkey header. */
    fun parseHeader(code: String): Pair<String, String> {
        var name = ""
        val m = mutableListOf<String>()
        var inHead = false
        val re = Regex("^//\\s*@(\\S+)\\s+(.*)$")
        for (line in code.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("// ==UserScript==")) {
                inHead = true
                continue
            }
            if (t.startsWith("// ==/UserScript==")) break
            if (!inHead) continue
            val r = re.find(t) ?: continue
            val k = r.groupValues[1]
            val v = r.groupValues[2].trim()
            if (k == "name" && name.isEmpty()) name = v
            if (k == "match" || k == "include") m.add(v)
        }
        return Pair(name, m.joinToString("\n"))
    }

    private fun globToRegex(p: String): Regex {
        val sb = StringBuilder("^")
        for (ch in p) {
            if (ch == '*') sb.append(".*") else sb.append(Regex.escape(ch.toString()))
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    // Patterns: a single star (all sites), <all_urls>, a plain domain (with subdomains) or a full match pattern.
    fun matches(url: String, patterns: String): Boolean {
        val host = hostOf(url).lowercase()
        for (raw in patterns.split('\n', ',', ' ', ';')) {
            val p = raw.trim()
            if (p.isEmpty()) continue
            if (p == "*" || p == "<all_urls>") return true
            if (!p.contains("://")) {
                val d = p.lowercase().removePrefix("www.")
                if (host == d || host.endsWith(".$d")) return true
                continue
            }
            if (globToRegex(p).matches(url)) return true
            if (p.contains("://*.") && globToRegex(p.replace("://*.", "://")).matches(url)) return true
        }
        return false
    }

    fun run(wv: WebView, url: String, store: Store) {
        if (!store.getBool("userscripts_on", true)) return
        if (!url.startsWith("http")) return
        for (s in list(store)) {
            if (!s.on || s.code.isBlank() || !matches(url, s.match)) continue
            val js = "(function(){try{" + SHIM + s.code + "\n}catch(e){console.error('Aura user script error',e);}})();"
            wv.evaluateJavascript(js, null)
        }
    }
}
