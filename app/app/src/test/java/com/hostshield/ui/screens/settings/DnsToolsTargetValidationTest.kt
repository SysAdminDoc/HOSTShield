package com.hostshield.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DnsToolsTargetValidationTest {

    @Test
    fun `normalizes safe hostnames and addresses`() {
        assertThat(normalizeNetworkDiagnosticTarget(" Example.COM. ")).isEqualTo("example.com")
        assertThat(normalizeNetworkDiagnosticTarget("8.8.8.8")).isEqualTo("8.8.8.8")
        assertThat(normalizeNetworkDiagnosticTarget("2001:4860:4860::8888")).isEqualTo("2001:4860:4860::8888")
    }

    @Test
    fun `rejects shell metacharacters and malformed addresses`() {
        assertThat(normalizeNetworkDiagnosticTarget("8.8.8.8; reboot")).isNull()
        assertThat(normalizeNetworkDiagnosticTarget("example.com && id")).isNull()
        assertThat(normalizeNetworkDiagnosticTarget("$(getprop)")).isNull()
        assertThat(normalizeNetworkDiagnosticTarget("999.1.1.1")).isNull()
        assertThat(normalizeNetworkDiagnosticTarget("-bad.example.com")).isNull()
    }
}
