package com.hostshield.service

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v6.1.0 — Content Filtering Categories (Roadmap #40)
//
// Toggleable content categories with curated domain lists.
// Users enable/disable categories; blocked domains are resolved
// via suffix matching (parent domain traversal) identical to
// NetworkTrackerDb.
//
// Domain lists are hardcoded and curated from public sources:
// - StevenBlack/hosts, Energized Protection, OISD
// - Manual enumeration of top sites per category
// ══════════════════════════════════════════════════════════════

/**
 * Content category enum — each category has a user-facing name and description.
 * Stored as [ContentCategory.name] strings in DataStore StringSet preference.
 */
enum class ContentCategory(val displayName: String, val description: String) {
    ADULT("Adult Content", "Pornography and explicit material"),
    GAMBLING("Gambling", "Online casinos, betting, lottery"),
    SOCIAL_MEDIA("Social Media", "Facebook, Instagram, TikTok, Twitter"),
    GAMING("Gaming", "Steam, Epic, gaming platforms"),
    STREAMING("Streaming", "Netflix, YouTube, Twitch, Spotify"),
    DATING("Dating", "Tinder, Bumble, dating apps"),
    DRUGS("Drugs & Alcohol", "Drug-related and alcohol promotion"),
    WEAPONS("Weapons", "Firearms and weapons sales"),
    PIRACY("Piracy", "Torrent sites, pirated content"),
    CRYPTO("Cryptocurrency", "Crypto exchanges and mining"),
    NEWS("News", "News and media outlets"),
    SHOPPING("Shopping", "Amazon, eBay, online stores"),
    VPN_PROXY("VPN & Proxy", "VPN services, web proxies, anonymizers"),
    MALWARE("Malware & Phishing", "Known malicious and phishing domains"),
    SOCIAL("Social Networking", "Social networks and messaging platforms")
}

@Singleton
class ContentFilterManager @Inject constructor() {

    private companion object {
        const val TAG = "ContentFilterManager"
    }

    /**
     * Primary domain→category index for O(1) exact-match lookups.
     * Built once in [init]; thread-safe for concurrent reads.
     */
    private val domainIndex = ConcurrentHashMap<String, ContentCategory>(2048)

    /**
     * Per-category domain sets for UI display and export.
     */
    private val categoryDomains = ConcurrentHashMap<ContentCategory, Set<String>>()

    val totalDomainCount: Int get() = domainIndex.size

    init {
        loadAllCategories()
        Log.i(TAG, "Loaded $totalDomainCount domains across ${ContentCategory.entries.size} categories")
    }

    // ── Public API ───────────────────────────────────────────────

    /** Return all available content categories. */
    fun getCategories(): List<ContentCategory> = ContentCategory.entries.toList()

    /** Return the curated domain set for a given category. */
    fun getDomainsForCategory(category: ContentCategory): Set<String> =
        categoryDomains[category] ?: emptySet()

    /**
     * Check if [domain] is blocked by any of the [enabledCategories].
     * Uses suffix matching (parent domain traversal) so "sub.example.com"
     * matches an entry for "example.com".
     */
    fun isBlocked(domain: String, enabledCategories: Set<ContentCategory>): Boolean {
        if (enabledCategories.isEmpty()) return false
        val lower = domain.lowercase()
        // Exact match
        domainIndex[lower]?.let { cat ->
            return cat in enabledCategories
        }
        // Parent domain traversal
        val parts = lower.split(".")
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            domainIndex[parent]?.let { cat ->
                return cat in enabledCategories
            }
        }
        return false
    }

    /**
     * Look up which category a domain belongs to (if any), using suffix matching.
     * Returns null if the domain is not in any category list.
     */
    fun lookupCategory(domain: String): ContentCategory? {
        val lower = domain.lowercase()
        domainIndex[lower]?.let { return it }
        val parts = lower.split(".")
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            domainIndex[parent]?.let { return it }
        }
        return null
    }

    /**
     * Convert a set of stored preference strings (enum names) to [ContentCategory] set.
     * Unknown/invalid strings are silently ignored.
     */
    fun getEnabledCategories(prefs: Set<String>): Set<ContentCategory> =
        prefs.mapNotNull { name ->
            try { ContentCategory.valueOf(name) }
            catch (_: IllegalArgumentException) { null }
        }.toSet()

    // ── Domain Loading ───────────────────────────────────────────

    private fun register(category: ContentCategory, domains: List<String>) {
        val domainSet = domains.map { it.lowercase() }.toSet()
        categoryDomains[category] = domainSet
        for (d in domainSet) {
            domainIndex[d] = category
        }
    }

    private fun loadAllCategories() {
        loadAdult()
        loadGambling()
        loadSocialMedia()
        loadGaming()
        loadStreaming()
        loadDating()
        loadDrugs()
        loadWeapons()
        loadPiracy()
        loadCrypto()
        loadNews()
        loadShopping()
        loadVpnProxy()
        loadMalware()
        loadSocial()
    }

    // ── ADULT ────────────────────────────────────────────────────

    private fun loadAdult() = register(ContentCategory.ADULT, listOf(
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "xhamster.com",
        "redtube.com",
        "youporn.com",
        "tube8.com",
        "spankbang.com",
        "eporner.com",
        "tnaflix.com",
        "drtuber.com",
        "hclips.com",
        "txxx.com",
        "beeg.com",
        "pornone.com",
        "thumbzilla.com",
        "porntrex.com",
        "fuq.com",
        "4tube.com",
        "pornpics.com",
        "imagefap.com",
        "brazzers.com",
        "realitykings.com",
        "bangbros.com",
        "naughtyamerica.com",
        "onlyfans.com",
        "fansly.com",
        "stripchat.com",
        "chaturbate.com",
        "livejasmin.com",
        "bongacams.com",
        "cam4.com",
        "myfreecams.com",
        "camsoda.com",
        "xxxvideos.com",
        "porn.com",
        "sex.com",
        "youjizz.com",
        "motherless.com",
        "alohatube.com"
    ))

    // ── GAMBLING ─────────────────────────────────────────────────

    private fun loadGambling() = register(ContentCategory.GAMBLING, listOf(
        "bet365.com",
        "draftkings.com",
        "fanduel.com",
        "betmgm.com",
        "caesars.com",
        "williamhill.com",
        "paddypower.com",
        "betfair.com",
        "ladbrokes.com",
        "coral.co.uk",
        "bwin.com",
        "888.com",
        "888poker.com",
        "888casino.com",
        "pokerstars.com",
        "partypoker.com",
        "betway.com",
        "unibet.com",
        "bovada.lv",
        "betonline.ag",
        "stake.com",
        "pointsbet.com",
        "barstoolsportsbook.com",
        "wynnbet.com",
        "betrivers.com",
        "twinspires.com",
        "hardrock.bet",
        "superbook.com",
        "sportsbetting.ag",
        "mybookie.ag",
        "pinnacle.com",
        "betfred.com",
        "skybet.com",
        "betsson.com",
        "casumo.com",
        "leovegas.com",
        "mrgreen.com",
        "jackpotcity.com",
        "spincasino.com",
        "royalpanda.com"
    ))

    // ── SOCIAL MEDIA ─────────────────────────────────────────────

    private fun loadSocialMedia() = register(ContentCategory.SOCIAL_MEDIA, listOf(
        "facebook.com",
        "fbcdn.net",
        "facebook.net",
        "fb.com",
        "fb.gg",
        "instagram.com",
        "cdninstagram.com",
        "twitter.com",
        "x.com",
        "twimg.com",
        "t.co",
        "tiktok.com",
        "tiktokcdn.com",
        "tiktokv.com",
        "musical.ly",
        "snapchat.com",
        "snap.com",
        "sc-static.net",
        "reddit.com",
        "redd.it",
        "redditstatic.com",
        "redditmedia.com",
        "pinterest.com",
        "pinimg.com",
        "linkedin.com",
        "licdn.com",
        "tumblr.com",
        "mastodon.social",
        "threads.net",
        "bsky.app",
        "bsky.social",
        "discord.com",
        "discord.gg",
        "discordapp.com",
        "quora.com",
        "vk.com",
        "weibo.com",
        "truth.social",
        "gettr.com",
        "parler.com"
    ))

    // ── GAMING ───────────────────────────────────────────────────

    private fun loadGaming() = register(ContentCategory.GAMING, listOf(
        "steampowered.com",
        "steamcommunity.com",
        "steamstatic.com",
        "store.steampowered.com",
        "epicgames.com",
        "unrealengine.com",
        "ea.com",
        "origin.com",
        "ubisoft.com",
        "uplay.com",
        "gog.com",
        "gog-statics.com",
        "battle.net",
        "blizzard.com",
        "riotgames.com",
        "leagueoflegends.com",
        "valorantesports.com",
        "roblox.com",
        "rbxcdn.com",
        "minecraft.net",
        "mojang.com",
        "itch.io",
        "gamejolt.com",
        "kongregate.com",
        "newgrounds.com",
        "gamespot.com",
        "ign.com",
        "kotaku.com",
        "polygon.com",
        "xbox.com",
        "playstation.com",
        "nintendo.com",
        "curseforge.com",
        "nexusmods.com",
        "moddb.com",
        "pcgamer.com",
        "humblebundle.com",
        "greenmangaming.com",
        "fanatical.com"
    ))

    // ── STREAMING ────────────────────────────────────────────────

    private fun loadStreaming() = register(ContentCategory.STREAMING, listOf(
        "netflix.com",
        "nflxvideo.net",
        "nflximg.net",
        "nflxso.net",
        "nflxext.com",
        "youtube.com",
        "youtu.be",
        "ytimg.com",
        "googlevideo.com",
        "youtube-nocookie.com",
        "twitch.tv",
        "twitchcdn.net",
        "jtvnw.net",
        "twitchsvc.net",
        "spotify.com",
        "scdn.co",
        "spotifycdn.com",
        "disneyplus.com",
        "disney-plus.net",
        "bamgrid.com",
        "hbomax.com",
        "max.com",
        "hulu.com",
        "hulustream.com",
        "primevideo.com",
        "aiv-cdn.net",
        "amazonvideo.com",
        "peacocktv.com",
        "paramountplus.com",
        "cbsi.com",
        "crunchyroll.com",
        "funimation.com",
        "deezer.com",
        "tidal.com",
        "soundcloud.com",
        "pandora.com",
        "apple.com",
        "applemusic.com",
        "vimeo.com",
        "dailymotion.com",
        "pluto.tv",
        "tubitv.com",
        "roku.com",
        "plex.tv"
    ))

    // ── DATING ───────────────────────────────────────────────────

    private fun loadDating() = register(ContentCategory.DATING, listOf(
        "tinder.com",
        "gotinder.com",
        "bumble.com",
        "hinge.co",
        "match.com",
        "okcupid.com",
        "pof.com",
        "zoosk.com",
        "eharmony.com",
        "badoo.com",
        "happn.com",
        "coffeemeetsbagel.com",
        "hily.com",
        "ship.co",
        "theinnercircle.co",
        "elitesingles.com",
        "silversingles.com",
        "jdate.com",
        "christianmingle.com",
        "grindr.com",
        "scruff.com",
        "jackd.com",
        "her.app",
        "feeld.co",
        "raya.app",
        "loveflutter.com",
        "muzmatch.com",
        "muzz.com",
        "tantan.com",
        "tagged.com"
    ))

    // ── DRUGS & ALCOHOL ──────────────────────────────────────────

    private fun loadDrugs() = register(ContentCategory.DRUGS, listOf(
        "leafly.com",
        "weedmaps.com",
        "dutchie.com",
        "iheartjane.com",
        "eaze.com",
        "budtender.com",
        "hightimes.com",
        "420magazine.com",
        "cannabis.net",
        "ganjapreneur.com",
        "thcdesign.com",
        "drizly.com",
        "minibar.com",
        "saucey.com",
        "totalwine.com",
        "wine.com",
        "vivino.com",
        "untappd.com",
        "beeradvocate.com",
        "ratebeer.com",
        "craftbeer.com",
        "liquor.com",
        "thewhiskyexchange.com",
        "masterofmalt.com",
        "caskers.com",
        "flaviar.com",
        "reservebar.com",
        "absinthes.com",
        "dankstop.com",
        "grasscity.com",
        "shroomery.org",
        "erowid.org",
        "psychonautwiki.org",
        "rollitup.org",
        "420science.com"
    ))

    // ── WEAPONS ──────────────────────────────────────────────────

    private fun loadWeapons() = register(ContentCategory.WEAPONS, listOf(
        "gunbroker.com",
        "budsgunshop.com",
        "palmettostatearmory.com",
        "brownells.com",
        "midwayusa.com",
        "cheaperthandirt.com",
        "sportsmansguide.com",
        "cabelas.com",
        "basspro.com",
        "smith-wesson.com",
        "ruger.com",
        "glock.com",
        "sigsauer.com",
        "springfield-armory.com",
        "berettausa.com",
        "colt.com",
        "remington.com",
        "winchester.com",
        "hornady.com",
        "federalpremium.com",
        "luckygunner.com",
        "ammoseek.com",
        "sgammo.com",
        "targetsportsusa.com",
        "natchezss.com",
        "primaryarms.com",
        "opticsplanet.com",
        "eurooptic.com",
        "kygunco.com",
        "galleryofguns.com",
        "armslist.com",
        "gunsamerica.com",
        "thefirearmblog.com",
        "thetruthaboutguns.com",
        "pewpewtactical.com"
    ))

    // ── PIRACY ───────────────────────────────────────────────────

    private fun loadPiracy() = register(ContentCategory.PIRACY, listOf(
        "thepiratebay.org",
        "1337x.to",
        "rarbg.to",
        "nyaa.si",
        "yts.mx",
        "limetorrents.cc",
        "torrentz2.eu",
        "torrentgalaxy.to",
        "eztv.re",
        "fitgirl-repacks.site",
        "skidrowreloaded.com",
        "oceanofgames.com",
        "igg-games.com",
        "steamunlocked.net",
        "fmovies.to",
        "soap2day.to",
        "putlocker.vip",
        "123movies.la",
        "gomovies.sx",
        "solarmovie.pe",
        "primewire.li",
        "flixtor.to",
        "popcorntime.app",
        "stremio.com",
        "real-debrid.com",
        "rapidgator.net",
        "uploaded.net",
        "nitroflare.com",
        "turbobit.net",
        "katcr.co",
        "zooqle.com",
        "glodls.to",
        "mobilism.org",
        "libgen.is",
        "z-lib.org",
        "sci-hub.se",
        "annas-archive.org",
        "b-ok.org",
        "audiobookbay.is",
        "myfreemp3.to"
    ))

    // ── CRYPTOCURRENCY ───────────────────────────────────────────

    private fun loadCrypto() = register(ContentCategory.CRYPTO, listOf(
        "coinbase.com",
        "binance.com",
        "kraken.com",
        "crypto.com",
        "gemini.com",
        "bitstamp.net",
        "bitfinex.com",
        "kucoin.com",
        "gate.io",
        "bybit.com",
        "okx.com",
        "huobi.com",
        "htx.com",
        "bitget.com",
        "mexc.com",
        "coinmarketcap.com",
        "coingecko.com",
        "tradingview.com",
        "blockchain.com",
        "blockchain.info",
        "etherscan.io",
        "bscscan.com",
        "polygonscan.com",
        "solscan.io",
        "metamask.io",
        "phantom.app",
        "trustwallet.com",
        "ledger.com",
        "trezor.io",
        "uniswap.org",
        "opensea.io",
        "rarible.com",
        "looksrare.org",
        "aave.com",
        "curve.fi",
        "lido.fi",
        "pancakeswap.finance",
        "sushiswap.org",
        "dydx.exchange",
        "mining-dutch.nl",
        "nicehash.com",
        "minergate.com",
        "2miners.com",
        "ethermine.org",
        "f2pool.com"
    ))

    // ── NEWS ─────────────────────────────────────────────────────

    private fun loadNews() = register(ContentCategory.NEWS, listOf(
        "cnn.com",
        "foxnews.com",
        "bbc.com",
        "bbc.co.uk",
        "nytimes.com",
        "washingtonpost.com",
        "theguardian.com",
        "reuters.com",
        "apnews.com",
        "nbcnews.com",
        "cbsnews.com",
        "abcnews.go.com",
        "msnbc.com",
        "cnbc.com",
        "bloomberg.com",
        "wsj.com",
        "usatoday.com",
        "huffpost.com",
        "politico.com",
        "thehill.com",
        "axios.com",
        "vice.com",
        "vox.com",
        "buzzfeednews.com",
        "dailymail.co.uk",
        "nypost.com",
        "latimes.com",
        "chicagotribune.com",
        "sky.com",
        "aljazeera.com",
        "dw.com",
        "france24.com",
        "rt.com",
        "news.yahoo.com",
        "news.google.com",
        "msn.com",
        "newsweek.com",
        "time.com",
        "theatlantic.com",
        "npr.org"
    ))

    // ── SHOPPING ─────────────────────────────────────────────────

    private fun loadShopping() = register(ContentCategory.SHOPPING, listOf(
        "amazon.com",
        "amazon.co.uk",
        "amazon.de",
        "amazon.co.jp",
        "amazon.in",
        "ebay.com",
        "ebay.co.uk",
        "walmart.com",
        "target.com",
        "bestbuy.com",
        "etsy.com",
        "aliexpress.com",
        "alibaba.com",
        "wish.com",
        "shein.com",
        "temu.com",
        "shopify.com",
        "wayfair.com",
        "overstock.com",
        "newegg.com",
        "costco.com",
        "homedepot.com",
        "lowes.com",
        "macys.com",
        "nordstrom.com",
        "zappos.com",
        "asos.com",
        "zara.com",
        "hm.com",
        "uniqlo.com",
        "nike.com",
        "adidas.com",
        "puma.com",
        "ikea.com",
        "sephora.com",
        "ulta.com",
        "chewy.com",
        "mercadolibre.com",
        "flipkart.com",
        "rakuten.co.jp"
    ))

    // ── VPN & PROXY ───────────────────────────────────────────────

    private fun loadVpnProxy() = register(ContentCategory.VPN_PROXY, listOf(
        "nordvpn.com",
        "expressvpn.com",
        "surfshark.com",
        "cyberghostvpn.com",
        "privateinternetaccess.com",
        "protonvpn.com",
        "mullvad.net",
        "windscribe.com",
        "tunnelbear.com",
        "hotspotshield.com",
        "hidemyass.com",
        "ipvanish.com",
        "purevpn.com",
        "strongvpn.com",
        "vypr.com",
        "hide.me",
        "kproxy.com",
        "hidester.com",
        "whoer.net",
        "proxysite.com",
        "croxyproxy.com",
        "anonymouse.org",
        "torproject.org",
        "psiphon.ca",
        "getlantern.org",
        "ultrasurf.us",
        "freegate.org",
        "zenvpn.net",
        "atlasvpn.com",
        "privatevpn.com"
    ))

    // ── MALWARE & PHISHING ────────────────────────────────────────

    private fun loadMalware() = register(ContentCategory.MALWARE, listOf(
        "malware-traffic-analysis.net",
        "urlhaus.abuse.ch",
        "phishtank.org",
        "openphish.com",
        "malwaredomainlist.com",
        "ransomwaretracker.abuse.ch",
        "cybercrime-tracker.net",
        "vxvault.net",
        "malc0de.com",
        "cleanmx.de"
    ))

    // ── SOCIAL (broader than SOCIAL_MEDIA, includes messaging) ───

    private fun loadSocial() = register(ContentCategory.SOCIAL, listOf(
        "facebook.com",
        "instagram.com",
        "twitter.com",
        "x.com",
        "tiktok.com",
        "snapchat.com",
        "reddit.com",
        "pinterest.com",
        "linkedin.com",
        "tumblr.com",
        "discord.com",
        "discordapp.com",
        "telegram.org",
        "web.telegram.org",
        "whatsapp.com",
        "web.whatsapp.com",
        "signal.org",
        "wechat.com",
        "line.me",
        "viber.com",
        "mastodon.social",
        "bsky.app",
        "threads.net",
        "quora.com",
        "vk.com",
        "weibo.com",
        "fb.com",
        "messenger.com"
    ))
}
