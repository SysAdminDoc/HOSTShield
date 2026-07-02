package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldInlineAction
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*

data class LeakTestResult(
    val testDomain: String,
    val resolvedTo: List<String>,
    val isLeaking: Boolean, // true = resolved outside HostShield (bad)
    val latencyMs: Long
)

data class LeakTestState(
    val isRunning: Boolean = false,
    val progress: String = "",
    val results: List<LeakTestResult> = emptyList(),
    val overallPass: Boolean? = null, // null = not run yet
    val blockedTestDomain: String? = null,
    val blockedCorrectly: Boolean? = null,
    val message: String? = null,
    val messageIsError: Boolean = false
)

@Composable
fun DnsLeakTestScreen(
    viewModel: DnsLeakTestViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "DNS leak test",
            subtitle = "Verify DNS queries are filtered through HostShield",
            onBack = onBack,
            actions = {
                HostShieldInlineAction(
                    label = if (state.isRunning) "Testing" else "Run test",
                    icon = Icons.Filled.PlayArrow,
                    accent = Teal,
                    enabled = !state.isRunning,
                    onClick = { viewModel.runTest() },
                )
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isRunning && state.progress.isNotEmpty()) {
                item {
                    HostShieldLoadingState(
                        title = "Testing DNS routing",
                        message = state.progress,
                        accent = Teal,
                    )
                }
            }

            state.message?.let { message ->
                item {
                    HostShieldStatusBanner(
                        icon = if (state.messageIsError) Icons.Filled.Error else Icons.Filled.Info,
                        title = if (state.messageIsError) "Leak test needs attention" else "Leak test note",
                        message = message,
                        accent = if (state.messageIsError) Yellow else Blue,
                        onDismiss = { viewModel.clearMessage() },
                    )
                }
            }

            // Overall result
            state.overallPass?.let { pass ->
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (pass) Green.copy(alpha = 0.12f) else Red.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (pass) Icons.Filled.VerifiedUser else Icons.Filled.GppBad,
                                    null,
                                    tint = if (pass) Green else Red,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    if (pass) "No DNS leaks detected" else "Potential DNS leak",
                                    color = if (pass) Green else Red,
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                                )
                                Text(
                                    if (pass) "DNS queries appear to stay inside HostShield filtering."
                                    else "Some queries may be bypassing HostShield filtering.",
                                    color = TextSecondary, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Blocked domain verification
                if (state.blockedCorrectly != null) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (state.blockedCorrectly == true) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                    null,
                                    tint = if (state.blockedCorrectly == true) Green else Yellow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        if (state.blockedCorrectly == true) "Blocking verified"
                                        else "Blocking may not be active",
                                        color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${state.blockedTestDomain} ${if (state.blockedCorrectly == true) "was blocked as expected" else "resolved normally"}",
                                        color = TextDim, fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Individual results
            items(state.results) { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (result.isLeaking) Red.copy(alpha = 0.06f) else Surface2
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(6.dp).clip(CircleShape)
                                .background(if (result.isLeaking) Red else Green)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                result.testDomain, color = TextPrimary, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace, maxLines = 1
                            )
                            if (result.resolvedTo.isNotEmpty()) {
                                result.resolvedTo.forEach { addr ->
                                    Text(addr, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            } else {
                                Text("NXDOMAIN, expected for random test domains", color = Green, fontSize = 10.sp)
                            }
                        }
                        Text("${result.latencyMs}ms", color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            if (state.results.isEmpty() && !state.isRunning) {
                item {
                    HostShieldEmptyState(
                        icon = Icons.Filled.VerifiedUser,
                        title = "No leak test has run yet",
                        message = "Run a quick check against random domains, blocked domains, and a known connectivity host.",
                        accent = Teal,
                        primaryActionLabel = "Run leak test",
                        onPrimaryAction = { viewModel.runTest() },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
