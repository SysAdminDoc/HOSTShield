package com.hostshield.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v6.0: Network-based tracker detection database.
 *
 * Maps DNS domains to tracker owner + category using a checked local
 * attribution dataset plus legacy curated fallback entries.
 *
 * Unlike APK scanning (TrackerSignatureDb) which detects SDK presence, this
 * proves actual data exfiltration by matching live DNS queries against known
 * tracker domains.
 *
 * Lookup uses suffix matching: a query for "pixel.ad.doubleclick.net" matches
 * the entry for "doubleclick.net" (parent domain traversal).
 */
@Singleton
class NetworkTrackerDb private constructor(
    private val datasetOpener: () -> InputStream
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({
        context.assets.open(TRACKER_ATTRIBUTION_ASSET)
    })

    internal constructor(datasetText: String) : this({
        datasetText.byteInputStream(Charsets.UTF_8)
    })

    data class TrackerDomain(
        val owner: String,
        val category: String,
        val source: String = LEGACY_SOURCE
    )

    private val domains = ConcurrentHashMap<String, TrackerDomain>()

    val domainCount: Int get() = domains.size

    init {
        loadBuiltinDatabase()
        loadAttributionDataset()
        Log.i(TAG, "Loaded ${domains.size} tracker domains")
    }

    /**
     * Look up a domain against the tracker database.
     * Uses parent domain traversal: "x.y.tracker.com" matches "tracker.com".
     * Thread-safe (ConcurrentHashMap).
     */
    fun lookup(domain: String): TrackerDomain? {
        val lower = normalizeDomain(domain) ?: return null
        // Exact match
        domains[lower]?.let { return it }
        // Parent domain traversal
        val parts = lower.split(".")
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            domains[parent]?.let { return it }
        }
        return null
    }

    private fun add(
        domain: String,
        owner: String,
        category: String,
        source: String = LEGACY_SOURCE
    ) {
        val normalizedDomain = normalizeDomain(domain) ?: return
        val normalizedOwner = owner.trim()
        val normalizedCategory = category.trim()
        val normalizedSource = source.trim()
        if (normalizedOwner.isEmpty() || normalizedCategory.isEmpty() || normalizedSource.isEmpty()) return
        domains[normalizedDomain] = TrackerDomain(
            owner = normalizedOwner,
            category = normalizedCategory,
            source = normalizedSource
        )
    }

    private fun loadAttributionDataset() {
        try {
            datasetOpener().bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { index, rawLine ->
                    loadAttributionLine(rawLine, index + 1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tracker attribution asset unavailable: ${e.message}")
        }
    }

    private fun loadAttributionLine(rawLine: String, lineNumber: Int) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return

        val columns = rawLine.split('\t')
        if (columns.size < 4) {
            Log.w(TAG, "Skipping malformed tracker attribution row $lineNumber")
            return
        }

        add(
            domain = columns[0],
            owner = columns[1],
            category = columns[2],
            source = columns[3]
        )
    }

    private fun normalizeDomain(domain: String): String? {
        val normalized = domain.trim()
            .lowercase(Locale.ROOT)
            .removePrefix("http://")
            .removePrefix("https://")
            .trimStart('.')
            .trimEnd('.')
        if (normalized.isEmpty() || !normalized.contains('.')) return null
        if (normalized.any { it.isWhitespace() || it == '/' }) return null
        return normalized
    }

    private fun loadBuiltinDatabase() {
        // ── Advertising ──────────────────────────────────────────
        // Google Ads
        add("doubleclick.net", "Google", "Advertising")
        add("googleadservices.com", "Google", "Advertising")
        add("googlesyndication.com", "Google", "Advertising")
        add("googleads.g.doubleclick.net", "Google", "Advertising")
        add("pagead2.googlesyndication.com", "Google", "Advertising")
        add("adservice.google.com", "Google", "Advertising")
        add("admob.com", "Google", "Advertising")
        add("app-measurement.com", "Google", "Advertising")
        add("googletagmanager.com", "Google", "Advertising")
        add("googletagservices.com", "Google", "Advertising")
        add("google-analytics.com", "Google", "Analytics")
        // Facebook/Meta Ads
        add("facebook.net", "Meta", "Advertising")
        add("fbcdn.net", "Meta", "Advertising")
        add("facebook.com", "Meta", "Social")
        add("graph.facebook.com", "Meta", "Advertising")
        add("an.facebook.com", "Meta", "Advertising")
        add("pixel.facebook.com", "Meta", "Advertising")
        add("connect.facebook.net", "Meta", "Advertising")
        // Amazon Ads
        add("amazon-adsystem.com", "Amazon", "Advertising")
        add("aax.amazon-adsystem.com", "Amazon", "Advertising")
        add("mads.amazon-adsystem.com", "Amazon", "Advertising")
        // Unity Ads
        add("unityads.unity3d.com", "Unity", "Advertising")
        add("config.unityads.unity3d.com", "Unity", "Advertising")
        add("adserver.unityads.unity3d.com", "Unity", "Advertising")
        // AppLovin
        add("applovin.com", "AppLovin", "Advertising")
        add("applvn.com", "AppLovin", "Advertising")
        add("maxcdn.com", "AppLovin", "Advertising")
        // ironSource
        add("ironsrc.com", "ironSource", "Advertising")
        add("is.com", "ironSource", "Advertising")
        add("supersonicads.com", "ironSource", "Advertising")
        // Vungle
        add("vungle.com", "Vungle", "Advertising")
        // AdColony
        add("adcolony.com", "AdColony", "Advertising")
        // InMobi
        add("inmobi.com", "InMobi", "Advertising")
        // Chartboost
        add("chartboost.com", "Chartboost", "Advertising")
        // Mopub (Twitter)
        add("mopub.com", "Twitter", "Advertising")
        // Yahoo/Flurry
        add("flurry.com", "Yahoo", "Advertising")
        add("ads.yahoo.com", "Yahoo", "Advertising")
        // Criteo
        add("criteo.com", "Criteo", "Advertising")
        add("criteo.net", "Criteo", "Advertising")
        // Taboola
        add("taboola.com", "Taboola", "Advertising")
        add("taboolasyndication.com", "Taboola", "Advertising")
        // Outbrain
        add("outbrain.com", "Outbrain", "Advertising")
        // Smaato
        add("smaato.net", "Smaato", "Advertising")
        // Fyber
        add("fyber.com", "Fyber", "Advertising")
        add("inner-active.mobi", "Fyber", "Advertising")
        // StartApp
        add("startappservice.com", "StartApp", "Advertising")
        add("startapp.com", "StartApp", "Advertising")
        // Digital Turbine
        add("digitalturbine.com", "Digital Turbine", "Advertising")
        // Verve
        add("verve.com", "Verve", "Advertising")
        // Liftoff
        add("liftoff.io", "Liftoff", "Advertising")
        // Pangle (ByteDance)
        add("pangleglobal.com", "ByteDance", "Advertising")
        // ByteDance/TikTok
        add("byteoversea.com", "ByteDance", "Advertising")
        add("tiktokv.com", "ByteDance", "Advertising")
        // Twitter/X Ads
        add("ads-twitter.com", "Twitter", "Advertising")
        add("ads-api.twitter.com", "Twitter", "Advertising")
        // Snap
        add("sc-static.net", "Snap", "Advertising")
        add("tr.snapchat.com", "Snap", "Advertising")
        // Pinterest
        add("trk.pinterest.com", "Pinterest", "Advertising")
        // Microsoft/LinkedIn
        add("ads.linkedin.com", "Microsoft", "Advertising")
        add("bat.bing.com", "Microsoft", "Advertising")

        // ── Analytics ────────────────────────────────────────────
        // Firebase/Google
        add("firebaselogging-pa.googleapis.com", "Google", "Analytics")
        add("firebase-settings.crashlytics.com", "Google", "Analytics")
        add("app-analytics-services.com", "Google", "Analytics")
        add("settings.crashlytics.com", "Google", "Analytics")
        // Amplitude
        add("amplitude.com", "Amplitude", "Analytics")
        add("api.amplitude.com", "Amplitude", "Analytics")
        // Mixpanel
        add("mixpanel.com", "Mixpanel", "Analytics")
        add("api.mixpanel.com", "Mixpanel", "Analytics")
        // Segment
        add("segment.io", "Segment", "Analytics")
        add("segment.com", "Segment", "Analytics")
        add("api.segment.io", "Segment", "Analytics")
        add("cdn.segment.com", "Segment", "Analytics")
        // Adjust
        add("adjust.com", "Adjust", "Analytics")
        add("app.adjust.com", "Adjust", "Analytics")
        // AppsFlyer
        add("appsflyer.com", "AppsFlyer", "Analytics")
        add("t.appsflyer.com", "AppsFlyer", "Analytics")
        add("launches.appsflyer.com", "AppsFlyer", "Analytics")
        // Branch
        add("branch.io", "Branch", "Analytics")
        add("app.link", "Branch", "Analytics")
        add("bnc.lt", "Branch", "Analytics")
        // Kochava
        add("kochava.com", "Kochava", "Analytics")
        add("control.kochava.com", "Kochava", "Analytics")
        // Singular
        add("singular.net", "Singular", "Analytics")
        // Braze (Appboy)
        add("braze.com", "Braze", "Analytics")
        add("appboy.com", "Braze", "Analytics")
        add("brazesdk.com", "Braze", "Analytics")
        // CleverTap
        add("clevertap.com", "CleverTap", "Analytics")
        add("clevertap-prod.com", "CleverTap", "Analytics")
        // OneSignal
        add("onesignal.com", "OneSignal", "Analytics")
        // Airship
        add("urbanairship.com", "Airship", "Analytics")
        // Localytics
        add("localytics.com", "Localytics", "Analytics")
        // Heap
        add("heap.io", "Heap", "Analytics")
        add("heapanalytics.com", "Heap", "Analytics")
        // Countly
        add("count.ly", "Countly", "Analytics")
        // New Relic
        add("newrelic.com", "New Relic", "Analytics")
        add("nr-data.net", "New Relic", "Analytics")
        // Datadog
        add("datadoghq.com", "Datadog", "Analytics")
        add("browser-intake-datadoghq.com", "Datadog", "Analytics")
        // Bugsnag
        add("bugsnag.com", "Bugsnag", "Analytics")
        add("notify.bugsnag.com", "Bugsnag", "Analytics")
        // Sentry
        add("sentry.io", "Sentry", "Analytics")
        add("ingest.sentry.io", "Sentry", "Analytics")

        // ── Fingerprinting ───────────────────────────────────────
        // Device fingerprinting services
        add("fingerprintjs.com", "FingerprintJS", "Fingerprinting")
        add("fpjs.io", "FingerprintJS", "Fingerprinting")
        // ThreatMetrix
        add("threatmetrix.com", "LexisNexis", "Fingerprinting")
        add("online-metrix.net", "LexisNexis", "Fingerprinting")
        // PerimeterX / Human
        add("perimeterx.net", "Human", "Fingerprinting")
        add("px-cdn.net", "Human", "Fingerprinting")
        add("px-cloud.net", "Human", "Fingerprinting")
        // Device Atlas
        add("deviceatlas.com", "DeviceAtlas", "Fingerprinting")
        // Iovation
        add("iovation.com", "TransUnion", "Fingerprinting")

        // ── Social ───────────────────────────────────────────────
        // Facebook Social
        add("instagram.com", "Meta", "Social")
        add("cdninstagram.com", "Meta", "Social")
        // Twitter/X
        add("twitter.com", "Twitter", "Social")
        add("t.co", "Twitter", "Social")
        add("twimg.com", "Twitter", "Social")
        // Snap
        add("snapchat.com", "Snap", "Social")
        // TikTok
        add("tiktok.com", "ByteDance", "Social")
        add("tiktokcdn.com", "ByteDance", "Social")
        add("musical.ly", "ByteDance", "Social")
        // Pinterest
        add("pinterest.com", "Pinterest", "Social")
        add("pinimg.com", "Pinterest", "Social")
        // LinkedIn
        add("linkedin.com", "Microsoft", "Social")
        add("licdn.com", "Microsoft", "Social")
        // Reddit
        add("redditstatic.com", "Reddit", "Social")
        add("redditmedia.com", "Reddit", "Social")

        // ── Content Delivery / Tracking Pixels ───────────────────
        // Hotjar
        add("hotjar.com", "Hotjar", "Analytics")
        add("hotjar.io", "Hotjar", "Analytics")
        // FullStory
        add("fullstory.com", "FullStory", "Analytics")
        // Mouseflow
        add("mouseflow.com", "Mouseflow", "Analytics")
        // Lucky Orange
        add("luckyorange.com", "Lucky Orange", "Analytics")
        // Crazy Egg
        add("crazyegg.com", "Crazy Egg", "Analytics")
        // Optimizely
        add("optimizely.com", "Optimizely", "Analytics")
        add("cdn.optimizely.com", "Optimizely", "Analytics")
        // Salesforce / Krux
        add("krxd.net", "Salesforce", "Advertising")
        add("krux.net", "Salesforce", "Advertising")
        add("exacttarget.com", "Salesforce", "Analytics")
        // Oracle / BlueKai
        add("bluekai.com", "Oracle", "Advertising")
        add("bkrtx.com", "Oracle", "Advertising")
        add("addthis.com", "Oracle", "Advertising")
        // Lotame
        add("crwdcntrl.net", "Lotame", "Advertising")
        add("lotame.com", "Lotame", "Advertising")
        // ComScore
        add("comscore.com", "comScore", "Analytics")
        add("scorecardresearch.com", "comScore", "Analytics")
        // Nielsen
        add("imrworldwide.com", "Nielsen", "Analytics")
        // Moat (Oracle)
        add("moatads.com", "Oracle", "Advertising")
        add("moat.com", "Oracle", "Advertising")
        // DoubleVerify
        add("doubleverify.com", "DoubleVerify", "Advertising")
        add("dvtps.com", "DoubleVerify", "Advertising")
        // IAS (Integral Ad Science)
        add("adsafeprotected.com", "IAS", "Advertising")

        // ── Location Tracking ────────────────────────────────────
        add("safegraph.com", "SafeGraph", "Location")
        add("foursquare.com", "Foursquare", "Location")
        add("factual.com", "Foursquare", "Location")
        add("cuebiq.com", "Cuebiq", "Location")
        add("placed.com", "Foursquare", "Location")
        add("huq.io", "Huq", "Location")

        // ── Identity / Cross-Device ──────────────────────────────
        add("tapad.com", "Tapad", "Identification")
        add("liveramp.com", "LiveRamp", "Identification")
        add("drawbridge.com", "LinkedIn", "Identification")
        add("id5-sync.com", "ID5", "Identification")
        add("adsrvr.org", "The Trade Desk", "Identification")
        add("thetradedesk.com", "The Trade Desk", "Identification")
        add("demdex.net", "Adobe", "Identification")
        add("omtrdc.net", "Adobe", "Identification")
        add("2o7.net", "Adobe", "Identification")
        add("everesttech.net", "Adobe", "Identification")
        add("rubiconproject.com", "Magnite", "Advertising")
        add("pubmatic.com", "PubMatic", "Advertising")
        add("openx.net", "OpenX", "Advertising")
        add("casalemedia.com", "Index Exchange", "Advertising")
        add("indexww.com", "Index Exchange", "Advertising")
        add("bidswitch.net", "IPONWEB", "Advertising")
        add("33across.com", "33Across", "Advertising")
        add("sharethrough.com", "Sharethrough", "Advertising")
        add("triplelift.com", "TripleLift", "Advertising")
        add("spotxchange.com", "SpotX", "Advertising")
        add("smartadserver.com", "Equativ", "Advertising")

        // ── Data Brokers ─────────────────────────────────────────
        add("quantcast.com", "Quantcast", "Advertising")
        add("quantserve.com", "Quantcast", "Advertising")
        add("exelator.com", "Nielsen", "Advertising")
        add("agkn.com", "Neustar", "Identification")
        add("acxiom.com", "Acxiom", "Identification")

    }

    private companion object {
        private const val TAG = "NetworkTrackerDb"
        private const val TRACKER_ATTRIBUTION_ASSET = "tracker_attribution.tsv"
        private const val LEGACY_SOURCE = "hostshield-legacy-curated"
    }
}
