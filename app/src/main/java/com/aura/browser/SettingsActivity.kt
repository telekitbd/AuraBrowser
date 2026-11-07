package com.aura.browser

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var box: LinearLayout
    private lateinit var card: LinearLayout
    private val zooms = listOf(80, 100, 125, 150, 175)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), 0, dp(4), 0)
        val back = iconButton(R.drawable.ic_arrow_back, 48, 56)
        back.setOnClickListener { finish() }
        val title = tv("Settings", 22f)
        title.setPadding(dp(8), 0, 0, 0)
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(56)))
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val sv = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(10), 0, dp(10), dp(24))
        sv.addView(box)

        root.addView(bar)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun section(name: String) {
        val h = tv(name.uppercase(), 12f, 0xFF9FA8FF.toInt())
        h.typeface = Typeface.DEFAULT_BOLD
        h.setPadding(dp(10), dp(18), 0, dp(6))
        box.addView(h)
        card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(0xFF1E1E1E.toInt())
        bg.cornerRadius = dp(20).toFloat()
        card.background = bg
        box.addView(card)
    }

    private fun row(name: String, sub: String, trailing: View?, onClick: () -> Unit) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(16), dp(13), dp(16), dp(13))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(name, 16f))
        if (sub.isNotEmpty()) {
            val s = tv(sub, 13f, 0xFF999999.toInt())
            s.setPadding(0, dp(2), 0, 0)
            col.addView(s)
        }
        r.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) r.addView(trailing)
        r.setOnClickListener { onClick() }
        card.addView(r)
    }

    private fun sw(on: Boolean): SwitchCompat {
        val s = SwitchCompat(this)
        s.isChecked = on
        s.isClickable = false
        s.isFocusable = false
        return s
    }

    private fun toggleRow(name: String, sub: String, key: String, def: Boolean) {
        val on = store.getBool(key, def)
        row(name, sub, sw(on)) {
            store.setBool(key, !on)
            render()
        }
    }

    private fun openList(mode: String) {
        startActivity(Intent(this, ManageActivity::class.java).putExtra("mode", mode))
    }

    private fun render() {
        box.removeAllViews()

        section("General")
        row("Search engine", ENGINES[store.getInt("engine", 0).coerceIn(0, ENGINES.size - 1)], null) { pickEngine() }
        toggleRow("JavaScript", "Turn off only if a site misbehaves", "js", true)
        toggleRow("Keep screen on", "Screen stays awake while browsing", "keepon", false)

        section("Safety")
        toggleRow("HTTPS-only mode", "Upgrade http:// links to https://", "httpsonly", false)
        toggleRow("Safe Browsing", "Warn about dangerous sites", "safebrowsing", true)

        section("Ad blocker")
        toggleRow("Ad blocker", "Blocks ad, tracker and video-ad requests", "adblock", true)
        toggleRow("Element inspection", "Hides banners, sticky bars and ad frames. Uses more memory", "elem", true)
        toggleRow("Hide ad boxes", "Remove empty ad containers on pages", "cosmetic", true)
        row("Ad whitelist", "${store.strings("adallow").size} sites where ads are allowed", null) { openList("adallow") }
        row("Update ad lists", "${AdLists.count} extra domains loaded - tap to refresh", null) {
            toast("Updating ad lists...")
            AdLists.update(applicationContext) { n ->
                runOnUiThread {
                    toast(if (n > 0) "Ad lists updated: $n domains" else "Update failed - check internet")
                    render()
                }
            }
        }
        row("Blocked ad domains", "${store.strings("addom").size} domains you added", null) { openList("addomains") }
        row("Hidden elements", "${store.strings("hide").size} elements you hid manually", null) { openList("hide") }

        section("Block area")
        toggleRow("Block area", "Long press an object, then tap it to hide it", "blockarea", false)

        section("Overlay blocker")
        toggleRow("Overlay blocker", "Removes invisible click layers and anti-adblock walls", "overlay", true)
        row("Overlay whitelist", "${store.strings("ovlallow").size} sites where overlays are allowed", null) { openList("ovlallow") }

        section("Popup blocker")
        row("Popup blocker", POP_LEVELS[store.getInt("pop_level", 2).coerceIn(0, 2)], null) { pickPopup() }
        row("Popup whitelist", "${store.strings("popallow").size} sites where popups are allowed", null) { openList("popallow") }

        section("Block app execution")
        row("Block app execution", APP_MODES[store.getInt("appexec", 0).coerceIn(0, 2)], null) { pickAppExec() }

        section("Log")
        row("Blocked links", "${store.strings("blocklog").size} recent pop-ups, redirects and app launches", null) { openList("blocklog") }
        row("Blocked so far", store.getBlockedTotal().toString() + " requests", null) { }

        section("VPN - open blocked sites")
        row("VPN", VPN_MODES[store.getInt("vpn_mode", 0).coerceIn(0, 2)], null) { pickVpn() }
        row(
            "My own proxy",
            store.getStr("vpn_custom", "").ifEmpty { "Not set - host:port or socks5://host:port" }, null
        ) { customProxyDialog() }
        row("Find a new free server", if (VpnProxy.finding) "Searching..." else "Tap to switch server", null) {
            if (store.getInt("vpn_mode", 0) != 1) {
                toast("Choose 'Free servers' in VPN first")
            } else {
                vpnWarn(store) {
                    toast("Looking for a free server - up to 45 seconds...")
                    VpnProxy.findFree(store) { m -> runOnUiThread { toast(m); render() } }
                }
            }
        }
        row(
            "Status",
            if (VpnProxy.active.isEmpty()) "Not connected" else "Connected via " + VpnProxy.active.substringAfter("://"),
            null
        ) { render() }

        section("Privacy")
        toggleRow("Block third-party cookies", "Stops cross-site tracking", "block3p", false)
        toggleRow("Block images", "Save data, load pages faster", "blockimg", false)
        toggleRow("Clear data on exit", "Cookies, cache and history", "clearexit", false)

        section("Display")
        toggleRow("Dark mode for pages", "Turn web pages dark", "dark", false)
        toggleRow("Desktop site", "Request desktop version of pages", "desktop", false)
        row("Text size", store.getInt("zoom", 100).toString() + "%", null) {
            val i = zooms.indexOf(store.getInt("zoom", 100))
            store.setInt("zoom", zooms[(i + 1) % zooms.size])
            render()
        }

        section("About")
        row("Aura Browser", "Version 1.1", null) { }
    }

    private val POP_LEVELS = listOf(
        "Off - allow all popups",
        "Normal - block ad popups",
        "Strong - block all popups and auto-redirects"
    )
    private val APP_MODES = listOf(
        "Always blocked - sites cannot open other apps",
        "Ask each time",
        "Allowed"
    )

    private fun pickPopup() {
        AlertDialog.Builder(this)
            .setTitle("Popup blocker")
            .setSingleChoiceItems(POP_LEVELS.toTypedArray(), store.getInt("pop_level", 2)) { d, w ->
                store.setInt("pop_level", w)
                d.dismiss()
                render()
            }
            .show()
    }

    private fun pickAppExec() {
        AlertDialog.Builder(this)
            .setTitle("Block app execution")
            .setSingleChoiceItems(APP_MODES.toTypedArray(), store.getInt("appexec", 0)) { d, w ->
                store.setInt("appexec", w)
                d.dismiss()
                render()
            }
            .show()
    }

    private val VPN_MODES = listOf(
        "Off",
        "Free servers (automatic)",
        "My own proxy"
    )

    private fun pickVpn() {
        AlertDialog.Builder(this)
            .setTitle("VPN")
            .setSingleChoiceItems(VPN_MODES.toTypedArray(), store.getInt("vpn_mode", 0)) { d, w ->
                d.dismiss()
                if (w == 1) vpnWarn(store) { setVpn(1) } else setVpn(w)
            }
            .show()
    }

    private fun setVpn(mode: Int) {
        store.setInt("vpn_mode", mode)
        if (mode == 2 && VpnProxy.normalize(store.getStr("vpn_custom", "")).isEmpty()) {
            render()
            customProxyDialog()
            return
        }
        if (mode == 1) toast("Looking for a free server - up to 45 seconds...")
        VpnProxy.apply(applicationContext, store) { m -> runOnUiThread { toast(m); render() } }
        render()
    }

    private fun customProxyDialog() {
        val e = EditText(this)
        e.hint = "1.2.3.4:8080  or  socks5://1.2.3.4:1080"
        e.setText(store.getStr("vpn_custom", ""))
        e.maxLines = 1
        AlertDialog.Builder(this)
            .setTitle("My own proxy")
            .setView(e)
            .setPositiveButton("Save") { _, _ ->
                val c = VpnProxy.normalize(e.text.toString())
                if (c.isEmpty()) {
                    toast("Use host:port or socks5://host:port")
                } else {
                    store.setStr("vpn_custom", c)
                    if (store.getInt("vpn_mode", 0) == 2) {
                        VpnProxy.apply(applicationContext, store) { m -> runOnUiThread { toast(m); render() } }
                    }
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickEngine() {
        AlertDialog.Builder(this)
            .setTitle("Search engine")
            .setSingleChoiceItems(ENGINES.toTypedArray(), store.getInt("engine", 0)) { d, w ->
                store.setInt("engine", w)
                d.dismiss()
                render()
            }
            .show()
    }

    private fun pinMenu(hasPin: Boolean) {
        if (!hasPin) {
            newPinDialog()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Parental PIN")
            .setItems(arrayOf("Change PIN", "Remove PIN")) { _, w ->
                askPin {
                    if (w == 0) {
                        newPinDialog()
                    } else {
                        store.setStr("pin", "")
                        toast("PIN removed")
                        render()
                    }
                }
            }
            .show()
    }

    private fun newPinDialog() {
        val e = EditText(this)
        e.hint = "New PIN (4+ digits)"
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val b = LinearLayout(this)
        b.setPadding(dp(20), dp(8), dp(20), 0)
        b.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("Set PIN")
            .setView(b)
            .setPositiveButton("Save") { _, _ ->
                val p = e.text.toString()
                if (p.length < 4) {
                    toast("PIN needs at least 4 digits")
                } else {
                    store.setStr("pin", sha256(p))
                    toast("PIN saved")
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
