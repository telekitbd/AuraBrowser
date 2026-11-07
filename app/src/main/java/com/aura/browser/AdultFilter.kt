package com.aura.browser

import android.net.Uri

/** Blocks adult websites by domain list, host keywords and adult search queries. */
object AdultFilter {

    private val DOMAINS: Set<String> = hashSetOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
        "brazzers.com", "spankbang.com", "eporner.com", "tube8.com", "beeg.com", "tnaflix.com",
        "chaturbate.com", "stripchat.com", "bongacams.com", "livejasmin.com", "cam4.com",
        "camsoda.com", "myfreecams.com", "onlyfans.com", "fansly.com", "rule34.xxx", "rule34.paheal.net",
        "e-hentai.org", "exhentai.org", "nhentai.net", "hanime.tv", "hentaihaven.xxx", "fapello.com",
        "motherless.com", "xtube.com", "pornone.com", "porntrex.com", "hqporner.com", "4tube.com",
        "sunporno.com", "drtuber.com", "txxx.com", "hclips.com", "vporn.com", "xxnx.com", "hotmovs.com",
        "gotporn.com", "hitomi.la", "imagefap.com", "luscious.net", "sex.com", "xmoviesforyou.com",
        "pornpics.com", "porn.com", "pornhd.com", "tubegalore.com", "thumbzilla.com", "pornmd.com",
        "xvideos2.com", "xvideos3.com", "naughtyamerica.com", "realitykings.com", "bangbros.com",
        "mofos.com", "nubiles.net", "playboy.com", "penthouse.com", "adultfriendfinder.com",
        "ashleymadison.com", "erome.com", "fap.bar", "reddxxx.com", "sxyprn.com", "yespornplease.com",
        "ixxx.com", "analdin.com", "pichunter.com", "daftsex.com", "ok.xxx", "watchmygf.xxx"
    )

    private val CONTAINS = listOf(
        "porn", "xxx", "hentai", "xvideos", "xhamster", "xnxx", "redtube", "youporn", "brazzers",
        "sexcam", "sexvid", "freesex", "sexchat", "sextube", "sexstor", "camgirl", "nsfw",
        "fuckbook", "nudes", "livesex", "bdsm", "fetish"
    )

    private val TOKENS = hashSetOf("sex", "sexy", "sexo", "nude", "naked", "adult18")

    private val SEARCH_HOSTS = listOf(
        "google.", "bing.com", "duckduckgo.com", "yahoo.com", "brave.com", "yandex.", "ecosia.org",
        "startpage.com", "baidu.com"
    )

    private val Q_BAD = hashSetOf(
        "porn", "porno", "pornhub", "xxx", "xvideos", "xnxx", "xhamster", "hentai", "nsfw", "nude",
        "nudes", "onlyfans", "redtube", "youporn", "brazzers", "milf", "bdsm", "fetish", "blowjob",
        "handjob", "orgasm", "erotic", "camgirl", "camgirls", "stripchat", "chaturbate", "jav"
    )

    private val Q_SEX_WITH = hashSetOf(
        "video", "videos", "vid", "movie", "movies", "chat", "cam", "tube", "story", "stories",
        "girl", "girls", "hot", "free", "clip", "clips", "photo", "photos", "pic", "pics", "image",
        "images", "live", "bangla", "desi", "scandal"
    )

    private fun suffixMatch(host: String, set: Set<String>): Boolean = AdBlock.matches(host, set)

    fun isBlocked(url: String, custom: Set<String>): Boolean {
        return try {
            isBlocked(Uri.parse(url), custom)
        } catch (e: Exception) {
            false
        }
    }

    fun isBlocked(u: Uri, custom: Set<String>): Boolean {
        val scheme = u.scheme ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = (u.host ?: return false).lowercase()
        if (suffixMatch(host, DOMAINS) || suffixMatch(host, custom)) return true
        for (k in CONTAINS) if (host.contains(k)) return true
        val labels = host.split('.', '-')
        for (l in labels) if (TOKENS.contains(l)) return true
        if (SEARCH_HOSTS.any { host.contains(it) }) {
            val q = (u.getQueryParameter("q") ?: u.getQueryParameter("p")
                ?: u.getQueryParameter("query") ?: u.getQueryParameter("text") ?: "").lowercase()
            if (q.isNotEmpty() && isAdultQuery(q)) return true
        }
        return false
    }

    fun isAdultQuery(q: String): Boolean {
        val words = q.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.any { Q_BAD.contains(it) }) return true
        if (words.contains("sex") && words.any { Q_SEX_WITH.contains(it) }) return true
        for (w in words) for (k in CONTAINS) if (w.contains(k)) return true
        return false
    }

    fun blockedPage(host: String): String {
        val safeHost = host.replace("<", "").replace(">", "").replace("&", "")
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>Blocked</title></head>" +
            "<body style='margin:0;background:#0d0d12;color:#eee;font-family:sans-serif;" +
            "display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center'>" +
            "<div style='padding:28px;max-width:420px'>" +
            "<div style='font-size:64px'>&#128274;</div>" +
            "<h2 style='margin:12px 0 6px'>Site blocked</h2>" +
            "<p style='color:#aaa;line-height:1.5'>Aura Browser blocked <b>$safeHost</b> " +
            "because adult content protection is turned on.</p>" +
            "<p><a href='javascript:history.back()' style='display:inline-block;margin-top:10px;" +
            "padding:12px 28px;border-radius:26px;background:#7C8CFF;color:#000;text-decoration:none;" +
            "font-weight:bold'>Go back</a></p></div></body></html>"
    }
}
