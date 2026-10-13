package com.animekompi.app

import android.net.Uri

object AdBlocker {
    private val hosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "adservice.google.com", "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "juicyads.com", "adsterra.com", "highperformanceformat.com", "effectivegatecpm.com",
        "profitablecpmrate.com", "onclickads.net", "histats.com", "adnxs.com", "taboola.com",
        "outbrain.com", "mgid.com", "trafficjunky.com", "clickadu.com", "hilltopads.net",
        "a-ads.com", "ad-maven.com", "admaven.com", "popcdn.com", "adcash.com", "yllix.com"
    )

    fun isAd(url: String): Boolean {
        val u = Uri.parse(url)
        val h = u.host?.lowercase() ?: return false
        if (hosts.any { h == it || h.endsWith(".$it") }) return true
        val p = (u.path ?: "").lowercase()
        return p.contains("/ads/") || p.contains("popunder") || p.contains("/adserver")
    }

    // Sembunyikan elemen iklan lewat CSS + matikan window.open (anti popup)
    val injectJs = """
        (function(){
          window.open=function(){return null;};
          var css='.ads,.adsbox,.ad-container,.ad-banner,.banner-ads,.adblock,ins.adsbygoogle,'+
            '[id^="div-gpt-ad"],[id*="google_ads"],[id*="banner-ad"],'+
            'iframe[src*="doubleclick"],iframe[src*="/ads/"],a[href*="popunder"]{display:none!important;visibility:hidden!important;height:0!important}';
          var s=document.getElementById('__noads');
          if(!s){s=document.createElement('style');s.id='__noads';document.head.appendChild(s);}
          s.textContent=css;
        })();
    """.trimIndent()
}
