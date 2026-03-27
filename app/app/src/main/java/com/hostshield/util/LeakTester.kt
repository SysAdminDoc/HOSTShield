package com.hostshield.util

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.coroutines.resume

/**
 * v5.2: WebRTC and IPv6 leak detection utilities.
 *
 * WebRTC leak: WebRTC uses STUN servers to discover the device's public IP,
 * potentially bypassing VPN tunnels and revealing the real IP.
 *
 * IPv6 leak: Many VPNs only tunnel IPv4 traffic, leaving IPv6 unprotected.
 * If IPv6 connections work while VPN is active and the returned IP doesn't
 * match the VPN's IPv6 address, IPv6 is leaking.
 *
 * Reference: IPCheck.ing/MyIP (github.com/jason5ng32/MyIP, ~9,990 stars)
 */
object LeakTester {

    private const val TAG = "LeakTester"

    // ── WebRTC Leak Test ──────────────────────────────────────

    data class WebRtcLeakResult(
        val leaked: Boolean,
        val discoveredIps: List<String>,
        val vpnIp: String?,
        val error: String? = null
    )

    /**
     * JavaScript that creates an RTCPeerConnection to discover local IPs.
     * Parses ICE candidates for IP addresses and sends them to Android via
     * the JavascriptInterface bridge.
     */
    private val WEBRTC_LEAK_JS = """
        (function() {
            var ips = [];
            var pc = new RTCPeerConnection({
                iceServers: [{urls: 'stun:stun.l.google.com:19302'}]
            });
            pc.createDataChannel('');
            pc.createOffer().then(function(offer) {
                return pc.setLocalDescription(offer);
            });
            pc.onicecandidate = function(event) {
                if (!event || !event.candidate) {
                    // ICE gathering complete
                    LeakBridge.onComplete(JSON.stringify(ips));
                    pc.close();
                    return;
                }
                var candidate = event.candidate.candidate;
                var match = candidate.match(/([0-9]{1,3}\.){3}[0-9]{1,3}|[a-f0-9]{1,4}(:[a-f0-9]{1,4}){7}/gi);
                if (match) {
                    for (var i = 0; i < match.length; i++) {
                        if (ips.indexOf(match[i]) === -1) {
                            ips.push(match[i]);
                        }
                    }
                }
            };
            // Timeout after 5 seconds
            setTimeout(function() {
                LeakBridge.onComplete(JSON.stringify(ips));
                pc.close();
            }, 5000);
        })();
    """.trimIndent()

    /**
     * Run WebRTC leak test using a WebView.
     *
     * Must be called from the main thread (WebView requirement).
     * Creates a WebView, runs JavaScript to discover IPs via RTCPeerConnection,
     * then compares discovered IPs against the VPN's assigned IP.
     *
     * @param webView A WebView instance (caller must provide — can be hidden)
     * @param vpnIp The IP address assigned by the VPN tunnel (null if VPN not active)
     * @return WebRtcLeakResult with discovered IPs and leak status
     */
    suspend fun testWebRtcLeak(webView: WebView, vpnIp: String?): WebRtcLeakResult {
        return try {
            val result = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine { cont ->
                    val bridge = object {
                        @JavascriptInterface
                        fun onComplete(ipsJson: String) {
                            try {
                                // Parse JSON array of IPs
                                val ips = ipsJson
                                    .removeSurrounding("[", "]")
                                    .split(",")
                                    .map { it.trim().removeSurrounding("\"") }
                                    .filter { it.isNotBlank() && it != "0.0.0.0" }

                                val leaked = if (vpnIp != null) {
                                    ips.any { it != vpnIp && !LanDetector.isPrivateIp(it) }
                                } else {
                                    false // Can't determine leak without knowing VPN IP
                                }

                                if (cont.isActive) {
                                    cont.resume(WebRtcLeakResult(
                                        leaked = leaked,
                                        discoveredIps = ips,
                                        vpnIp = vpnIp
                                    ))
                                }
                            } catch (e: Exception) {
                                if (cont.isActive) {
                                    cont.resume(WebRtcLeakResult(
                                        leaked = false, discoveredIps = emptyList(),
                                        vpnIp = vpnIp, error = e.message
                                    ))
                                }
                            }
                        }
                    }

                    webView.settings.javaScriptEnabled = true
                    webView.addJavascriptInterface(bridge, "LeakBridge")
                    webView.webViewClient = WebViewClient()
                    webView.loadDataWithBaseURL(
                        "https://localhost",
                        "<html><body><script>$WEBRTC_LEAK_JS</script></body></html>",
                        "text/html", "utf-8", null
                    )
                }
            }

            result ?: WebRtcLeakResult(
                leaked = false, discoveredIps = emptyList(),
                vpnIp = vpnIp, error = "Timeout"
            )
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC leak test failed: ${e.message}")
            WebRtcLeakResult(
                leaked = false, discoveredIps = emptyList(),
                vpnIp = vpnIp, error = e.message
            )
        }
    }

    // ── IPv6 Leak Test ────────────────────────────────────────

    data class Ipv6LeakResult(
        val leaked: Boolean,
        val ipv6Reachable: Boolean,
        val discoveredIpv6: String?,
        val vpnIpv6: String?,
        val error: String? = null
    )

    /**
     * Test for IPv6 leaks by attempting an IPv6 connection.
     *
     * If IPv6 is reachable while VPN is active and the source IP doesn't
     * match the VPN's IPv6 address, IPv6 traffic is leaking around the tunnel.
     *
     * @param vpnIpv6 The IPv6 address assigned by the VPN (null if VPN doesn't assign IPv6)
     * @return Ipv6LeakResult with reachability and leak status
     */
    suspend fun testIpv6Leak(vpnIpv6: String?): Ipv6LeakResult = withContext(Dispatchers.IO) {
        try {
            // Try to connect to a well-known IPv6 endpoint
            val testHost = "ipv6.google.com"
            val addresses = InetAddress.getAllByName(testHost)
            val ipv6Addr = addresses.firstOrNull { it is Inet6Address }

            if (ipv6Addr == null) {
                return@withContext Ipv6LeakResult(
                    leaked = false, ipv6Reachable = false,
                    discoveredIpv6 = null, vpnIpv6 = vpnIpv6
                )
            }

            // Try to establish a TCP connection to test reachability
            val socket = Socket()
            try {
                socket.connect(java.net.InetSocketAddress(ipv6Addr, 80), 5000)
                val localIp = socket.localAddress?.hostAddress
                socket.close()

                val leaked = if (vpnIpv6 != null && localIp != null) {
                    localIp != vpnIpv6 && !LanDetector.isPrivateIp(localIp)
                } else if (vpnIpv6 == null && localIp != null) {
                    // VPN doesn't provide IPv6 but IPv6 is reachable — leak
                    !LanDetector.isPrivateIp(localIp)
                } else {
                    false
                }

                Ipv6LeakResult(
                    leaked = leaked, ipv6Reachable = true,
                    discoveredIpv6 = localIp, vpnIpv6 = vpnIpv6
                )
            } catch (_: Exception) {
                socket.close()
                // Connection failed — IPv6 not reachable (no leak)
                Ipv6LeakResult(
                    leaked = false, ipv6Reachable = false,
                    discoveredIpv6 = null, vpnIpv6 = vpnIpv6
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "IPv6 leak test failed: ${e.message}")
            Ipv6LeakResult(
                leaked = false, ipv6Reachable = false,
                discoveredIpv6 = null, vpnIpv6 = vpnIpv6, error = e.message
            )
        }
    }

    /**
     * Get the device's current IPv6 addresses from network interfaces.
     * Useful for displaying in the leak test results UI.
     */
    fun getDeviceIpv6Addresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { iface ->
                    iface.inetAddresses.toList()
                        .filter { it is Inet6Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                        .mapNotNull { it.hostAddress }
                } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
