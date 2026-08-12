package com.hostshield.service

/**
 * Signed remote IP sets used for DNS-trap and DoH-bypass routes.
 *
 * The fallback is deliberately kept in the APK: a network update must never
 * be able to remove the last known protection, and older signed manifests do
 * not carry IP sets yet.
 */
data class DnsTrapIpSets(
    val dnsTrapIpv4: Set<String>,
    val dnsTrapIpv6: Set<String>,
    val dohBypassIpv4: Set<String>,
    val dohBypassIpv6: Set<String>
) {
    val isComplete: Boolean
        get() = dnsTrapIpv4.isNotEmpty() &&
            dnsTrapIpv6.isNotEmpty() &&
            dohBypassIpv4.isNotEmpty() &&
            dohBypassIpv6.isNotEmpty()

    companion object {
        val FALLBACK = DnsTrapIpSets(
            dnsTrapIpv4 = setOf(
                "8.8.8.8", "8.8.4.4", // Google
                "1.1.1.1", "1.0.0.1", // Cloudflare
                "9.9.9.9", "149.112.112.112", // Quad9
                "208.67.222.222", "208.67.220.220", // OpenDNS
                "94.140.14.14", "94.140.15.15", // AdGuard
                "76.76.2.0", "76.76.10.0", // ControlD
                "185.228.168.9", "185.228.169.9", // CleanBrowsing
                "194.242.2.2", "194.242.2.3" // Mullvad
            ),
            dnsTrapIpv6 = setOf(
                "2001:4860:4860::8888", "2001:4860:4860::8844", // Google
                "2606:4700:4700::1111", "2606:4700:4700::1001", // Cloudflare
                "2620:fe::fe", "2620:fe::9", // Quad9
                "2620:119:35::35", "2620:119:53::53", // OpenDNS
                "2a10:50c0::ad1", "2a10:50c0::ad2", // AdGuard
                "2a0d:2a00:1::2", "2a0d:2a00:2::2", // CleanBrowsing
                "2a07:e340::2", "2a07:e340::3" // Mullvad
            ),
            dohBypassIpv4 = setOf(
                "104.16.248.249", "104.16.249.249", // Cloudflare
                "172.64.36.1", "172.64.36.2",
                "142.250.80.14", "142.251.1.100", // Google
                "8.8.8.8", "8.8.4.4",
                "9.9.9.11", "149.112.112.11", // Quad9
                "94.140.14.140", "94.140.14.141", // AdGuard
                "45.90.28.0", "45.90.30.0", // NextDNS
                "146.112.41.2", "146.112.41.3", // OpenDNS
                "185.228.168.168", "185.228.169.168", // CleanBrowsing
                "194.242.2.2", "194.242.2.3", // Mullvad
                "76.76.2.11", "76.76.10.11" // ControlD
            ),
            dohBypassIpv6 = setOf(
                "2606:4700:4700::1111", "2606:4700:4700::1001", // Cloudflare
                "2001:4860:4860::8888", "2001:4860:4860::8844", // Google
                "2620:fe::fe", "2620:fe::9", // Quad9
                "2a10:50c0::ad1", "2a10:50c0::ad2", // AdGuard
                "2620:119:35::35", "2620:119:53::53", // OpenDNS
                "2a0d:2a00:1::2", "2a0d:2a00:2::2", // CleanBrowsing
                "2a07:e340::2", "2a07:e340::3" // Mullvad
            )
        )
    }
}

internal object DnsTrapRoutePlanner {
    fun routeTargets(
        ipSets: DnsTrapIpSets,
        ipv6Available: Boolean
    ): List<VpnRouteCanonicalizer.Route> {
        val addresses = linkedSetOf<String>().apply {
            addAll(ipSets.dnsTrapIpv4)
            addAll(ipSets.dohBypassIpv4)
            if (ipv6Available) {
                addAll(ipSets.dnsTrapIpv6)
                addAll(ipSets.dohBypassIpv6)
            }
        }
        return addresses.map { address ->
            VpnRouteCanonicalizer.canonicalize(
                address = address,
                prefixLength = if (':' in address) 128 else 32
            )
        }
    }
}
