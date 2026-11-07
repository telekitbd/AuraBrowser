package com.aura.browser

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Sends every page opened inside Aura through a proxy server so blocked sites can open.
 * Modes (store "vpn_mode"): 0 off, 1 free public servers (found and tested automatically),
 * 2 your own proxy (store "vpn_custom").
 * This covers WebView pages only - not downloads and not other apps.
 */
object VpnProxy {
    /** Proxy rule that is applied right now, "" = direct connection. */
    @Volatile
    var active: String = ""

    @Volatile
    var finding = false

    private var lastRetry = 0L

    private val LISTS = listOf(
        "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=5000&country=all&ssl=all&anonymity=all",
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
        "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/http/data.txt"
    )

    private val ADDR = Regex("(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{2,5})")
    private val CUSTOM_URL = Regex("^(https?|socks4|socks5)://[^\\s/]+$")
    private val CUSTOM_PLAIN = Regex("^[^\\s/:]+:\\d{2,5}$")

    fun supported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /** Returns a clean proxy rule ("host:port" or "scheme://host:port") or "" when invalid. */
    fun normalize(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        val l = if (t.contains("://")) t.substringBefore("://").lowercase() + "://" + t.substringAfter("://") else t
        return if (CUSTOM_URL.matches(l) || CUSTOM_PLAIN.matches(l)) l else ""
    }

    fun shouldRetry(): Boolean {
        val n = System.currentTimeMillis()
        if (finding || n - lastRetry < 60000) return false
        lastRetry = n
        return true
    }

    /** Applies the saved mode. [onDone] gets a short message and may run on any thread. */
    fun apply(ctx: Context, store: Store, onDone: (String) -> Unit) {
        when (store.getInt("vpn_mode", 0)) {
            1 -> {
                val saved = store.getStr("vpn_free", "")
                if (saved.isNotEmpty()) setRule(saved, onDone) else findFree(store, onDone)
            }
            2 -> {
                val c = normalize(store.getStr("vpn_custom", ""))
                if (c.isEmpty()) onDone("Add your proxy address first") else setRule(c, onDone)
            }
            else -> clear(onDone)
        }
    }

    private fun setRule(rule: String, onDone: (String) -> Unit) {
        if (!supported()) {
            onDone("Update Android System WebView to use the VPN")
            return
        }
        try {
            // no direct fallback on purpose: if the server dies the page fails instead of leaking
            val cfg = ProxyConfig.Builder().addProxyRule(rule).build()
            ProxyController.getInstance().setProxyOverride(cfg, Executor { it.run() }) {
                active = rule
                onDone("VPN on: " + rule.substringAfter("://"))
            }
        } catch (e: Exception) {
            onDone("VPN failed: " + (e.message ?: "unknown error"))
        }
    }

    private fun clear(onDone: (String) -> Unit) {
        if (!supported()) {
            active = ""
            onDone("VPN off")
            return
        }
        try {
            ProxyController.getInstance().clearProxyOverride(Executor { it.run() }) {
                active = ""
                onDone("VPN off")
            }
        } catch (e: Exception) {
            active = ""
            onDone("VPN off")
        }
    }

    /** Downloads public proxy lists, tests many at once over HTTPS and keeps the first that works. */
    fun findFree(store: Store, onDone: (String) -> Unit) {
        if (finding) return
        finding = true
        Thread {
            try {
                val all = LinkedHashSet<String>()
                for (u in LISTS) {
                    try {
                        val c = URL(u).openConnection() as HttpURLConnection
                        c.connectTimeout = 10000
                        c.readTimeout = 15000
                        if (c.responseCode == 200) {
                            c.inputStream.bufferedReader().useLines { lines ->
                                for (line in lines) {
                                    val m = ADDR.find(line) ?: continue
                                    all.add(m.groupValues[1] + ":" + m.groupValues[2])
                                    if (all.size > 4000) break
                                }
                            }
                        }
                        c.disconnect()
                    } catch (_: Exception) {
                    }
                }
                if (all.isEmpty()) {
                    onDone("Could not load the free server list - check internet")
                    return@Thread
                }
                val cand = all.toMutableList()
                cand.shuffle()
                val pick = cand.take(150)
                val pool = Executors.newFixedThreadPool(24)
                val found = AtomicReference<String?>(null)
                val left = AtomicInteger(pick.size)
                val latch = CountDownLatch(1)
                for (c in pick) {
                    pool.execute {
                        try {
                            if (found.get() == null && works(c)) {
                                if (found.compareAndSet(null, c)) latch.countDown()
                            }
                        } catch (_: Exception) {
                        } finally {
                            if (left.decrementAndGet() == 0) latch.countDown()
                        }
                    }
                }
                latch.await(45, TimeUnit.SECONDS)
                pool.shutdownNow()
                val f = found.get()
                if (f == null) {
                    onDone("No free server worked right now. Try again or use your own proxy.")
                } else {
                    store.setStr("vpn_free", f)
                    setRule(f, onDone)
                }
            } catch (e: Exception) {
                onDone("Could not find a free server")
            } finally {
                finding = false
            }
        }.start()
    }

    /** A server counts only if it can open an HTTPS page with a valid certificate. */
    private fun works(c: String): Boolean {
        val i = c.lastIndexOf(':')
        val px = Proxy(Proxy.Type.HTTP, InetSocketAddress(c.substring(0, i), c.substring(i + 1).toInt()))
        val conn = URL("https://www.gstatic.com/generate_204").openConnection(px) as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.instanceFollowRedirects = false
        return try {
            conn.responseCode == 204
        } finally {
            conn.disconnect()
        }
    }
}
