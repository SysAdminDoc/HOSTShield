package com.hostshield.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DnsTrapRoutePlannerTest {

    @Test
    fun `dual stack trap routes include IPv4 and IPv6 host routes`() {
        val routes = DnsTrapRoutePlanner.routeTargets(
            DnsTrapIpSets(
                dnsTrapIpv4 = setOf("8.8.8.8"),
                dnsTrapIpv6 = setOf("2606:4700:4700::1111"),
                dohBypassIpv4 = setOf("1.1.1.1"),
                dohBypassIpv6 = setOf("2001:4860:4860::8888")
            ),
            ipv6Available = true
        )

        assertThat(routes).containsExactly(
            VpnRouteCanonicalizer.Route("8.8.8.8", 32),
            VpnRouteCanonicalizer.Route("1.1.1.1", 32),
            VpnRouteCanonicalizer.Route("2606:4700:4700:0:0:0:0:1111", 128),
            VpnRouteCanonicalizer.Route("2001:4860:4860:0:0:0:0:8888", 128)
        ).inOrder()
    }

    @Test
    fun `IPv6 routes are omitted when the tunnel is v4-only`() {
        val routes = DnsTrapRoutePlanner.routeTargets(
            DnsTrapIpSets.FALLBACK,
            ipv6Available = false
        )

        assertThat(routes).isNotEmpty()
        assertThat(routes.map { it.address }).containsNoneOf(
            "2606:4700:4700:0:0:0:0:1111",
            "2001:4860:4860:0:0:0:0:8888"
        )
        assertThat(routes).contains(VpnRouteCanonicalizer.Route("8.8.8.8", 32))
    }
}
