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
        "exoclick.net", "popmyads.com", "ad4game.com", "adk2.co", "adk2x.com", "bidbadger.com",
        "imasdk.googleapis.com", "spotxchange.com", "spotx.tv", "springserve.com", "tremorhub.com", "tremorvideo.com",
        "videoplaza.tv", "fwmrm.net", "freewheel.tv", "innovid.com", "vidazoo.com", "connatix.com",
        "primis.tech", "anyclip.com", "aniview.com", "adpone.com", "richaudience.com", "unrulymedia.com",
        "smartclip.net", "stickyadstv.com", "lkqd.net", "videoadex.com", "vdo.ai", "adingo.jp",
        "video-ads.com", "vidverto.io", "playwire.com", "bannerflow.com", "adsrvmedia.net",
        "streamrail.com", "brealtime.com", "ads.pubmatic.com", "adition.com", "adhigh.net"
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
            "vast[0-9]*\\.xml|vpaid|vmap|preroll|pre-roll|midroll|mid-roll|adtag|ad_tag|adbreak|adrequest|adslot|videoad|video-ad|vastad)([/._?=&-]|$)"
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


    /** No blue flash / focus ring when the user taps a link, video or image. Runs in every frame (also iframes). */
    const val TAP_JS = """
(function(){
  function a(){
    var p=document.head||document.documentElement;
    if(!p||document.getElementById('aura-tap'))return false;
    var s=document.createElement('style');
    s.id='aura-tap';
    s.textContent='*{-webkit-tap-highlight-color:rgba(0,0,0,0)!important}:focus,:focus-visible{outline:none!important}video,img,a,button{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none}video::selection,img::selection,video::-webkit-media-controls-overlay-enclosure{background:transparent!important}';
    p.appendChild(s);
    return true;
  }
  if(!a()){
    document.addEventListener('readystatechange',a);
    document.addEventListener('DOMContentLoaded',a);
  }
})();
"""

    /** Reports a playing video and its sources (with quality labels) to the app. */
    const val VIDEO_JS = """
(function(){
  if(window.__auraVid){return;}
  window.__auraVid=true;
  function lab(e){return e.getAttribute('label')||e.getAttribute('data-res')||e.getAttribute('data-quality')||e.getAttribute('res')||e.getAttribute('size')||e.getAttribute('title')||'';}
  function collect(v){
    var out=[];
    function add(u,l){if(u&&u.indexOf('http')===0){out.push({u:u,l:l||''});}}
    add(v.currentSrc||v.src,v.videoHeight?(v.videoHeight+'p'):'');
    var ss=v.querySelectorAll('source');
    for(var i=0;i<ss.length;i++){add(ss[i].src,lab(ss[i]));}
    return out;
  }
  function rep(e){
    var v=e.target;
    if(!v||v.tagName!=='VIDEO'){return;}
    try{AuraBridge.videoPlaying(JSON.stringify(collect(v)));}catch(x){}
  }
  document.addEventListener('play',rep,true);
  document.addEventListener('playing',rep,true);
})();
"""

    /**
     * Collapses the empty hole that a blocked ad leaves behind: hides blocked images / iframes /
     * videos and then every wrapper that has nothing visible left in it. Also hides tiny
     * "Advertisement" labels, outstream / floating video ads, and skips in-player video ads.
     * Needs window.__auraCfg and the AuraBridge.isBlocked(url) bridge call.
     */
    const val COLLAPSE_JS = """
(function(){
  if(window.__auraCollapse){return;}
  window.__auraCollapse=true;
  var ADSIG=/(^|[-_ ])(ad|ads|advert|advertisement|banner|sponsor|sponsored|outstream|floating-video|sticky-video)(?![a-z0-9])/i;
  var LABEL=/^(advertisements?|advertising|sponsored|ads?|promoted|বিজ্ঞাপন|প্রচারিত|विज्ञापन)\s*:?$/i;
  var SKIP='.videoAdUiSkipButton,.ytp-ad-skip-button,.ytp-skip-ad-button,.jw-skip,.vjs-skip-button,.ima-skip-button,.videoAdUiSkipContainer button,[class*="skip-ad"],[class*="ad-skip"],[class*="adSkip"],[id*="skip-ad"],[id*="skipAd"]';
  var ADPLAY='.vjs-ad-playing,.jw-flag-ads,.jw-state-ad,.ad-showing,.ad-interrupting,.plyr--ads,.plyr--ad-playing,.mejs-ad-playing,.fp-ad,.is-ad-playing,.ima-ad-container';
  function on(){var c=window.__auraCfg;return !c||c.ads!==false;}
  function hide(e){if(e.__auraX)return;e.__auraX=1;e.style.setProperty('display','none','important');}
  function skipTag(t){return t==='SCRIPT'||t==='STYLE'||t==='LINK'||t==='META'||t==='NOSCRIPT'||t==='TEMPLATE'||t==='BR';}
  function hollow(c,cs){
    if(c.querySelector('img,video,iframe,canvas,svg,input,button,textarea,select,picture,embed,object,audio')){return false;}
    if((c.textContent||'').replace(/\s+/g,'').length>0){return false;}
    return !cs||cs.backgroundImage==='none';
  }
  function shown(c){
    if(c.nodeType===3){return c.nodeValue.replace(/\s+/g,'').length>0;}
    if(c.nodeType!==1||skipTag(c.tagName)){return false;}
    if(c.__auraX){return false;}
    var cs=getComputedStyle(c);
    if(cs.display==='none'||cs.visibility==='hidden'){return false;}
    if(hollow(c,cs)){return false;}
    var r=c.getBoundingClientRect();
    if(r.width===0&&r.height===0&&!c.firstElementChild){return false;}
    return true;
  }
  function collapseUp(el){
    var p=el.parentElement,n=0;
    while(p&&p!==document.body&&p!==document.documentElement&&n<8){
      var kids=p.childNodes,any=false;
      for(var i=0;i<kids.length;i++){if(shown(kids[i])){any=true;break;}}
      if(any){break;}
      hide(p);
      p=p.parentElement;n++;
    }
  }
  function kill(el){hide(el);collapseUp(el);}
  function blocked(u){
    if(!u||u.indexOf('data:')===0||u.indexOf('blob:')===0||u.indexOf('javascript:')===0||u.indexOf('about:')===0){return false;}
    try{return !!(window.AuraBridge&&AuraBridge.isBlocked(u));}catch(e){return false;}
  }
  function srcOf(e){return e.currentSrc||e.src||e.getAttribute('data-src')||e.getAttribute('data-lazy-src')||'';}
  function adAncestor(e){
    var p=e,n=0;
    while(p&&p!==document.body&&n<5){
      var id=(p.id||'')+' '+(typeof p.className==='string'?p.className:'');
      if(ADSIG.test(id)){return p;}
      p=p.parentElement;n++;
    }
    return null;
  }
  function floating(e){
    var p=e,n=0;
    while(p&&p!==document.body&&n<6){
      var pos=getComputedStyle(p).position;
      if(pos==='fixed'||pos==='sticky'){return p;}
      p=p.parentElement;n++;
    }
    return null;
  }
  function playerAd(){
    var ps=document.querySelectorAll(ADPLAY);
    for(var i=0;i<ps.length;i++){
      var v=ps[i].tagName==='VIDEO'?ps[i]:ps[i].querySelector('video');
      if(!v){var par=ps[i].closest('.video-js,.jwplayer,.plyr,.mejs__container,.flowplayer');if(par)v=par.querySelector('video');}
      if(v&&isFinite(v.duration)&&v.duration>0&&v.duration<180){
        if(!v.__auraMuted){v.__auraMuted=true;v.__auraWas=v.muted;}
        v.muted=true;v.playbackRate=16;
        try{v.currentTime=Math.max(0,v.duration-0.1);}catch(e){}
      }
    }
    var vs=document.querySelectorAll('video');
    for(var j=0;j<vs.length;j++){
      var w=vs[j];
      if(w.__auraMuted&&!w.closest(ADPLAY)){w.__auraMuted=false;w.playbackRate=1;w.muted=!!w.__auraWas;}
    }
    var sk=document.querySelectorAll(SKIP);
    for(var k=0;k<sk.length;k++){
      var r=sk[k].getBoundingClientRect();
      if(r.width>0&&r.height>0){try{sk[k].click();}catch(e){}}
    }
  }
  function scan(){
    if(!on()||document.fullscreenElement){return;}
    try{
      var els=document.querySelectorAll('img,iframe,video,embed,object,source,picture');
      for(var i=0;i<els.length;i++){
        var e=els[i];
        if(e.__auraX){continue;}
        var t=e.tagName;
        if(t==='SOURCE'){
          var host=e.parentElement;
          if(host&&!host.__auraX&&blocked(e.src)){kill(host);}
          continue;
        }
        if(t==='PICTURE'){continue;}
        var u=srcOf(e);
        if(u&&e.__auraSrc!==u){
          e.__auraSrc=u;
          if(blocked(u)){kill(e);continue;}
        }
        if(t==='VIDEO'||t==='IFRAME'){
          var a=adAncestor(e);
          if(a){
            var f=floating(e);
            if(f){kill(f);continue;}
            if(t==='IFRAME'&&e.src&&!e.__auraChk){e.__auraChk=1;}
          }
        }
      }
      var ad=document.querySelectorAll(window.__auraAdSel||'ins.adsbygoogle,.adsbygoogle,[id^="google_ads"],[id^="div-gpt-ad"],[data-ad-slot]');
      for(var j=0;j<ad.length;j++){if(!ad[j].__auraSeen){ad[j].__auraSeen=1;hide(ad[j]);collapseUp(ad[j]);}}
      var sm=document.querySelectorAll('span,div,p,small,label,b,i,a');
      var cnt=0;
      for(var m=0;m<sm.length&&cnt<400;m++){
        var s=sm[m];
        if(s.__auraL||s.firstElementChild){continue;}
        s.__auraL=1;cnt++;
        var tx=s.textContent;
        if(tx&&tx.length<24&&LABEL.test(tx.trim())){
          var q=s.parentElement;
          if(q&&q.tagName!=='BODY'&&(q.children.length<=3)&&(q.innerText||'').trim().length<40){kill(q);}
          else{kill(s);}
        }
      }
      var sl=document.querySelectorAll('div[id],div[class],section[id],section[class],aside[id],aside[class],span[class]');
      for(var z=0;z<sl.length&&z<2500;z++){
        var d=sl[z];
        if(d.__auraX){continue;}
        var nm=(d.id||'')+' '+(typeof d.className==='string'?d.className:'');
        if(!ADSIG.test(nm)){continue;}
        var dcs=getComputedStyle(d);
        if(dcs.display!=='none'&&hollow(d,dcs)){kill(d);}
      }
      playerAd();
    }catch(x){}
  }
  document.addEventListener('error',function(ev){
    var e=ev.target;
    if(!on()||!e||!e.tagName){return;}
    var t=e.tagName;
    if(t==='IMG'||t==='VIDEO'||t==='SOURCE'||t==='IFRAME'){
      var u=srcOf(e);
      if(blocked(u)){kill(t==='SOURCE'&&e.parentElement?e.parentElement:e);}
    }
  },true);
  var tm=0;
  function later(){if(tm)return;tm=setTimeout(function(){tm=0;scan();},250);}
  try{
    new MutationObserver(later).observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['src','srcset','class']});
  }catch(e){}
  document.addEventListener('DOMContentLoaded',scan);
  window.addEventListener('load',scan);
  setInterval(scan,1500);
  scan();
})();
"""

    /** YouTube video ads: skip button, fast-forward the ad, hide ad slots. */
    const val YT_JS = """
(function(){
  if(window.__auraYT){return;}
  window.__auraYT=true;
  var DEL=['adPlacements','playerAds','adSlots','adBreakHeartbeatParams'];
  function prune(o){
    try{
      if(o&&typeof o==='object'){
        for(var i=0;i<DEL.length;i++){if(DEL[i] in o){delete o[DEL[i]];}}
        if(o.playerResponse){prune(o.playerResponse);}
      }
    }catch(e){}
    return o;
  }
  try{
    var pj=JSON.parse;
    JSON.parse=function(){return prune(pj.apply(this,arguments));};
    var rj=Response.prototype.json;
    Response.prototype.json=function(){return rj.apply(this,arguments).then(prune);};
    var pv;
    Object.defineProperty(window,'ytInitialPlayerResponse',{get:function(){return pv;},set:function(x){pv=prune(x);},configurable:true});
  }catch(e){}
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

    const val COSMETIC_SEL =
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
            "a[href*='juicyads'],a[href*='clickadu'],a[href*='trafficjunky']"

    const val COSMETIC_CSS = COSMETIC_SEL + "{display:none!important}"

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
