package com.aura.browser

import org.json.JSONObject

object AdBlock {

    val DOMAINS = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "googletagservices.com", "google-analytics.com", "googletagmanager.com", "2mdn.net",
        "adnxs.com", "taboola.com", "outbrain.com", "popads.net", "popcash.net",
        "propellerads.com", "exoclick.com", "adsterra.com", "onclickads.net",
        "ad-maven.com", "admaven.com", "mgid.com", "criteo.com", "criteo.net", "scorecardresearch.com",
        "moatads.com", "adsco.re", "juicyads.com", "trafficjunky.com",
        "clickadu.com", "hilltopads.net", "rubiconproject.com", "pubmatic.com",
        "openx.net", "adform.net", "smartadserver.com", "casalemedia.com", "indexww.com",
        "amazon-adsystem.com", "advertising.com", "adcolony.com", "applovin.com", "unity3d.com/ads",
        "chartboost.com", "inmobi.com", "mopub.com", "vungle.com", "startapp.com", "ironsrc.com",
        "revcontent.com", "zemanta.com", "yieldmo.com", "sharethrough.com", "teads.tv",
        "adsrvr.org", "bidswitch.net", "contextweb.com", "lijit.com", "sovrn.com", "33across.com",
        "media.net", "bidvertiser.com", "adbrite.com", "adbooth.com", "admixer.net", "adskeeper.com",
        "adcash.com", "a-ads.com", "coinzilla.com", "bitmedia.io", "monetag.com", "richpush.co",
        "pushwoosh.com", "pushengage.com", "onesignal.com/sdks", "evadav.com", "adspyglass.com",
        "ero-advertising.com", "tsyndicate.com", "trafficstars.com", "tubecorporate.com",
        "realsrv.com", "syndication.realsrv.com", "pemsrv.com", "tmsrv.com", "mc.yandex.ru",
        "an.yandex.ru", "adriver.ru", "begun.ru", "ads.yahoo.com", "analytics.yahoo.com",
        "adtech.com", "serving-sys.com", "flashtalking.com", "mediamath.com", "turn.com",
        "bluekai.com", "krxd.net", "demdex.net", "everesttech.net", "rlcdn.com", "agkn.com",
        "quantserve.com", "quantcast.com", "hotjar.com", "mouseflow.com", "crazyegg.com",
        "facebook.net/tr", "ads.twitter.com", "ads.linkedin.com", "ads.tiktok.com",
        "adsafeprotected.com", "doubleverify.com", "integral-marketing.com", "zedo.com",
        "yllix.com", "clickaine.com", "popunderjs.com", "go.oclasrv.com", "oclaserver.com",
        "adexchangeprediction.com", "profitableratecpm.com", "highperformanceformat.com",
        "effectivegatecpm.com", "displayvertising.com", "acint.net", "adx.ws", "vidoomy.com",
        "exosrv.com", "exdynsrv.com", "magsrv.com", "exoticads.com", "plugrush.com", "adtng.com",
        "profitablegatecpm.com", "highcpmrevenuegate.com", "effectivecpmgate.com", "creativecdn.com",
        "adtrafficquality.google", "pagead2.googlesyndication.com", "tpc.googlesyndication.com",
        "partner.googleadservices.com", "securepubads.g.doubleclick.net", "adsymptotic.com",
        "tapad.com", "adsafeprotected.com", "ads-twitter.com", "snap.licdn.com", "px.ads.linkedin.com",
        "analytics.tiktok.com", "bat.bing.com", "clarity.ms", "outbrainimg.com", "taboolasyndication.com",
        "popin.cc", "mgid.com", "adskeeper.co.uk", "lentainform.com", "directrev.com", "trafmag.com",
        "clickaine.com", "onclkds.com", "ad-score.com", "pubfuture-ad.com", "publisher.adcash.com",
        "clksite.com", "adtelligent.com", "bidgear.com", "propellerclick.com", "pushub.net",
        "notifpush.com", "push-sdk.com", "pushprofit.net", "wpnrtnmrewunrtok.xyz", "ad.atdmt.com",
        "ads.mopub.com", "googleads.g.doubleclick.net", "stats.g.doubleclick.net", "adbuddiz.com",
        "smaato.net", "tapjoy.com", "fyber.com", "adcolony.com", "liftoff.io", "pangleglobal.com",
        "bytedance.com/ads", "mintegral.com", "ogury.com", "gadsme.com", "yandexadexchange.net",
        "adnium.com", "kadam.net", "dolohen.com", "adsupply.com", "traffichaus.com", "trafficfactory.biz",
        "exoclick.net", "popmyads.com", "ad4game.com", "adk2.co", "adk2x.com", "bidbadger.com"
    ).filter { !it.contains("/") }

    val SET: Set<String> = DOMAINS.toHashSet()

    /** true when [host] or any parent domain is in [set]. */
    fun matches(host: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        var h = host.lowercase()
        while (true) {
            if (set.contains(h)) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            h = h.substring(i + 1)
        }
    }


    /** Registrable-domain guess (example.co.uk -> example.co.uk, a.b.example.com -> example.com). */
    fun root(host: String): String {
        val h = host.lowercase()
        val p = h.split('.')
        if (p.size <= 2) return h
        val sld = setOf("co", "com", "org", "net", "gov", "edu", "ac")
        val n = if (p[p.size - 1].length == 2 && sld.contains(p[p.size - 2])) 3 else 2
        return p.takeLast(n).joinToString(".")
    }

    fun sameSite(a: String, b: String): Boolean = root(a) == root(b)

    private val AD_URL = Regex(
        "(^|[/._?=&-])(pagead|adserver|adservice|adsystem|adframe|adbanner|adsbygoogle|popunder|" +
            "popads|popcash|banner-?ads?|ads?[0-9]*\\.js|adv[0-9]*\\.js|ad_?unit|prebid|" +
            "vast[0-9]*\\.xml|vpaid)([/._?=&-]|$)"
    )
    private val AD_PATH = Regex("/(ads?|adv|advert|adserver|banners?|sponsor(s|ed)?|popups?)/")

    /** Pattern based check, used only for third-party sub-requests. */
    fun looksLikeAd(url: String): Boolean {
        val u = url.lowercase()
        return AD_URL.containsMatchIn(u) || AD_PATH.containsMatchIn(u)
    }

    private val TRUSTED_POPUP = setOf(
        "accounts.google.com", "facebook.com", "appleid.apple.com", "login.microsoftonline.com",
        "login.live.com", "github.com", "twitter.com", "x.com", "paypal.com", "discord.com",
        "okta.com", "auth0.com", "line.me", "stripe.com", "checkout.stripe.com", "id.apple.com"
    )

    fun isTrustedPopup(host: String): Boolean = matches(host, TRUSTED_POPUP)

    fun isYtAd(host: String, path: String): Boolean {
        val h = host.lowercase()
        if (h != "youtube.com" && !h.endsWith(".youtube.com")) return false
        return path.startsWith("/api/stats/ads") || path.startsWith("/pagead/") || path.startsWith("/ptracking")
    }

    /**
     * Runs inside every page (settings come from window.__auraCfg): kills script pop-ups and
     * pop-unders, removes invisible click-catcher layers, anti-adblock walls, sticky ad bars.
     */
    const val ANTI_JS = """
(function(){
  if(window.__auraAnti){return;}
  window.__auraAnti=true;
  function cfg(){return window.__auraCfg||{pop:2,ovl:true,elem:true,ads:true};}
  var ok=['accounts.google.com','facebook.com','appleid.apple.com','login.microsoftonline.com','login.live.com','github.com','twitter.com','x.com','paypal.com','discord.com','okta.com','auth0.com','line.me','stripe.com','id.apple.com'];
  function root(h){var p=h.split('.');if(p.length<=2)return h;var sl={co:1,com:1,org:1,net:1,gov:1,edu:1,ac:1};var n=(p[p.length-1].length==2&&sl[p[p.length-2]])?3:2;return p.slice(-n).join('.');}
  var here=root(location.hostname);
  function hostOf(u){try{return new URL(u,location.href).hostname;}catch(e){return '';}}
  function trusted(h){for(var i=0;i<ok.length;i++){if(h===ok[i]||h.slice(-ok[i].length-1)==='.'+ok[i])return true;}return false;}
  var realOpen=window.open;
  var fake={closed:true,close:function(){},focus:function(){},blur:function(){},postMessage:function(){},location:{href:''},document:{write:function(){},close:function(){},open:function(){}}};
  try{
    window.open=function(u){
      var c=cfg();
      if(c.pop===0){return realOpen.apply(window,arguments);}
      var h=u?hostOf(String(u)):'';
      if(h&&trusted(h)){return realOpen.apply(window,arguments);}
      if(c.pop===1&&h&&root(h)===here){return realOpen.apply(window,arguments);}
      return fake;
    };
  }catch(e){}
  var WALL=/ad ?block|disable (your )?(ad|block)|turn off (your )?(ad|block)|whitelist (us|this)|allow (ads|advertis)/i;
  var ADSIG=/(^|[-_ ])(ad|ads|advert|advertisement|banner|sponsor|sponsored|promo|popup|popunder)(?![a-z0-9])/i;
  function big(r){return r.width>=innerWidth*0.6&&r.height>=innerHeight*0.6;}
  function clear(c){return c.backgroundColor==='rgba(0, 0, 0, 0)'||c.backgroundColor==='transparent'||parseFloat(c.opacity)<0.08;}
  function hide(e){e.style.setProperty('display','none','important');}
  function unlock(){
    try{
      var b=document.body,h=document.documentElement;
      if(getComputedStyle(b).overflow==='hidden'){b.style.setProperty('overflow','auto','important');}
      if(getComputedStyle(h).overflow==='hidden'){h.style.setProperty('overflow','auto','important');}
    }catch(e){}
  }
  function sweep(){
    var c=cfg();
    if(!c.ovl&&!(c.ads&&c.elem)){return;}
    try{
      if(document.fullscreenElement){return;}
      var hid=false;
      var W=innerWidth,H=innerHeight,k,i,st,e,cs,r;
      if(c.ovl){
        var pts=[[W/2,H/2],[W/2,H/4],[W/2,H*0.75],[W/4,H/2],[W*0.75,H/2]];
        for(k=0;k<pts.length;k++){
          st=document.elementsFromPoint(pts[k][0],pts[k][1]);
          for(i=0;i<st.length;i++){
            e=st[i];
            if(e===document.body||e===document.documentElement){break;}
            if(e.tagName==='VIDEO'||e.tagName==='CANVAS'||e.tagName==='IMG'){break;}
            cs=getComputedStyle(e);
            var pos=cs.position;
            if(pos!=='fixed'&&pos!=='absolute'&&pos!=='sticky'){continue;}
            r=e.getBoundingClientRect();
            if(!big(r)){continue;}
            var txt=(e.innerText||'').trim();
            if(pos==='fixed'&&WALL.test(txt)&&txt.length<600){hide(e);hid=true;continue;}
            if(e.querySelector('video,input,textarea,select,canvas')){continue;}
            if(txt.length>2){continue;}
            var z=parseInt(cs.zIndex)||0;
            var isLink=e.tagName==='A'&&e.href&&root(hostOf(e.href))!==here;
            var isFrame=e.tagName==='IFRAME'&&pos==='fixed'&&clear(cs);
            if(isFrame||isLink||(pos==='fixed'&&z>=100&&clear(cs))||(z>=1000&&clear(cs))){hide(e);hid=true;}
          }
        }
      }
      if(c.ads&&c.elem){
        var ep=[[W/2,12],[W/2,H-12],[W/2,H-60]];
        for(k=0;k<ep.length;k++){
          st=document.elementsFromPoint(ep[k][0],ep[k][1]);
          for(i=0;i<st.length;i++){
            e=st[i];
            if(e===document.body||e===document.documentElement){break;}
            cs=getComputedStyle(e);
            if(cs.position!=='fixed'&&cs.position!=='sticky'){continue;}
            r=e.getBoundingClientRect();
            if(r.height>H*0.3||r.width<W*0.5){continue;}
            var sig=ADSIG.test((e.id||'')+' '+(typeof e.className==='string'?e.className:''));
            var fr=e.querySelector('iframe,ins');
            var xf=false;
            if(fr){if(fr.tagName==='INS'){xf=true;}else if(fr.src){xf=root(hostOf(fr.src))!==here;}}
            if(sig||xf){hide(e);hid=true;}
          }
        }
      }
      if(hid){unlock();}
    }catch(x){}
  }
  var n=0;
  var t=setInterval(function(){sweep();n++;if(n>40){clearInterval(t);setInterval(sweep,5000);}},1000);
  document.addEventListener('DOMContentLoaded',sweep);
})();
"""

    /** YouTube video ads: skip button, fast-forward the ad, hide ad slots. */
    const val YT_JS = """
(function(){
  if(window.__auraYT){return;}
  window.__auraYT=true;
  var css='ytd-ad-slot-renderer,ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-promoted-video-renderer,ytd-banner-promo-renderer,ytd-in-feed-ad-layout-renderer,ytd-companion-slot-renderer,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytm-promoted-video-renderer,ad-slot-renderer,#player-ads,.ytp-ad-overlay-container,.ytp-ad-overlay-slot,.ytp-ad-image-overlay,.video-ads,.ytwFeedAdMetadataViewModelHost{display:none!important}';
  var s=document.createElement('style');
  s.textContent=css;
  (document.head||document.documentElement).appendChild(s);
  var muted=false;
  function tick(){
    try{
      var p=document.querySelector('.html5-video-player');
      var v=document.querySelector('video.html5-main-video')||document.querySelector('video');
      var ad=p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting'));
      var skip=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button');
      if(skip){skip.click();}
      var close=document.querySelector('.ytp-ad-overlay-close-button');
      if(close){close.click();}
      if(ad&&v&&isFinite(v.duration)&&v.duration>0){
        if(!muted){muted=v.muted?false:true;v.muted=true;}
        v.playbackRate=16;
        v.currentTime=Math.max(0,v.duration-0.1);
      }else if(v&&v.playbackRate===16){
        v.playbackRate=1;
        if(muted){v.muted=false;muted=false;}
      }
    }catch(e){}
  }
  setInterval(tick,250);
})();
"""

    const val COSMETIC_CSS =
        "ins.adsbygoogle,.adsbygoogle,[id^='google_ads'],[id^='div-gpt-ad'],[id^='aswift_'],[data-ad-slot]," +
            "[data-ad-client],[data-google-query-id],iframe[src*='doubleclick.net'],iframe[src*='googlesyndication']," +
            "iframe[id^='google_ads'],iframe[src*='//ads.'],iframe[src*='/adserver'],iframe[src*='popunder']," +
            "[id*='taboola'],[class*='taboola'],[id*='outbrain'],[class*='outbrain'],[id*='mgid'],[class*='mgid']," +
            "[class*='adsbox'],[class*='ad-banner'],[class*='advert-'],[id*='-advert'],[class*='ad-container']," +
            "[class*='ad_container'],[class*='ad-wrapper'],[class*='adwrapper'],[class*='ad-slot'],[class*='google-ad']," +
            "[class~='ad'],[class~='ads'],[class~='advert'],[class~='advertisement'],[id~='ad'],[id~='ads']," +
            "[id^='ad-'],[id^='ads-'],[id^='adv-'],[id*='popunder'],[class*='popunder'],[class*='pop-under']," +
            "[class*='sponsored-'],[class~='sponsored'],[aria-label='Advertisement'],[aria-label='advertisement']," +
            "a[href*='doubleclick.net'],a[href*='adsterra'],a[href*='popads'],a[href*='exoclick'],a[href*='propellerads']," +
            "a[href*='juicyads'],a[href*='clickadu'],a[href*='trafficjunky']" +
            "{display:none!important}"

    fun styleJs(css: String): String =
        "(function(){var id='aura-hide';var o=document.getElementById(id);if(o)o.remove();" +
            "var s=document.createElement('style');s.id=id;s.textContent=" + JSONObject.quote(css) + ";" +
            "(document.head||document.documentElement).appendChild(s);})();"

    /**
     * Manual ad blocker: the user taps any element on the page, can widen the selection with
     * "Parent", then taps "Hide". The CSS selector goes back to the app through AuraBridge.picked().
     */
    const val PICKER_JS = """
(function(){
  if(window.__auraPick){return;}
  window.__auraPick=true;
  var cur=null;
  var box=document.createElement('div');
  box.style.cssText='position:fixed;z-index:2147483646;pointer-events:none;border:2px solid #ff4081;background:rgba(255,64,129,.25);display:none;box-sizing:border-box;';
  var bar=document.createElement('div');
  bar.style.cssText='position:fixed;left:0;right:0;bottom:0;z-index:2147483647;background:#15151c;color:#fff;font:14px sans-serif;padding:10px;text-align:center;box-shadow:0 -2px 12px rgba(0,0,0,.6);';
  var msg=document.createElement('div');
  msg.textContent='Tap the ad or element you want to hide';
  msg.style.cssText='margin-bottom:8px;';
  function btn(t,c){var b=document.createElement('button');b.textContent=t;b.style.cssText='margin:0 4px;padding:9px 16px;border:0;border-radius:18px;font:600 14px sans-serif;color:#fff;background:'+c+';';return b;}
  var bHide=btn('Hide','#e91e63'),bUp=btn('Parent','#3f51b5'),bX=btn('Cancel','#555');
  bar.appendChild(msg);bar.appendChild(bHide);bar.appendChild(bUp);bar.appendChild(bX);
  document.documentElement.appendChild(box);document.documentElement.appendChild(bar);
  function uniq(id){try{return document.querySelectorAll('#'+CSS.escape(id)).length===1;}catch(e){return false;}}
  function sel(el){
    var path=[];
    while(el&&el.nodeType===1&&el!==document.documentElement){
      if(el.id&&uniq(el.id)){path.unshift('#'+CSS.escape(el.id));break;}
      var s=el.tagName.toLowerCase();
      var p=el.parentElement;
      if(p){
        var same=[];
        for(var i=0;i<p.children.length;i++){if(p.children[i].tagName===el.tagName)same.push(p.children[i]);}
        if(same.length>1){s+=':nth-of-type('+(same.indexOf(el)+1)+')';}
      }
      path.unshift(s);
      el=p;
    }
    return path.join(' > ');
  }
  function show(el){
    cur=el;
    var r=el.getBoundingClientRect();
    box.style.display='block';
    box.style.left=r.left+'px';box.style.top=r.top+'px';
    box.style.width=r.width+'px';box.style.height=r.height+'px';
    msg.textContent=el.tagName.toLowerCase()+(el.id?'#'+el.id:'')+'  -  Hide it?';
  }
  function cleanup(){
    document.removeEventListener('click',onClick,true);
    box.remove();bar.remove();window.__auraPick=false;
  }
  function onClick(e){
    if(bar.contains(e.target))return;
    e.preventDefault();e.stopPropagation();
    show(e.target);
  }
  document.addEventListener('click',onClick,true);
  bHide.onclick=function(){
    if(!cur){msg.textContent='Tap an element first';return;}
    var s=sel(cur);
    try{cur.style.setProperty('display','none','important');}catch(e){}
    cleanup();
    try{AuraBridge.picked(s);}catch(e){}
  };
  bUp.onclick=function(){
    if(cur&&cur.parentElement&&cur.parentElement!==document.body&&cur.parentElement!==document.documentElement){show(cur.parentElement);}
  };
  bX.onclick=function(){cleanup();try{AuraBridge.cancelled();}catch(e){}};
})();
"""

    const val READER_JS = """
(function(){
  var best=null,bs=0;
  var cands=document.querySelectorAll('article,main,section,div');
  for(var i=0;i<cands.length;i++){
    var c=cands[i];var ps=c.querySelectorAll(':scope > p');var l=0;
    for(var j=0;j<ps.length;j++){l+=(ps[j].innerText||'').length;}
    if(l>bs){bs=l;best=c;}
  }
  if(!best)best=document.body;
  var out='';
  var els=best.querySelectorAll('h1,h2,h3,p,li,blockquote,img');
  for(var k=0;k<els.length;k++){
    var e=els[k];var t=e.tagName.toLowerCase();
    if(t==='img'){if(e.naturalWidth>150){out+='<img src="'+e.src+'">';}}
    else{
      var x=(e.innerText||'').trim();
      if(x.length>0){out+='<'+t+'>'+x.replace(/&/g,'&amp;').replace(/</g,'&lt;')+'</'+t+'>';}
    }
  }
  return JSON.stringify({title:document.title,body:out});
})()
"""
}

/** Extra ad/tracker domains downloaded from public block lists (hosts / domain / AdGuard formats). */
object AdLists {
    @Volatile
    var set: Set<String> = emptySet()
    val count: Int get() = set.size

    private const val FILE = "adlists.txt"
    private const val MAX_AGE = 7L * 24 * 60 * 60 * 1000

    private val URLS = listOf(
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext",
        "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt",
        "https://adguardteam.github.io/HostlistsRegistry/assets/filter_2.txt"
    )

    /** Never block these bare domains, whatever a list says. */
    private val NEVER = setOf(
        "google.com", "youtube.com", "gstatic.com", "googlevideo.com", "ytimg.com", "googleapis.com",
        "googleusercontent.com", "facebook.com", "fbcdn.net", "github.com", "githubusercontent.com",
        "microsoft.com", "live.com", "apple.com", "icloud.com", "amazon.com", "cloudflare.com",
        "wikipedia.org", "twitter.com", "x.com", "instagram.com", "whatsapp.com", "telegram.org",
        "bing.com", "yahoo.com", "duckduckgo.com", "netflix.com", "tiktok.com", "reddit.com"
    )

    private val DOMAIN = Regex("^[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}$")

    fun load(ctx: android.content.Context) {
        val f = java.io.File(ctx.filesDir, FILE)
        Thread {
            try {
                if (f.exists()) {
                    val s = HashSet<String>()
                    f.forEachLine { if (it.isNotEmpty()) s.add(it) }
                    set = s
                }
            } catch (_: Exception) {
            }
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > MAX_AGE) {
                update(ctx) { }
            }
        }.start()
    }

    private fun parse(raw: String): String? {
        var l = raw.trim().lowercase()
        if (l.isEmpty() || l[0] == '#' || l[0] == '!' || l[0] == '[' || l.startsWith("@@")) return null
        if (l.startsWith("||")) {
            l = l.substring(2)
            val end = l.indexOfFirst { it == '^' || it == '/' || it == '$' || it == '*' }
            if (end <= 0 || l[end] == '/' || l[end] == '*') return null
            l = l.substring(0, end)
        } else {
            val hash = l.indexOf('#')
            if (hash > 0) l = l.substring(0, hash).trim()
            val parts = l.split(Regex("\\s+"))
            l = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) parts[1]
            else if (parts.size == 1) parts[0] else return null
        }
        if (l == "localhost" || !DOMAIN.matches(l) || NEVER.contains(l)) return null
        return l
    }

    /** Downloads the lists on a background thread. [done] gets the number of domains (0 = failed). */
    fun update(ctx: android.content.Context, done: (Int) -> Unit) {
        Thread {
            val all = HashSet<String>()
            for (u in URLS) {
                try {
                    val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 15000
                    c.readTimeout = 30000
                    c.setRequestProperty("User-Agent", "AuraBrowser")
                    if (c.responseCode == 200) {
                        c.inputStream.bufferedReader().useLines { lines ->
                            for (line in lines) {
                                val d = parse(line) ?: continue
                                all.add(d)
                                if (all.size > 400000) break
                            }
                        }
                    }
                    c.disconnect()
                } catch (_: Exception) {
                }
            }
            if (all.isNotEmpty()) {
                try {
                    java.io.File(ctx.filesDir, FILE).writeText(all.joinToString("\n"))
                } catch (_: Exception) {
                }
                set = all
            }
            done(all.size)
        }.start()
    }
}
