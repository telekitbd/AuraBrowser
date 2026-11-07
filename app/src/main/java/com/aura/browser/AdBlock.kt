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
        "effectivegatecpm.com", "displayvertising.com", "acint.net", "adx.ws", "vidoomy.com"
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

    const val COSMETIC_CSS =
        "ins.adsbygoogle,.adsbygoogle,[id^='google_ads'],[id^='div-gpt-ad'],[id^='aswift_']," +
            "iframe[src*='doubleclick.net'],iframe[src*='googlesyndication'],iframe[id^='google_ads']," +
            "[id*='taboola'],[class*='taboola'],[id*='outbrain'],[class*='outbrain']," +
            "[class*='adsbox'],[class*='ad-banner'],[class*='advert-'],[id*='-advert']," +
            "[class*='sponsored-'],a[href*='doubleclick.net'],a[href*='adsterra'],a[href*='popads']" +
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
