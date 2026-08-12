package com.hostshield.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hostshield.data.model.BlockMethod
import com.hostshield.service.VpnRecoveryAdvisory
import com.hostshield.ui.screens.home.HomeWarningsSection
import com.hostshield.ui.theme.Black
import com.hostshield.ui.theme.HostShieldTheme
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import com.hostshield.util.PrivateSpaceDetector
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProtectionResilienceMatrixTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun warningSurfacesRenderRecoveryBatteryAndProfileActions() {
        var batteryRequests = 0
        var batteryDismisses = 0
        var restartRequests = 0
        var recoveryDismisses = 0
        var privateSpaceDismisses = 0

        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { activityScenario ->
            activityScenario.onActivity { activity ->
                activity.setContent {
                    HostShieldTheme {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Black)
                                .verticalScroll(rememberScrollState())
                        ) {
                            HomeWarningsSection(
                                privateDnsWarning = null,
                                privateDnsSettingsIntent = null,
                                onDismissPrivateDns = {},
                                batteryWarning = "Battery optimization may stop HostShield in the background.",
                                onRequestBatteryExemption = { batteryRequests++ },
                                onDismissBattery = { batteryDismisses++ },
                                vpnRecoveryAdvisory = VpnRecoveryAdvisory(
                                    title = "VPN recovery advisory",
                                    message = "Always-on lockdown is active but no tunnel traffic has arrived.",
                                    detectedAtMillis = 1_000L
                                ),
                                canRestartDevice = true,
                                onRestartDevice = { restartRequests++ },
                                onDismissVpnRecovery = { recoveryDismisses++ },
                                privateSpaceWarning = PrivateSpaceDetector.getWarningMessage(isVpnMode = true),
                                onDismissPrivateSpace = { privateSpaceDismisses++ },
                                queryAnomalyWarning = null,
                                droppedQueries = 0,
                                isEnabled = true,
                                blockMethod = BlockMethod.VPN,
                                dohEnabled = false,
                                dnsTrapEnabled = false,
                                firewalledApps = 0,
                                networkFirewallActive = false,
                                queriesPerMinute = 0,
                                blocksPerMinute = 0,
                                avgLatencyMs = 0,
                                latencySparkline = emptyList(),
                                context = activity
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        waitForText("Battery Optimization")
        waitForText("VPN recovery advisory")
        waitForText("Private Space Detected")
        compose.onNodeWithText("Apps in Private Space bypass VPN-based blocking entirely.", substring = true)
            .assertIsDisplayed()

        compose.onNodeWithContentDescription(
            "Battery optimization may interrupt protection. Open battery exemption settings.",
            useUnmergedTree = true
        ).performClick()
        compose.onNodeWithContentDescription(
            "Restart device to recover the VPN stack",
            useUnmergedTree = true
        ).performClick()
        compose.onNodeWithContentDescription(
            "Dismiss battery optimization warning",
            useUnmergedTree = true
        ).performClick()
        compose.onNodeWithContentDescription(
            "Dismiss VPN recovery advisory",
            useUnmergedTree = true
        ).performClick()
        compose.onNodeWithContentDescription(
            "Dismiss Private Space warning",
            useUnmergedTree = true
        ).performClick()

        assertEquals(1, batteryRequests)
        assertEquals(1, restartRequests)
        assertEquals(1, batteryDismisses)
        assertEquals(1, recoveryDismisses)
        assertEquals(1, privateSpaceDismisses)
    }

    @Test
    fun diagnosticEventsExposeProtectionFailureWireNames() {
        val file = diagnosticEventFile()
        file.delete()
        val store = DiagnosticEventStore(context)

        store.recordBlocking(
            DiagnosticEventType.FOREGROUND_SERVICE_START_FAILED,
            "Foreground service start failed",
            mapOf(
                "caller" to "ProtectionResilienceMatrixTest",
                "service" to "DnsVpnService",
                "action" to "com.hostshield.VPN_START"
            )
        )
        store.recordBlocking(
            DiagnosticEventType.FOREGROUND_SERVICE_TIMEOUT,
            "Foreground service timed out",
            mapOf("service" to "DnsVpnService")
        )
        store.recordBlocking(DiagnosticEventType.VPN_START, "VPN started", mapOf("source" to "matrix"))
        store.recordBlocking(DiagnosticEventType.VPN_STOP, "VPN stopped", mapOf("source" to "matrix"))
        store.recordBlocking(
            DiagnosticEventType.VPN_RECOVERY_SNAPSHOT,
            "VPN recovery observation window reached with no tunnel ingress",
            mapOf(
                "sdk_int" to 37,
                "always_on" to true,
                "lockdown_enabled" to true,
                "tun_fd_valid" to true,
                "validated_physical_network" to true,
                "elapsed_since_vpn_start_ms" to 120_000L,
                "inbound_packet_count" to 0L
            )
        )

        val lines = store.readJsonlSnapshotBlocking()
            .lineSequence()
            .filter { it.isNotBlank() }
            .map(::JSONObject)
            .toList()

        assertEquals(5, lines.size)
        assertTrue(lines.any { it.getString("type") == "foreground_service_start_failed" })
        assertTrue(lines.any { it.getString("type") == "foreground_service_timeout" })
        assertTrue(lines.any { it.getString("type") == "vpn_start" })
        assertTrue(lines.any { it.getString("type") == "vpn_stop" })
        assertTrue(lines.any { it.getString("type") == "vpn_recovery_snapshot" })
    }

    private fun diagnosticEventFile(): File =
        File(File(context.filesDir, "diagnostics"), "diagnostic-events.jsonl")

    private fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMillis: Long = 10_000
    ) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
