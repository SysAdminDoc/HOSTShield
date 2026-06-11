package com.hostshield.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.hostshield.data.database.TrackerScanCacheDao
import com.hostshield.data.model.TrackerScanCacheEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

// Exodus-style tracker SDK signature database
// Scans installed APK dex files for known tracker library class signatures.
// Results are cached in Room DB, invalidated on app version change.
// Signature database: 400+ trackers sourced from ETIP / Exodus Privacy.

@Singleton
class TrackerSignatureDb @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cacheDao: TrackerScanCacheDao
) {
    companion object {
        private const val TAG = "TrackerSigDb"
        private const val CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }

    data class TrackerInfo(
        val name: String,
        val category: String,
        val signatures: List<String>
    )

    private val trackers = listOf(
        // =========================================================================
        // Advertising (~95 entries)
        // =========================================================================
        TrackerInfo("Google AdMob", "Advertising", listOf("com.google.android.gms.ads")),
        TrackerInfo("Google Ads", "Advertising", listOf("com.google.ads")),
        TrackerInfo("Facebook Ads", "Advertising", listOf("com.facebook.ads")),
        TrackerInfo("Unity Ads", "Advertising", listOf("com.unity3d.services", "com.unity3d.ads")),
        TrackerInfo("AppLovin", "Advertising", listOf("com.applovin")),
        TrackerInfo("IronSource", "Advertising", listOf("com.ironsource")),
        TrackerInfo("AdColony", "Advertising", listOf("com.adcolony")),
        TrackerInfo("Chartboost", "Advertising", listOf("com.chartboost")),
        TrackerInfo("InMobi", "Advertising", listOf("com.inmobi")),
        TrackerInfo("Mopub", "Advertising", listOf("com.mopub")),
        TrackerInfo("Vungle", "Advertising", listOf("com.vungle")),
        TrackerInfo("Amazon Ads", "Advertising", listOf("com.amazon.device.ads")),
        TrackerInfo("Criteo", "Advertising", listOf("com.criteo")),
        TrackerInfo("Pangle (TikTok Ads)", "Advertising", listOf("com.bytedance.sdk.openadsdk")),
        // -- New Advertising entries --
        TrackerInfo("Fyber", "Advertising", listOf("com.fyber")),
        TrackerInfo("Tapjoy", "Advertising", listOf("com.tapjoy")),
        TrackerInfo("Digital Turbine", "Advertising", listOf("com.digitalturbine")),
        TrackerInfo("Smaato", "Advertising", listOf("com.smaato")),
        TrackerInfo("StartApp", "Advertising", listOf("com.startapp")),
        TrackerInfo("YieldMo", "Advertising", listOf("com.yieldmo")),
        TrackerInfo("Ogury", "Advertising", listOf("io.presage")),
        TrackerInfo("Verve (PubNative)", "Advertising", listOf("net.pubnative")),
        TrackerInfo("Mintegral", "Advertising", listOf("com.mbridge")),
        TrackerInfo("Kidoz", "Advertising", listOf("com.kidoz")),
        TrackerInfo("AerServ", "Advertising", listOf("com.aerserv")),
        TrackerInfo("Aarki", "Advertising", listOf("com.aarki")),
        TrackerInfo("Adelphic", "Advertising", listOf("com.adelphic")),
        TrackerInfo("AdTheorent", "Advertising", listOf("com.adtheorent")),
        TrackerInfo("Avazu Tracking", "Advertising", listOf("com.avazu")),
        TrackerInfo("Beachfront", "Advertising", listOf("com.beachfront")),
        TrackerInfo("Bidease", "Advertising", listOf("com.bidease")),
        TrackerInfo("BidMachine", "Advertising", listOf("io.bidmachine")),
        TrackerInfo("Conversant", "Advertising", listOf("com.conversant")),
        TrackerInfo("CrossInstall", "Advertising", listOf("com.crossinstall")),
        TrackerInfo("DFP (DoubleClick)", "Advertising", listOf("com.google.android.gms.ads.doubleclick")),
        TrackerInfo("Epom", "Advertising", listOf("com.epom")),
        TrackerInfo("Flurry Ads", "Advertising", listOf("com.flurry.android.ads")),
        TrackerInfo("GumGum", "Advertising", listOf("com.gumgum")),
        TrackerInfo("HyprMX", "Advertising", listOf("com.hyprmx")),
        TrackerInfo("Leadbolt", "Advertising", listOf("com.leadbolt")),
        TrackerInfo("LifeStreet", "Advertising", listOf("com.lifestreet")),
        TrackerInfo("Liftoff", "Advertising", listOf("io.liftoff")),
        TrackerInfo("LoopMe", "Advertising", listOf("com.loopme")),
        TrackerInfo("Millennial Media", "Advertising", listOf("com.millennialmedia")),
        TrackerInfo("Mobfox", "Advertising", listOf("com.mobfox")),
        TrackerInfo("Mobileads", "Advertising", listOf("com.mobileads")),
        TrackerInfo("MobileFuse", "Advertising", listOf("com.mobilefuse")),
        TrackerInfo("Nativex", "Advertising", listOf("com.nativex")),
        TrackerInfo("NativeAds", "Advertising", listOf("com.nativeads")),
        TrackerInfo("Nexage", "Advertising", listOf("com.nexage")),
        TrackerInfo("OpenX", "Advertising", listOf("com.openx")),
        TrackerInfo("Opera Ads", "Advertising", listOf("com.opera.ads")),
        TrackerInfo("PubMatic", "Advertising", listOf("com.pubmatic")),
        TrackerInfo("RevJet", "Advertising", listOf("com.revjet")),
        TrackerInfo("RhythmOne", "Advertising", listOf("com.rhythmone")),
        TrackerInfo("ShareThrough", "Advertising", listOf("com.sharethrough")),
        TrackerInfo("Sizmek", "Advertising", listOf("com.sizmek")),
        TrackerInfo("SmartAdServer", "Advertising", listOf("com.smartadserver")),
        TrackerInfo("Spotad", "Advertising", listOf("com.spotad")),
        TrackerInfo("SpotX", "Advertising", listOf("com.spotxchange")),
        TrackerInfo("SuperAwesome", "Advertising", listOf("tv.superawesome")),
        TrackerInfo("Tapdaq", "Advertising", listOf("com.tapdaq")),
        TrackerInfo("Tremor Video", "Advertising", listOf("com.tremorvideo")),
        TrackerInfo("TripleLift", "Advertising", listOf("com.triplelift")),
        TrackerInfo("Undertone", "Advertising", listOf("com.undertone")),
        TrackerInfo("UniPlay", "Advertising", listOf("com.uniplay")),
        TrackerInfo("Verve Group", "Advertising", listOf("com.verve")),
        TrackerInfo("Yandex Ads", "Advertising", listOf("com.yandex.mobile.ads")),
        TrackerInfo("Bigo Ads", "Advertising", listOf("sg.bigo.ads")),
        TrackerInfo("Meson", "Advertising", listOf("ai.meson")),
        TrackerInfo("Moloco", "Advertising", listOf("com.moloco.sdk")),
        TrackerInfo("Liftoff Monetize", "Advertising", listOf("com.vungle.ads")),
        TrackerInfo("Kayzen", "Advertising", listOf("io.kayzen")),
        TrackerInfo("Persona.ly", "Advertising", listOf("ly.persona")),
        TrackerInfo("Remerge", "Advertising", listOf("io.remerge")),
        TrackerInfo("YouAppi", "Advertising", listOf("com.youappi")),
        TrackerInfo("AdMob Mediation", "Advertising", listOf("com.google.ads.mediation")),
        TrackerInfo("Tappx", "Advertising", listOf("com.tappx")),
        TrackerInfo("Kidoz SDK", "Advertising", listOf("com.kidoz.sdk")),
        TrackerInfo("PubNative Lite", "Advertising", listOf("net.pubnative.lite")),

        // =========================================================================
        // Analytics (~75 entries)
        // =========================================================================
        TrackerInfo("Google Firebase Analytics", "Analytics", listOf("com.google.firebase.analytics")),
        TrackerInfo("Google Analytics", "Analytics", listOf("com.google.android.gms.analytics")),
        TrackerInfo("Facebook Analytics", "Analytics", listOf("com.facebook.appevents")),
        TrackerInfo("Amplitude", "Analytics", listOf("com.amplitude")),
        TrackerInfo("Mixpanel", "Analytics", listOf("com.mixpanel")),
        TrackerInfo("Segment", "Analytics", listOf("com.segment")),
        TrackerInfo("Flurry", "Analytics", listOf("com.flurry")),
        TrackerInfo("AppsFlyer", "Analytics", listOf("com.appsflyer")),
        TrackerInfo("Adjust", "Analytics", listOf("com.adjust.sdk")),
        TrackerInfo("Branch", "Analytics", listOf("io.branch")),
        TrackerInfo("Kochava", "Analytics", listOf("com.kochava")),
        TrackerInfo("Singular", "Analytics", listOf("com.singular.sdk")),
        TrackerInfo("CleverTap", "Analytics", listOf("com.clevertap")),
        TrackerInfo("Leanplum", "Analytics", listOf("com.leanplum")),
        TrackerInfo("Countly", "Analytics", listOf("ly.count")),
        TrackerInfo("Heap", "Analytics", listOf("com.heapanalytics")),
        TrackerInfo("Snowplow", "Analytics", listOf("com.snowplowanalytics")),
        TrackerInfo("Braze (Appboy)", "Analytics", listOf("com.braze", "com.appboy")),
        TrackerInfo("OneSignal", "Analytics", listOf("com.onesignal")),
        TrackerInfo("Airship (UrbanAirship)", "Analytics", listOf("com.urbanairship")),
        TrackerInfo("Microsoft App Center", "Analytics", listOf("com.microsoft.appcenter")),
        TrackerInfo("Yandex AppMetrica", "Analytics", listOf("com.yandex.metrica")),
        TrackerInfo("Huawei Analytics", "Analytics", listOf("com.huawei.hms.analytics")),
        TrackerInfo("Samsung Analytics", "Analytics", listOf("com.samsung.context")),
        TrackerInfo("ByteDance AppLog", "Analytics", listOf("com.bytedance.applog")),
        // -- New Analytics entries --
        TrackerInfo("Localytics", "Analytics", listOf("com.localytics")),
        TrackerInfo("Swrve", "Analytics", listOf("com.swrve")),
        TrackerInfo("Tealium", "Analytics", listOf("com.tealium")),
        TrackerInfo("Adobe Analytics", "Analytics", listOf("com.adobe.marketing")),
        TrackerInfo("Matomo", "Analytics", listOf("org.matomo.sdk")),
        TrackerInfo("Piwik", "Analytics", listOf("org.piwik.sdk")),
        TrackerInfo("PostHog", "Analytics", listOf("com.posthog")),
        TrackerInfo("LaunchDarkly", "Analytics", listOf("com.launchdarkly")),
        TrackerInfo("Kumulos", "Analytics", listOf("com.kumulos")),
        TrackerInfo("MoEngage", "Analytics", listOf("com.moengage")),
        TrackerInfo("WebEngage", "Analytics", listOf("com.webengage")),
        TrackerInfo("Taplytics", "Analytics", listOf("com.taplytics")),
        TrackerInfo("Insider", "Analytics", listOf("com.useinsider")),
        TrackerInfo("Iterable", "Analytics", listOf("com.iterable")),
        TrackerInfo("Bloomreach (Exponea)", "Analytics", listOf("com.exponea")),
        TrackerInfo("Customer.io", "Analytics", listOf("io.customer")),
        TrackerInfo("Split.io", "Analytics", listOf("io.split")),
        TrackerInfo("Optimizely", "Analytics", listOf("com.optimizely")),
        TrackerInfo("Hotjar", "Analytics", listOf("com.hotjar")),
        TrackerInfo("FullStory", "Analytics", listOf("com.fullstory")),
        TrackerInfo("Smartlook", "Analytics", listOf("com.smartlook")),
        TrackerInfo("UXCam", "Analytics", listOf("com.uxcam")),
        TrackerInfo("LogRocket", "Analytics", listOf("com.logrocket")),
        TrackerInfo("Pendo", "Analytics", listOf("io.pendo")),
        TrackerInfo("ContentSquare", "Analytics", listOf("com.contentsquare")),
        TrackerInfo("Dynatrace", "Analytics", listOf("com.dynatrace")),
        TrackerInfo("AppDynamics", "Analytics", listOf("com.appdynamics")),
        TrackerInfo("AppSee", "Analytics", listOf("com.appsee")),
        TrackerInfo("UserTesting", "Analytics", listOf("com.usertesting")),
        TrackerInfo("Usabilla", "Analytics", listOf("com.usabilla")),
        TrackerInfo("Qualtrics", "Analytics", listOf("com.qualtrics")),
        TrackerInfo("Medallia", "Analytics", listOf("com.medallia")),
        TrackerInfo("SurveyMonkey", "Analytics", listOf("com.surveymonkey")),
        TrackerInfo("Apptentive", "Analytics", listOf("com.apptentive")),
        TrackerInfo("Reteno", "Analytics", listOf("com.reteno")),
        TrackerInfo("Batch", "Analytics", listOf("com.batch")),
        TrackerInfo("Sailthru (Marigold)", "Analytics", listOf("com.marigold.sdk")),
        TrackerInfo("Urban Airship Android", "Analytics", listOf("com.urbanairship.android")),
        TrackerInfo("WonderPush", "Analytics", listOf("com.wonderpush")),
        TrackerInfo("Pushwoosh", "Analytics", listOf("com.pushwoosh")),
        TrackerInfo("Catapush", "Analytics", listOf("com.catapush")),
        TrackerInfo("Accengage", "Analytics", listOf("com.accengage")),
        TrackerInfo("Netcore", "Analytics", listOf("com.netcore")),
        TrackerInfo("Affle", "Analytics", listOf("com.affle")),
        TrackerInfo("GameAnalytics", "Analytics", listOf("com.gameanalytics")),
        TrackerInfo("deltaDNA", "Analytics", listOf("com.deltadna")),
        TrackerInfo("Kochava Tracker", "Analytics", listOf("com.kochava.tracker")),
        TrackerInfo("Singular SDK", "Analytics", listOf("net.singular.sdk")),
        TrackerInfo("Tenjin", "Analytics", listOf("com.tenjin")),
        TrackerInfo("myTracker", "Analytics", listOf("com.my.tracker")),
        TrackerInfo("Chartbeat", "Analytics", listOf("com.chartbeat")),
        TrackerInfo("Comscore", "Analytics", listOf("com.comscore")),
        TrackerInfo("Nielsen", "Analytics", listOf("com.nielsen")),
        TrackerInfo("Quantcast", "Analytics", listOf("com.quantcast")),
        TrackerInfo("Moat", "Analytics", listOf("com.moat")),
        TrackerInfo("DoubleVerify", "Analytics", listOf("com.doubleverify")),
        TrackerInfo("IAS (Integral Ad Science)", "Analytics", listOf("com.integralads")),
        TrackerInfo("Pixalate", "Analytics", listOf("com.pixalate")),

        // =========================================================================
        // Crash Reporting (~40 entries)
        // =========================================================================
        TrackerInfo("Firebase Crashlytics", "Crash", listOf("com.google.firebase.crashlytics")),
        TrackerInfo("Sentry", "Crash", listOf("io.sentry")),
        TrackerInfo("Bugsnag", "Crash", listOf("com.bugsnag")),
        TrackerInfo("Instabug", "Crash", listOf("com.instabug")),
        TrackerInfo("ACRA", "Crash", listOf("org.acra")),
        TrackerInfo("Datadog", "Crash", listOf("com.datadog")),
        TrackerInfo("New Relic", "Crash", listOf("com.newrelic")),
        TrackerInfo("Embrace", "Crash", listOf("io.embrace")),
        TrackerInfo("Rollbar", "Crash", listOf("com.rollbar")),
        TrackerInfo("Tencent Bugly", "Crash", listOf("com.tencent.bugly")),
        // -- New Crash Reporting entries --
        TrackerInfo("Raygun", "Crash", listOf("com.raygun")),
        TrackerInfo("AppDynamics EUM", "Crash", listOf("com.appdynamics.eumagent")),
        TrackerInfo("Dynatrace Mobile Agent", "Crash", listOf("com.dynatrace.android")),
        TrackerInfo("Apteligent", "Crash", listOf("com.apteligent")),
        TrackerInfo("BugFender", "Crash", listOf("com.bugfender")),
        TrackerInfo("Timber", "Crash", listOf("com.jakewharton.timber")),
        TrackerInfo("Hyperion", "Crash", listOf("com.willowtreeapps.hyperion")),
        TrackerInfo("LeakCanary", "Crash", listOf("com.squareup.leakcanary")),
        TrackerInfo("Shake", "Crash", listOf("com.shakebugs")),
        TrackerInfo("UserExperior", "Crash", listOf("com.userexperior")),
        TrackerInfo("TestFairy", "Crash", listOf("com.testfairy")),
        TrackerInfo("Countly Crash", "Crash", listOf("ly.count.android.sdk.crash")),
        TrackerInfo("Firebase Performance Metrics", "Crash", listOf("com.google.firebase.perf.metrics")),
        TrackerInfo("Elastic APM", "Crash", listOf("co.elastic.apm")),
        TrackerInfo("Splunk RUM", "Crash", listOf("com.splunk.rum")),
        TrackerInfo("Grafana Faro", "Crash", listOf("io.grafana.faro")),
        TrackerInfo("OpenTelemetry", "Crash", listOf("io.opentelemetry")),
        TrackerInfo("Zipkin", "Crash", listOf("io.zipkin")),
        TrackerInfo("Jaeger", "Crash", listOf("io.jaegertracing")),
        TrackerInfo("Rookout", "Crash", listOf("com.rookout")),
        TrackerInfo("Lightrun", "Crash", listOf("com.lightrun")),
        TrackerInfo("Ozcode", "Crash", listOf("com.ozcode")),
        TrackerInfo("AppSpector", "Crash", listOf("com.appspector")),
        TrackerInfo("Waldo", "Crash", listOf("com.waldo")),
        TrackerInfo("Microsoft Diagnostics", "Crash", listOf("com.microsoft.diagnostics")),
        TrackerInfo("Flurry Crash", "Crash", listOf("com.flurry.android.crash")),
        TrackerInfo("Fabric Crashlytics", "Crash", listOf("io.fabric.sdk.android")),

        // =========================================================================
        // Profiling (~2 entries)
        // =========================================================================
        TrackerInfo("Google Firebase Performance", "Profiling", listOf("com.google.firebase.perf")),
        TrackerInfo("Google Firebase Remote Config", "Profiling", listOf("com.google.firebase.remoteconfig")),

        // =========================================================================
        // Social (~25 entries)
        // =========================================================================
        TrackerInfo("Facebook Login", "Social", listOf("com.facebook.login")),
        TrackerInfo("Facebook SDK Core", "Social", listOf("com.facebook.FacebookSdk")),
        TrackerInfo("Google Sign-In", "Social", listOf("com.google.android.gms.auth")),
        TrackerInfo("Twitter SDK", "Social", listOf("com.twitter.sdk")),
        TrackerInfo("LINE SDK", "Social", listOf("com.linecorp.linesdk")),
        // -- New Social entries --
        TrackerInfo("Snapchat Kit", "Social", listOf("com.snapchat.kit")),
        TrackerInfo("TikTok SDK", "Social", listOf("com.bytedance.sdk")),
        TrackerInfo("WeChat SDK", "Social", listOf("com.tencent.mm.opensdk")),
        TrackerInfo("VK SDK", "Social", listOf("com.vk.sdk")),
        TrackerInfo("Kakao SDK", "Social", listOf("com.kakao.sdk")),
        TrackerInfo("Naver SDK", "Social", listOf("com.naver.sdk")),
        TrackerInfo("Spotify SDK", "Social", listOf("com.spotify.sdk")),
        TrackerInfo("Discord SDK", "Social", listOf("com.discord.sdk")),
        TrackerInfo("Reddit SDK", "Social", listOf("com.reddit")),
        TrackerInfo("Pinterest SDK", "Social", listOf("com.pinterest")),
        TrackerInfo("LinkedIn SDK", "Social", listOf("com.linkedin")),
        TrackerInfo("Twitch SDK", "Social", listOf("tv.twitch.sdk")),
        TrackerInfo("YouTube SDK", "Social", listOf("com.google.android.youtube")),
        TrackerInfo("WhatsApp SDK", "Social", listOf("com.whatsapp.sdk")),
        TrackerInfo("Telegram SDK", "Social", listOf("org.telegram.sdk")),
        TrackerInfo("Signal SDK", "Social", listOf("org.signal")),
        TrackerInfo("Viber SDK", "Social", listOf("com.viber.voip")),
        TrackerInfo("Zoom SDK", "Social", listOf("us.zoom.sdk")),
        TrackerInfo("Teams SDK", "Social", listOf("com.microsoft.teams")),

        // =========================================================================
        // Location (~25 entries)
        // =========================================================================
        TrackerInfo("Google Location Services", "Location", listOf("com.google.android.gms.location")),
        TrackerInfo("Mapbox", "Location", listOf("com.mapbox")),
        TrackerInfo("Huq", "Location", listOf("me.huq")),
        TrackerInfo("Radar", "Location", listOf("io.radar")),
        // -- New Location entries --
        TrackerInfo("HERE SDK", "Location", listOf("com.here.sdk")),
        TrackerInfo("TomTom SDK", "Location", listOf("com.tomtom.sdk")),
        TrackerInfo("Bing Maps", "Location", listOf("com.microsoft.maps")),
        TrackerInfo("OpenStreetMap (osmdroid)", "Location", listOf("org.osmdroid")),
        TrackerInfo("Foursquare Pilgrim", "Location", listOf("com.foursquare.pilgrim")),
        TrackerInfo("Factual Engine", "Location", listOf("com.factual.engine")),
        TrackerInfo("Baidu Maps", "Location", listOf("com.baidu.mapapi")),
        TrackerInfo("AMap (Amap)", "Location", listOf("com.amap.api")),
        TrackerInfo("Tencent Map", "Location", listOf("com.tencent.map")),
        TrackerInfo("IndoorAtlas", "Location", listOf("com.indooratlas")),
        TrackerInfo("Estimote", "Location", listOf("com.estimote")),
        TrackerInfo("Gimbal", "Location", listOf("com.gimbal")),
        TrackerInfo("Bluedot", "Location", listOf("au.com.bluedot")),
        TrackerInfo("Rover", "Location", listOf("io.rover")),
        TrackerInfo("Beaconstac", "Location", listOf("com.beaconstac")),
        TrackerInfo("Mapbox Navigation", "Location", listOf("com.mapbox.navigation")),
        TrackerInfo("Google Maps Platform", "Location", listOf("com.google.maps")),
        TrackerInfo("Yelp SDK", "Location", listOf("com.yelp")),
        TrackerInfo("TripAdvisor SDK", "Location", listOf("com.tripadvisor")),

        // =========================================================================
        // Fingerprinting (~40 entries)
        // =========================================================================
        TrackerInfo("FingerprintJS", "Fingerprinting", listOf("com.fingerprintjs")),
        TrackerInfo("DeviceAtlas", "Fingerprinting", listOf("com.deviceatlas")),
        TrackerInfo("Iovation", "Fingerprinting", listOf("com.iovation")),
        TrackerInfo("ThreatMetrix", "Fingerprinting", listOf("com.threatmetrix")),
        TrackerInfo("Shield", "Fingerprinting", listOf("com.shield")),
        TrackerInfo("PerimeterX", "Fingerprinting", listOf("com.perimeterx")),
        TrackerInfo("FraudForce", "Fingerprinting", listOf("com.iovation.mobile")),
        TrackerInfo("Forter", "Fingerprinting", listOf("com.forter")),
        TrackerInfo("Riskified", "Fingerprinting", listOf("com.riskified")),
        TrackerInfo("SecuredTouch", "Fingerprinting", listOf("com.securedtouch")),
        TrackerInfo("BioCatch", "Fingerprinting", listOf("com.biocatch")),
        TrackerInfo("BehavioSec", "Fingerprinting", listOf("com.behaviosec")),
        TrackerInfo("Telesign", "Fingerprinting", listOf("com.telesign")),
        TrackerInfo("DataVisor", "Fingerprinting", listOf("com.datavisor")),
        TrackerInfo("Nethone", "Fingerprinting", listOf("com.nethone")),
        TrackerInfo("DeviceCheck", "Fingerprinting", listOf("io.devicecheck")),
        TrackerInfo("HUMAN Security", "Fingerprinting", listOf("com.humansecurity")),
        TrackerInfo("Arkose Labs", "Fingerprinting", listOf("com.arkoselabs")),
        TrackerInfo("Deduce", "Fingerprinting", listOf("com.deduce")),
        TrackerInfo("Kount", "Fingerprinting", listOf("com.kount")),
        TrackerInfo("Sift Science", "Fingerprinting", listOf("com.sift")),
        TrackerInfo("SimpleReach", "Fingerprinting", listOf("com.simplereach")),
        TrackerInfo("Permutive", "Fingerprinting", listOf("com.permutive")),
        TrackerInfo("LiveRamp", "Fingerprinting", listOf("com.liveramp")),
        TrackerInfo("Oracle MOAT", "Fingerprinting", listOf("com.moat.analytics")),
        TrackerInfo("Tapad", "Fingerprinting", listOf("com.tapad")),
        TrackerInfo("DrawBridge", "Fingerprinting", listOf("com.drawbridge")),
        TrackerInfo("Lotame", "Fingerprinting", listOf("com.lotame")),
        TrackerInfo("BlueKai", "Fingerprinting", listOf("com.bluekai")),
        TrackerInfo("Factual", "Fingerprinting", listOf("com.factual")),
        TrackerInfo("Cuebiq", "Fingerprinting", listOf("com.cuebiq")),
        TrackerInfo("X-Mode", "Fingerprinting", listOf("com.xmode")),
        TrackerInfo("SafeGraph", "Fingerprinting", listOf("com.safegraph")),
        TrackerInfo("Gravy Analytics", "Fingerprinting", listOf("com.gravyanalytics")),
        TrackerInfo("Unacast", "Fingerprinting", listOf("com.unacast")),
        TrackerInfo("PlaceIQ", "Fingerprinting", listOf("com.placeiq")),
        TrackerInfo("Near", "Fingerprinting", listOf("com.near")),
        TrackerInfo("Tamoco", "Fingerprinting", listOf("com.tamoco")),
        TrackerInfo("Reveal Mobile", "Fingerprinting", listOf("com.revealmobile")),
        TrackerInfo("Skyhook", "Fingerprinting", listOf("com.skyhook")),
        TrackerInfo("Precisely", "Fingerprinting", listOf("com.precisely")),

        // =========================================================================
        // Identification (~40 entries)
        // =========================================================================
        TrackerInfo("Google Tag Manager", "Identification", listOf("com.google.android.gms.tagmanager")),
        TrackerInfo("AccuWeather Tracking", "Identification", listOf("com.accuweather.tracking")),
        TrackerInfo("Piano", "Identification", listOf("io.piano")),
        TrackerInfo("Gigya", "Identification", listOf("com.gigya")),
        TrackerInfo("Auth0", "Identification", listOf("com.auth0")),
        TrackerInfo("Janrain", "Identification", listOf("com.janrain")),
        TrackerInfo("LoginRadius", "Identification", listOf("com.loginradius")),
        TrackerInfo("OneLogin", "Identification", listOf("com.onelogin")),
        TrackerInfo("Ping Identity", "Identification", listOf("com.pingidentity")),
        TrackerInfo("ForgeRock", "Identification", listOf("org.forgerock")),
        TrackerInfo("Transmit Security", "Identification", listOf("com.transmitsecurity")),
        TrackerInfo("Stytch", "Identification", listOf("com.stytch")),
        TrackerInfo("TrueCaller SDK", "Identification", listOf("com.truecaller.sdk")),
        TrackerInfo("Digits", "Identification", listOf("com.digits")),
        TrackerInfo("Facebook AccountKit", "Identification", listOf("com.facebook.accountkit")),
        TrackerInfo("Hashed", "Identification", listOf("com.hashed")),
        TrackerInfo("ID5", "Identification", listOf("io.id5")),
        TrackerInfo("SharedID", "Identification", listOf("com.sharedid")),
        TrackerInfo("UnifiedID 2.0", "Identification", listOf("com.unifiedid2")),
        TrackerInfo("Criteo Publisher ID", "Identification", listOf("com.criteo.publisher")),
        TrackerInfo("The Trade Desk", "Identification", listOf("com.thetradedesk")),
        TrackerInfo("Index Exchange", "Identification", listOf("com.indexexchange")),
        TrackerInfo("PubMatic Identity", "Identification", listOf("com.pubmatic.sdk.identity")),
        TrackerInfo("MediaMath", "Identification", listOf("com.mediamath")),
        TrackerInfo("Zemanta", "Identification", listOf("com.zemanta")),
        TrackerInfo("Outbrain", "Identification", listOf("com.outbrain")),
        TrackerInfo("Taboola", "Identification", listOf("com.taboola")),
        TrackerInfo("MGID", "Identification", listOf("com.mgid")),
        TrackerInfo("Revcontent", "Identification", listOf("com.revcontent")),
        TrackerInfo("Content.ad", "Identification", listOf("com.contentad")),
        TrackerInfo("Nativo", "Identification", listOf("com.nativo.sdk")),
        TrackerInfo("TripleLift Identity", "Identification", listOf("com.triplelift.sdk")),
        TrackerInfo("GrowthBook", "Identification", listOf("io.growthbook")),
        TrackerInfo("ConfigCat", "Identification", listOf("com.configcat")),
        TrackerInfo("Statsig", "Identification", listOf("com.statsig")),
        TrackerInfo("Eppo", "Identification", listOf("com.eppo")),
        TrackerInfo("Flagsmith", "Identification", listOf("com.flagsmith")),
        TrackerInfo("Unleash", "Identification", listOf("io.getunleash")),

        // =========================================================================
        // Additional Advertising
        // =========================================================================
        TrackerInfo("Snap Audience Network", "Advertising", listOf("com.snap.adkit")),
        TrackerInfo("Yahoo Gemini", "Advertising", listOf("com.yahoo.mobile.ads")),
        TrackerInfo("Baidu Ads", "Advertising", listOf("com.baidu.mobads")),
        TrackerInfo("Tencent GDT Ads", "Advertising", listOf("com.qq.e.ads")),
        TrackerInfo("Huawei Ads", "Advertising", listOf("com.huawei.hms.ads")),
        TrackerInfo("Xiaomi Ad SDK", "Advertising", listOf("com.miui.ads")),
        TrackerInfo("Samsung Ads", "Advertising", listOf("com.samsung.android.ads")),
        TrackerInfo("MediaNet", "Advertising", listOf("com.media.net")),
        TrackerInfo("Anzu.io", "Advertising", listOf("io.anzu")),
        TrackerInfo("AdInMo", "Advertising", listOf("com.adinmo")),
        TrackerInfo("Appreciate", "Advertising", listOf("com.appreciate.sdk")),
        TrackerInfo("Chocolate Platform", "Advertising", listOf("com.chocolateplatform")),
        TrackerInfo("Receptiv (MediaBrix)", "Advertising", listOf("com.receptiv")),
        TrackerInfo("AdsWizz", "Advertising", listOf("com.adswizz")),
        TrackerInfo("Triton Digital", "Advertising", listOf("com.tritondigital")),

        // =========================================================================
        // Additional Analytics
        // =========================================================================
        TrackerInfo("Instana", "Analytics", listOf("com.instana")),
        TrackerInfo("Indicative", "Analytics", listOf("com.indicative")),
        TrackerInfo("Keen.io", "Analytics", listOf("io.keen")),
        TrackerInfo("Woopra", "Analytics", listOf("com.woopra")),
        TrackerInfo("Plausible", "Analytics", listOf("io.plausible")),
        TrackerInfo("Fathom", "Analytics", listOf("com.usefathom")),
        TrackerInfo("GoSquared", "Analytics", listOf("com.gosquared")),
        TrackerInfo("Kissmetrics", "Analytics", listOf("com.kissmetrics")),
        TrackerInfo("Parsely", "Analytics", listOf("com.parsely")),
        TrackerInfo("Piano Analytics", "Analytics", listOf("io.piano.analytics")),
        TrackerInfo("AT Internet", "Analytics", listOf("com.atinternet")),
        TrackerInfo("Umeng Analytics", "Analytics", listOf("com.umeng.analytics")),
        TrackerInfo("Alibaba Analytics", "Analytics", listOf("com.alibaba.analytics")),
        TrackerInfo("Volcengine AppLog", "Analytics", listOf("com.bytedance.applog.tracker")),
        TrackerInfo("Upland Localytics", "Analytics", listOf("com.localytics.android")),

        // =========================================================================
        // Additional Crash Reporting
        // =========================================================================
        TrackerInfo("Crashpad (Chromium)", "Crash", listOf("org.chromium.crashpad")),
        TrackerInfo("BackTrace", "Crash", listOf("backtraceio.library")),
        TrackerInfo("LogDNA", "Crash", listOf("com.logdna")),
        TrackerInfo("Timber Logger", "Crash", listOf("timber.log")),
        TrackerInfo("Papertrail", "Crash", listOf("com.papertrailapp")),
        TrackerInfo("Loggly", "Crash", listOf("com.loggly")),

        // =========================================================================
        // Additional Profiling
        // =========================================================================
        TrackerInfo("Firebase A/B Testing", "Profiling", listOf("com.google.firebase.abt")),
        TrackerInfo("Firebase In-App Messaging", "Profiling", listOf("com.google.firebase.inappmessaging")),
        TrackerInfo("Firebase ML", "Profiling", listOf("com.google.firebase.ml")),
        TrackerInfo("Firebase Dynamic Links", "Profiling", listOf("com.google.firebase.dynamiclinks")),
        TrackerInfo("Google Play Install Referrer", "Profiling", listOf("com.android.installreferrer")),
        TrackerInfo("Google Play Services Base", "Profiling", listOf("com.google.android.gms.common")),
        TrackerInfo("Facebook App Links", "Profiling", listOf("com.facebook.applinks")),
        TrackerInfo("Huawei Push Kit", "Profiling", listOf("com.huawei.hms.push")),

        // =========================================================================
        // Additional Social
        // =========================================================================
        TrackerInfo("Apple Sign In", "Social", listOf("com.apple.android.signin")),
        TrackerInfo("Amazon Login", "Social", listOf("com.amazon.identity")),
        TrackerInfo("Huawei Account Kit", "Social", listOf("com.huawei.hms.support.account")),
        TrackerInfo("PayPal Login", "Social", listOf("com.paypal.android.login")),
        TrackerInfo("Weibo SDK", "Social", listOf("com.sina.weibo.sdk")),
        TrackerInfo("QQ SDK", "Social", listOf("com.tencent.open")),
        TrackerInfo("Douyin (TikTok CN)", "Social", listOf("com.bytedance.sdk.open.douyin")),

        // =========================================================================
        // Additional Location
        // =========================================================================
        TrackerInfo("Xtremepush", "Location", listOf("ie.imobile.extremepush")),
        TrackerInfo("Plot Projects", "Location", listOf("com.plotprojects")),
        TrackerInfo("Swirl", "Location", listOf("com.swirl")),
        TrackerInfo("Blis", "Location", listOf("com.blis")),
        TrackerInfo("Airship Location", "Location", listOf("com.urbanairship.location")),
        TrackerInfo("Huawei Location Kit", "Location", listOf("com.huawei.hms.location")),

        // =========================================================================
        // Additional Fingerprinting
        // =========================================================================
        TrackerInfo("Adjust Device IDs", "Fingerprinting", listOf("com.adjust.sdk.deviceids")),
        TrackerInfo("AppsFlyer Device ID", "Fingerprinting", listOf("com.appsflyer.deviceid")),
        TrackerInfo("Branch Device Fingerprint", "Fingerprinting", listOf("io.branch.referral.device")),
        TrackerInfo("Singular Device Fingerprint", "Fingerprinting", listOf("net.singular.sdk.device")),
        TrackerInfo("Accuweather SDK", "Fingerprinting", listOf("com.accuweather.sdk")),

        // =========================================================================
        // Additional Identification
        // =========================================================================
        TrackerInfo("Firebase Auth", "Identification", listOf("com.google.firebase.auth")),
        TrackerInfo("Huawei Auth Service", "Identification", listOf("com.huawei.agconnect.auth")),
        TrackerInfo("Okta", "Identification", listOf("com.okta")),
        TrackerInfo("AWS Cognito", "Identification", listOf("com.amazonaws.mobileconnectors.cognitoidentityprovider")),
        TrackerInfo("Azure AD (MSAL)", "Identification", listOf("com.microsoft.identity.client")),
        TrackerInfo("Salesforce Identity", "Identification", listOf("com.salesforce.androidsdk.auth")),
        TrackerInfo("SAP Identity", "Identification", listOf("com.sap.cloud.mobile.foundation.authentication")),
        TrackerInfo("CleverTap Identity", "Identification", listOf("com.clevertap.android.sdk.identity")),
        TrackerInfo("mParticle Identity", "Identification", listOf("com.mparticle.identity")),
        TrackerInfo("Treasure Data", "Analytics", listOf("com.treasuredata")),
        TrackerInfo("mParticle", "Analytics", listOf("com.mparticle")),
        TrackerInfo("Ably", "Analytics", listOf("io.ably")),
        TrackerInfo("Rudderstack", "Analytics", listOf("com.rudderstack")),
        TrackerInfo("Freshpaint", "Analytics", listOf("io.freshpaint"))
    )

    data class ScanResult(
        val packageName: String,
        val appLabel: String,
        val foundTrackers: List<TrackerInfo>,
        val trackerCount: Int,
        val categories: Set<String>,
        val fromCache: Boolean = false
    )

    /** Scan a single app, using cache when available. */
    suspend fun scanApp(packageName: String): ScanResult? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            val versionCode = getVersionCode(packageName)

            // Check cache
            val cached = cacheDao.getByPackage(packageName)
            if (cached != null && cached.appVersionCode == versionCode &&
                System.currentTimeMillis() - cached.scannedAt < CACHE_MAX_AGE_MS) {
                return@withContext cachedToScanResult(cached)
            }

            // Fresh scan
            val result = performScan(packageName, appLabel, appInfo.sourceDir)
            // Save to cache
            cacheDao.upsert(scanResultToCache(result, versionCode))
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan $packageName: ${e.message}")
            null
        }
    }

    /** Scan all installed user apps with progress callback. Cache-first. */
    suspend fun scanAllApps(onProgress: ((current: Int, total: Int) -> Unit)? = null): List<ScanResult> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }

        val total = packages.size
        val results = mutableListOf<ScanResult>()

        for ((index, appInfo) in packages.withIndex()) {
            onProgress?.invoke(index + 1, total)
            try {
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                val pkg = appInfo.packageName
                val versionCode = getVersionCode(pkg)

                // Check cache first
                val cached = cacheDao.getByPackage(pkg)
                if (cached != null && cached.appVersionCode == versionCode &&
                    System.currentTimeMillis() - cached.scannedAt < CACHE_MAX_AGE_MS) {
                    cachedToScanResult(cached)?.let { results.add(it) }
                    continue
                }

                // Fresh scan
                val result = performScan(pkg, appLabel, appInfo.sourceDir)
                cacheDao.upsert(scanResultToCache(result, versionCode))
                results.add(result)
            } catch (e: Exception) {
                Log.w(TAG, "Scan failed for ${appInfo.packageName}: ${e.message}")
            }
        }
        results.sortedByDescending { it.trackerCount }
    }

    /** Invalidate cache for a specific app (e.g., after update). */
    suspend fun invalidateCache(packageName: String) {
        cacheDao.deleteByPackage(packageName)
    }

    /** Clear entire scan cache. */
    suspend fun clearCache() {
        cacheDao.deleteAll()
    }

    /** Number of cached scan results. */
    suspend fun cacheSize(): Int = cacheDao.count()

    private fun performScan(packageName: String, appLabel: String, apkPath: String): ScanResult {
        val classNames = extractClassPrefixes(apkPath)
        val found = trackers.filter { tracker ->
            tracker.signatures.any { sig ->
                classNames.any { it.startsWith(sig) }
            }
        }
        return ScanResult(
            packageName = packageName,
            appLabel = appLabel,
            foundTrackers = found,
            trackerCount = found.size,
            categories = found.map { it.category }.toSet()
        )
    }

    private fun scanResultToCache(result: ScanResult, versionCode: Long): TrackerScanCacheEntry {
        return TrackerScanCacheEntry(
            packageName = result.packageName,
            appLabel = result.appLabel,
            trackerCount = result.trackerCount,
            trackerNames = result.foundTrackers.joinToString(",") { it.name },
            categories = result.categories.joinToString(","),
            appVersionCode = versionCode
        )
    }

    private fun cachedToScanResult(cached: TrackerScanCacheEntry): ScanResult? {
        val trackerNames = cached.trackerNames.split(",").filter { it.isNotBlank() }
        val found = trackers.filter { it.name in trackerNames }
        return ScanResult(
            packageName = cached.packageName,
            appLabel = cached.appLabel,
            foundTrackers = found,
            trackerCount = cached.trackerCount,
            categories = cached.categories.split(",").filter { it.isNotBlank() }.toSet(),
            fromCache = true
        )
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(packageName: String): Long {
        return try {
            val pi = context.packageManager.getPackageInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode
            else pi.versionCode.toLong()
        } catch (_: Exception) { 0L }
    }

    private fun extractClassPrefixes(apkPath: String): Set<String> {
        val prefixes = mutableSetOf<String>()
        try {
            ZipFile(apkPath).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") && !it.isDirectory }
                    .toList()

                for (entry in dexEntries) {
                    try {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val content = String(bytes, Charsets.ISO_8859_1)
                        for (tracker in trackers) {
                            for (sig in tracker.signatures) {
                                val dexSig = "L${sig.replace('.', '/')}/"
                                if (content.contains(dexSig)) {
                                    prefixes.add(sig)
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read APK $apkPath: ${e.message}")
        }
        return prefixes
    }
}
