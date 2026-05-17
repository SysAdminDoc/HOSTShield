package com.hostshield.util

import javax.inject.Inject
import javax.inject.Singleton

// Suspicious TLD detector
// Flags queries to TLDs commonly associated with malware, phishing, or abuse.

@Singleton
class SuspiciousTldDetector @Inject constructor() {

    // TLDs with high abuse rates (source: Spamhaus, SURBL, DomainTools threat intel)
    private val suspiciousTlds = setOf(
        // Known high-abuse TLDs
        "tk", "ml", "ga", "cf", "gq",           // Freenom free TLDs (top malware sources)
        "buzz", "top", "xyz", "club", "icu",     // Cheap/bulk registration TLDs
        "loan", "work", "click", "link", "gdn",  // Phishing-heavy TLDs
        "racing", "review", "science", "party",   // Abuse-heavy newTLDs
        "download", "bid", "win", "stream",       // Historically abused
        "accountant", "cricket", "date", "faith", // Spamhaus worst offenders
        "men", "trade",
        // Dark web / anonymous
        "onion", "i2p", "bit",
        // Suspicious country codes often used for phishing
        "su",  // Soviet Union (orphaned, heavily abused)
        "pw",  // Palau (cheap, abused)
        "cc",  // Cocos Islands (abused)
    )

    // Flagged but not necessarily malicious — just uncommon/noteworthy
    private val uncommonTlds = setOf(
        "crypto", "nft", "web3", "dao", "defi",  // Crypto/Web3
        "adult", "porn", "xxx", "sex",            // Adult
    )

    data class TldCheck(
        val tld: String,
        val isSuspicious: Boolean,
        val isUncommon: Boolean,
        val category: String // "malware", "phishing", "anonymous", "adult", "crypto", "clean"
    )

    fun check(hostname: String): TldCheck {
        val tld = hostname.substringAfterLast('.').lowercase()
        return when {
            tld in setOf("onion", "i2p", "bit") -> TldCheck(tld, true, false, "anonymous")
            tld in setOf("adult", "porn", "xxx", "sex") -> TldCheck(tld, false, true, "adult")
            tld in setOf("crypto", "nft", "web3", "dao", "defi") -> TldCheck(tld, false, true, "crypto")
            tld in suspiciousTlds -> TldCheck(tld, true, false, "malware")
            tld in uncommonTlds -> TldCheck(tld, false, true, "uncommon")
            else -> TldCheck(tld, false, false, "clean")
        }
    }

    fun isSuspicious(hostname: String): Boolean {
        val tld = hostname.substringAfterLast('.').lowercase()
        return tld in suspiciousTlds
    }
}
