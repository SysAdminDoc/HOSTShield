package com.hostshield.service

import android.util.Log
import com.hostshield.data.model.FirewallRule
import com.hostshield.domain.BlockDecision
import com.hostshield.util.PrivacyLog
import com.hostshield.util.TlsFingerprinter

/** Result of response inspection before the packet is wrapped back to the app. */
internal data class PostForwardResult(
    val blocked: Boolean,
    val blockResponse: ByteArray? = null,
)

/**
 * Packet-level DNS policy coordinator.
 *
 * The service owns the TUN and response I/O; this class owns the ordered
 * decision pipeline for UDP DNS, the pragmatic TCP-DNS handling, and TLS
 * fingerprint extraction. [Host] is deliberately narrow so the policy order
 * can be exercised without constructing an Android VpnService.
 */
internal class DnsQueryProcessor(
    private val appDnsRuleEngine: AppDnsRuleEngine,
    private val safeSearchEnforcer: SafeSearchEnforcer,
    private val contentFilterManager: ContentFilterManager,
    private val parentalControlManager: ParentalControlManager,
    private val threatIntelManager: ThreatIntelManager,
    private val tlsFingerprinter: TlsFingerprinter,
    private val dnsCache: DnsCache,
    private val host: Host,
    private val tag: String = "HostShield",
) {
    internal interface Host {
        val dnsOnlyMode: Boolean
        val blockedApps: Set<String>
        val contextRules: Map<String, FirewallRule>
        val safeSearchEnabled: Boolean
        val contentFilterCategories: Set<ContentCategory>
        val threatIntelEnabled: Boolean

        fun resolveApp(packet: ByteArray, headerOffset: Int, isV6: Boolean): Pair<String, String>
        fun findUidByDnsCorrelation(domain: String): Int
        fun resolvePkg(uid: Int): Pair<String, String>
        fun shouldBlockByContext(rule: FirewallRule, packageName: String): Boolean
        fun domainDecision(domain: String, queryType: Int? = null): BlockDecision

        fun log(
            domain: String,
            blocked: Boolean,
            app: Pair<String, String>,
            qtype: String,
            decision: BlockDecision? = null,
        )

        fun logRich(
            domain: String,
            blocked: Boolean,
            app: Pair<String, String>,
            qtype: String,
            trackerCategory: String = "",
            trackerOwner: String = "",
            decision: BlockDecision? = null,
        )

        suspend fun sendBlockResponse(
            dns: ByteArray,
            packet: ByteArray,
            headerOffset: Int,
            isV6: Boolean,
            qtype: String,
            reason: String,
        )

        fun wrapAndSend(packet: ByteArray, headerOffset: Int, isV6: Boolean, dns: ByteArray)
        fun launchWork(block: suspend () -> Unit)
        suspend fun forwardEncrypted(
            dns: ByteArray,
            domain: String,
            packet: ByteArray,
            headerOffset: Int,
            app: Pair<String, String>,
            isV6: Boolean,
            skipThreatIntelChecks: Boolean,
        )

        suspend fun postForwardChecks(
            response: ByteArray,
            dns: ByteArray,
            domain: String,
            app: Pair<String, String>,
            latencyMs: Int,
            upstreamServer: String,
            skipThreatIntelChecks: Boolean,
            isFromCache: Boolean,
        ): PostForwardResult

        suspend fun refreshDnsCacheOnly(
            dns: ByteArray,
            domain: String,
            app: Pair<String, String>,
            skipThreatIntelChecks: Boolean,
        )

        fun sendToTun(packet: ByteArray)
        fun incrementBlocked()
        fun incrementAllowed()
    }

    suspend fun processDnsPacket(packet: ByteArray, length: Int, isV6: Boolean) {
        val headerOffset = if (isV6) 40 else (packet[0].toInt() and 0x0F) * 4
        val dns = if (isV6) {
            DnsPacketParser.extractDnsPayloadV6(packet, length, headerOffset)
        } else {
            DnsPacketParser.extractDnsPayload(packet, length, headerOffset)
        } ?: return

        val domain = DnsPacketParser.parseDnsQueryDomain(dns) ?: return
        val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
        val qtype = DnsPacketBuilder.queryTypeLabel(qtypeNum)
        var app = host.resolveApp(packet, headerOffset, isV6)

        if (app.first.isEmpty()) {
            val heuristicUid = host.findUidByDnsCorrelation(domain)
            if (heuristicUid > 0) app = host.resolvePkg(heuristicUid)
        }

        if (!host.dnsOnlyMode && app.first.isNotEmpty() && app.first in host.blockedApps) {
            host.log(domain, true, app, qtype, decision(
                blocked = true,
                reason = "app_firewall",
                source = "Per-app DNS firewall",
                matchedValue = app.first,
                precedence = "per-app firewall runs before DNS policy",
            ))
            host.sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "app_firewall")
            return
        }

        if (!host.dnsOnlyMode && app.first.isNotEmpty()) {
            val contextRule = host.contextRules[app.first]
            if (contextRule != null && host.shouldBlockByContext(contextRule, app.first)) {
                host.log(domain, true, app, qtype, decision(
                    blocked = true,
                    reason = "context_firewall",
                    source = "Context-aware firewall",
                    matchedValue = app.first,
                    precedence = "context firewall runs before DNS policy",
                ))
                host.sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "context_firewall")
                return
            }
        }

        if (host.safeSearchEnabled && safeSearchEnforcer.isSafeSearchDomain(domain)) {
            val safeResponse = safeSearchEnforcer.buildSafeResponse(dns, domain)
            if (safeResponse != null) {
                PrivacyLog.d(tag, "SAFE-SEARCH $domain")
                host.log(domain, false, app, qtype, decision(
                    blocked = false,
                    reason = "safe_search",
                    source = "Safe Search enforcer",
                    matchedValue = domain,
                    precedence = "safe-search rewrite runs before blocklist lookup",
                ))
                host.wrapAndSend(packet, headerOffset, isV6, safeResponse)
                host.incrementAllowed()
                return
            }
        }

        if (app.first.isNotEmpty()) {
            when (appDnsRuleEngine.checkDomain(app.first, domain, qtypeNum)) {
                AppDnsRuleEngine.RuleAction.BLOCK -> {
                    PrivacyLog.d(tag, "APP-RULE blocked $domain for ${app.second}")
                    host.log(domain, true, app, qtype, decision(
                        blocked = true,
                        reason = "app_rule_block",
                        source = "Per-app DNS rule",
                        matchedValue = app.first,
                        precedence = "per-app block rule runs before shared blocklist",
                    ))
                    host.sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "app_rule")
                    return
                }

                AppDnsRuleEngine.RuleAction.ALLOW -> {
                    PrivacyLog.d(tag, "APP-RULE allowed $domain for ${app.second}")
                    host.log(domain, false, app, qtype, decision(
                        blocked = false,
                        reason = "app_rule_allow",
                        source = "Per-app DNS rule",
                        matchedValue = app.first,
                        precedence = "per-app allow rule skips shared blocklist and threat intel for this app",
                    ))
                    val packetCopy = packet.copyOf(length)
                    host.launchWork {
                        host.forwardEncrypted(
                            dns = dns,
                            domain = domain,
                            packet = packetCopy,
                            headerOffset = headerOffset,
                            app = app,
                            isV6 = isV6,
                            skipThreatIntelChecks = true,
                        )
                    }
                    host.incrementAllowed()
                    return
                }

                null -> Unit
            }
        }

        if (
            host.contentFilterCategories.isNotEmpty() &&
            contentFilterManager.isBlocked(domain, host.contentFilterCategories)
        ) {
            val category = contentFilterManager.lookupCategory(domain)?.displayName ?: "Unknown"
            PrivacyLog.d(tag, "CONTENT-FILTER blocked $domain ($qtype) category=$category")
            host.logRich(
                domain,
                true,
                app,
                qtype,
                trackerCategory = "ContentFilter:$category",
                decision = decision(
                    blocked = true,
                    reason = "content_filter",
                    source = category,
                    matchedValue = domain,
                    precedence = "content category policy runs before shared blocklist",
                ),
            )
            host.sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "content_filter")
            return
        }

        if (parentalControlManager.shouldBlock(domain)) {
            val category = contentFilterManager.lookupCategory(domain)?.displayName ?: "Unknown"
            PrivacyLog.d(
                tag,
                "PARENTAL blocked $domain ($qtype) category=$category profile=${parentalControlManager.currentProfile.name}",
            )
            host.logRich(
                domain,
                true,
                app,
                qtype,
                trackerCategory = "Parental:$category",
                decision = decision(
                    blocked = true,
                    reason = "parental_control",
                    source = parentalControlManager.currentProfile.name,
                    matchedValue = category,
                    precedence = "parental profile runs before shared blocklist",
                ),
            )
            host.sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "parental_control")
            return
        }

        val blockDecision = host.domainDecision(domain, qtypeNum)
        val skipThreatIntelChecks = blockDecision.skipsThreatIntelChecks()

        if (!blockDecision.blocked && host.threatIntelEnabled && !skipThreatIntelChecks) {
            val threat = threatIntelManager.isDomainMalicious(domain)
            if (threat != null) {
                PrivacyLog.i(tag, "THREAT-INTEL blocked domain: $domain (${threat.feedName})")
                host.log(domain, true, app, qtype, decision(
                    blocked = true,
                    reason = "threat_intel_domain",
                    source = threat.feedName,
                    matchedValue = domain,
                    precedence = "threat intel runs after blocklist miss",
                ))
                host.sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "threat_intel")
                return
            }
        }

        if (blockDecision.blocked) {
            host.log(domain, true, app, qtype, blockDecision)
            PrivacyLog.d(tag, "BLOCKED $domain ($qtype) [${app.second.ifEmpty { "system" }}]")
            host.sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "blocklist")
            return
        }

        val transactionId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
        val cacheResult = dnsCache.get(domain, qtypeNum, transactionId)
        if (cacheResult != null) {
            if (!cacheResult.isStale) {
                PrivacyLog.d(tag, "CACHE HIT $domain ($qtype)")
                val postForwardResult = host.postForwardChecks(
                    cacheResult.response,
                    dns,
                    domain,
                    app,
                    latencyMs = 0,
                    upstreamServer = "DNS cache",
                    skipThreatIntelChecks = skipThreatIntelChecks,
                    isFromCache = true,
                )
                if (postForwardResult.blocked) {
                    if (postForwardResult.blockResponse != null) {
                        host.wrapAndSend(packet, headerOffset, isV6, postForwardResult.blockResponse)
                    }
                    return
                }
                host.wrapAndSend(packet, headerOffset, isV6, cacheResult.response)
                host.incrementAllowed()
                if (cacheResult.needsPrefetch) {
                    host.launchWork {
                        try {
                            host.refreshDnsCacheOnly(dns, domain, app, skipThreatIntelChecks)
                        } catch (e: Exception) {
                            PrivacyLog.d(tag, "Prefetch failed for $domain: ${e.message}")
                        }
                    }
                }
                return
            }

            PrivacyLog.d(tag, "SERVE-STALE $domain ($qtype) — refreshing in background")
            val postForwardResult = host.postForwardChecks(
                cacheResult.response,
                dns,
                domain,
                app,
                latencyMs = 0,
                upstreamServer = "DNS stale cache",
                skipThreatIntelChecks = skipThreatIntelChecks,
                isFromCache = true,
            )
            if (postForwardResult.blocked) {
                if (postForwardResult.blockResponse != null) {
                    host.wrapAndSend(packet, headerOffset, isV6, postForwardResult.blockResponse)
                }
                return
            }
            host.wrapAndSend(packet, headerOffset, isV6, cacheResult.response)
            host.incrementAllowed()
            host.launchWork {
                try {
                    host.refreshDnsCacheOnly(dns, domain, app, skipThreatIntelChecks)
                } catch (e: Exception) {
                    PrivacyLog.d(tag, "Stale refresh failed for $domain: ${e.message}")
                }
            }
            return
        }

        PrivacyLog.d(tag, "ALLOWED $domain ($qtype)")
        val packetCopy = packet.copyOf(length)
        host.launchWork {
            host.forwardEncrypted(
                dns = dns,
                domain = domain,
                packet = packetCopy,
                headerOffset = headerOffset,
                app = app,
                isV6 = isV6,
                skipThreatIntelChecks = skipThreatIntelChecks,
            )
        }
        host.incrementAllowed()
    }

    suspend fun processTcpDns(packet: ByteArray, length: Int, isV6: Boolean) {
        val headerOffset = if (isV6) 40 else (packet[0].toInt() and 0x0F) * 4
        val tcpOffset = headerOffset
        if (length < tcpOffset + 20) return

        val dataOffset = ((packet[tcpOffset + 12].toInt() and 0xF0) shr 4) * 4
        val tcpFlags = packet[tcpOffset + 13].toInt() and 0xFF
        if ((tcpFlags and 0x04) != 0) return

        val payloadStart = tcpOffset + dataOffset
        val payloadLength = length - payloadStart
        val isSyn = (tcpFlags and 0x02) != 0
        var hostname: String? = null
        var qtypeNum: Int? = null
        if (payloadLength > 14) {
            val dnsLength = ((packet[payloadStart].toInt() and 0xFF) shl 8) or
                (packet[payloadStart + 1].toInt() and 0xFF)
            if (dnsLength in 12..4096 && payloadStart + 2 + dnsLength <= length) {
                val dns = packet.copyOfRange(payloadStart + 2, payloadStart + 2 + dnsLength)
                hostname = DnsPacketParser.parseDnsQueryDomain(dns)
                qtypeNum = DnsPacketBuilder.parseQueryType(dns)
            }
        }

        if (hostname == null && !isSyn) return

        val blocked = if (hostname != null) host.domainDecision(hostname, qtypeNum).blocked else true
        if (blocked) {
            val trimmed = packet.copyOf(length)
            val rst = if (isV6) TcpRstBuilder.buildTcpRstV6(trimmed)
            else TcpRstBuilder.buildTcpRst(trimmed, headerOffset)
            rst ?: return
            host.sendToTun(rst)
            host.incrementBlocked()
            if (hostname != null) {
                PrivacyLog.d(tag, "TCP-DNS BLOCKED (RST) $hostname")
                host.log(hostname, true, "" to "", "TCP")
            }
        } else if (hostname != null) {
            PrivacyLog.d(tag, "TCP-DNS allowed (drop→UDP fallback) $hostname")
        }
    }

    fun tryTlsFingerprintPacket(packet: ByteArray, length: Int, isV6: Boolean) {
        val minSize = if (isV6) 80 else 60
        if (length < minSize) return
        val headerOffset = if (isV6) 40 else (packet[0].toInt() and 0x0F) * 4
        val protocol = if (isV6) packet[6].toInt() and 0xFF else packet[9].toInt() and 0xFF
        if (protocol != 6) return
        val tcpOffset = headerOffset
        if (length < tcpOffset + 20) return
        val dataOffset = ((packet[tcpOffset + 12].toInt() and 0xF0) shr 4) * 4
        val payloadStart = tcpOffset + dataOffset
        val payloadLength = length - payloadStart
        if (payloadLength < 6) return
        if (!tlsFingerprinter.isClientHello(packet, payloadStart, payloadLength)) return
        val fingerprint = tlsFingerprinter.fingerprint(packet, payloadStart, payloadLength) ?: return
        val app = host.resolveApp(packet, headerOffset, isV6)
        tlsFingerprinter.record(app.first, app.second, fingerprint)
        PrivacyLog.d(
            tag,
            "TLS-FP ${app.second.ifEmpty { "unknown" }}: JA3=${fingerprint.ja3} " +
                "JA4=${fingerprint.ja4} SNI=${fingerprint.sni ?: "-"}",
        )
    }

    private fun decision(
        blocked: Boolean,
        reason: String,
        source: String = "",
        matchedValue: String = "",
        precedence: String = "",
    ): BlockDecision = BlockDecision(blocked, reason, source, matchedValue, precedence)
}
