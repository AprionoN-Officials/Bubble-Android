package com.bubble.screentools.lens

import java.net.URI

/**
 * Lightweight, 100% offline WebView Ad & Popunder Blocker for [VisualSearchWebActivity].
 * - Blocks known third-party ad networks, popunder redirects, and tracking beacons via O(labels) lookup.
 * - Never blocks the active visual search engine's own apex domain (except dedicated ad subdomains like
 *   an.yandex.ru / yabs.yandex.ru) and never blocks Cloudflare / reCAPTCHA / hCaptcha verification hosts.
 */
object WebViewAdBlocker {

    private val NEVER_BLOCK_HOSTS = setOf(
        "challenges.cloudflare.com",
        "hcaptcha.com",
        "js.hcaptcha.com",
        "imgs.hcaptcha.com",
        "recaptcha.net",
        "www.recaptcha.net",
        "www.gstatic.com",
        "gstatic.com",
        "tineye.com",
        "saucenao.com",
        "trace.moe",
        "api.trace.moe",
        "bing.com",
        "www.bing.com"
    )

    /** Dedicated ad/tracker subdomains blocked even if their parent apex is an active search engine. */
    private val STRICT_AD_SUBDOMAINS = setOf(
        "an.yandex.ru",
        "yabs.yandex.ru",
        "mc.yandex.ru",
        "ads.yandex.ru",
        "awaps.yandex.ru",
        "an.yandex.com",
        "yabs.yandex.com",
        "mc.yandex.com",
        "bat.bing.com",
        "r.bing.com"
    )

    private val BLOCKED_AD_DOMAINS = setOf(
        // Google / DoubleClick / AdSense
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "fundingchoicesmessages.google.com",
        // Native content recommendation & display networks
        "taboola.com",
        "outbrain.com",
        "mgid.com",
        "revcontent.com",
        "zergnet.com",
        "criteo.com",
        "criteo.net",
        "amazon-adsystem.com",
        "adnxs.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "indexww.com",
        "smartadserver.com",
        "media.net",
        "adroll.com",
        "bidswitch.net",
        "sharethrough.com",
        "triplelift.com",
        "teads.tv",
        "33across.com",
        "lijit.com",
        "sovrn.com",
        "yieldmo.com",
        "gumgum.com",
        "spotxchange.com",
        "spotx.tv",
        // Popunder / aggressive ad networks common on image-hosting & reverse-search sites
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "onclkds.com",
        "adsterra.com",
        "highperformanceformat.com",
        "highcpmrevenuegate.com",
        "profitabledisplaynetwork.com",
        "topcreativeformat.com",
        "exoclick.com",
        "exosrv.com",
        "realsrv.com",
        "juicyads.com",
        "trafficjunky.net",
        "trafficstars.com",
        "tsyndicate.com",
        "adskeeper.co.uk",
        "adskeeper.com",
        "hilltopads.net",
        "clickadu.com",
        "adcash.com",
        "acscdn.com",
        "ero-advertising.com",
        "plugrush.com",
        "admaven.com",
        "a-ads.com",
        "coinzilla.com",
        "bitmedia.io",
        // Trackers / telemetry
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "clarity.ms",
        "connect.facebook.net",
        "ads-twitter.com",
        "analytics.tiktok.com",
        "ads.linkedin.com",
        "ads.reddit.com",
        "moatads.com",
        "doubleverify.com",
        "adsafeprotected.com",
        "omtrdc.net",
        "demdex.net",
        "krxd.net",
        "bluekai.com",
        "rlcdn.com",
        "crwdcntrl.net",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "unityads.unity3d.com",
        "applovin.com",
        "vungle.com",
        "ironsrc.com",
        "inmobi.com",
        "chartboost.com"
    )

    private val AD_PATH_MARKERS = listOf(
        "/pagead/",
        "/ads/js/",
        "/adserver/",
        "/popunder",
        "adsbygoogle.js",
        "prebid.js",
        "gpt.js"
    )

    /**
     * Returns true when [requestUrl] should be blocked as an ad/tracker/popunder.
     * For main-frame navigations, only blocks explicit ad/popunder redirect domains so normal
     * search result navigation is never disrupted.
     */
    fun shouldBlockRequest(
        requestUrl: String?,
        pageUrl: String? = null,
        isMainFrame: Boolean = false
    ): Boolean {
        if (requestUrl.isNullOrBlank()) return false
        val trimmed = requestUrl.trim()
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false

        val reqHost = extractHost(trimmed) ?: return false
        if (reqHost in NEVER_BLOCK_HOSTS) return false
        if (lower.contains("recaptcha") || lower.contains("hcaptcha") || lower.contains("cloudflare")) {
            return false
        }

        // Strict ad subdomains (e.g. an.yandex.ru) are always blocked on sub-resources
        if (!isMainFrame && reqHost in STRICT_AD_SUBDOMAINS) {
            return true
        }

        // Check domain/subdomain match against ad blocklist
        if (matchesDomainSet(reqHost, BLOCKED_AD_DOMAINS)) {
            return true
        }

        // On sub-resources only, also catch obvious ad script paths on third-party hosts
        if (!isMainFrame) {
            val pageHost = pageUrl?.let { extractHost(it) }
            val isSameOrigin = pageHost != null && (reqHost == pageHost || reqHost.endsWith(".$pageHost"))
            if (!isSameOrigin && AD_PATH_MARKERS.any { lower.contains(it) }) {
                return true
            }
        }

        return false
    }

    /**
     * Lightweight cosmetic CSS injected on page finish to collapse empty ad slots
     * without ever touching file upload inputs or visual search results.
     */
    const val COSMETIC_AD_HIDE_JS: String = """
        (function() {
            if (document.getElementById('bubble-adblock-style')) return;
            var style = document.createElement('style');
            style.id = 'bubble-adblock-style';
            style.textContent =
                'ins.adsbygoogle, iframe[id^="google_ads_iframe"], iframe[src*="doubleclick.net"], ' +
                'iframe[src*="googlesyndication"], div[id^="div-gpt-ad"], [data-ad-slot], ' +
                '.adsbygoogle, .taboola-container, [id*="taboola-"], [class*="outbrain"], ' +
                '.OUTBRAIN, .mgbox, .mgheader, [id^="yandex_rtb_"], .ya-partner, ' +
                'a[href*="doubleclick.net"], a[href*="adsterra"], a[href*="exoclick"] ' +
                '{ display: none !important; visibility: hidden !important; height: 0 !important; min-height: 0 !important; }';
            (document.head || document.documentElement).appendChild(style);
        })();
    """

    private fun extractHost(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase()?.trimEnd('.')
        if (!host.isNullOrBlank()) return host
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        return afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .lowercase()
            .trimEnd('.')
            .takeIf { it.isNotBlank() }
    }

    private fun matchesDomainSet(host: String, blocklist: Set<String>): Boolean {
        if (host in blocklist) return true
        var dotIdx = host.indexOf('.')
        while (dotIdx >= 0 && dotIdx + 1 < host.length) {
            val suffix = host.substring(dotIdx + 1)
            if (suffix in blocklist) return true
            dotIdx = host.indexOf('.', dotIdx + 1)
        }
        return false
    }
}
