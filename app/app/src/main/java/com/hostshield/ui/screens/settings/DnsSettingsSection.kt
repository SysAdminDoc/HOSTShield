package com.hostshield.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.hostshield.BuildConfig
import com.hostshield.R
import com.hostshield.util.ExperimentalEngineDisclosure
import com.hostshield.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DnsSettingsSection(
    dohEnabled: Boolean,
    onDohEnabledChange: (Boolean) -> Unit,
    dohProvider: String,
    onDohProviderChange: (String) -> Unit,
    dotEnabled: Boolean,
    onDotEnabledChange: (Boolean) -> Unit,
    dotProvider: String,
    onDotProviderChange: (String) -> Unit,
    doqEnabled: Boolean,
    onDoqEnabledChange: (Boolean) -> Unit,
    doqProvider: String,
    onDoqProviderChange: (String) -> Unit,
    wireGuardEnabled: Boolean,
    onWireGuardEnabledChange: (Boolean) -> Unit,
    wireGuardEndpoint: String,
    onWireGuardEndpointChange: (String) -> Unit,
    wireGuardDnsIp: String,
    onWireGuardDnsIpChange: (String) -> Unit,
    dnsTrapEnabled: Boolean,
    onDnsTrapEnabledChange: (Boolean) -> Unit,
    customUpstreamDns: String,
    onCustomUpstreamDnsChange: (String) -> Unit,
    blockResponseType: String,
    onBlockResponseTypeChange: (String) -> Unit,
    edeEnabled: Boolean,
    onEdeEnabledChange: (Boolean) -> Unit,
    onClearDnsCache: () -> Unit,
    onNavigateToDnsBenchmark: () -> Unit,
    onNavigateToDnsLeakTest: () -> Unit
) {
    SettingsSection("DNS", Icons.Filled.Dns, Blue) {
        SettingsToggle("DNS-over-HTTPS", "Encrypt DNS queries", Icons.Filled.Lock, dohEnabled) {
            onDohEnabledChange(it)
        }
        if (dohEnabled) {
            Spacer(Modifier.height(6.dp))
            DohProviderSelector(dohProvider) { onDohProviderChange(it) }
        }
        Spacer(Modifier.height(8.dp))
        SettingsToggle("DNS-over-TLS", "TLS-encrypted DNS (RFC 7858)", Icons.Filled.Shield, dotEnabled) {
            onDotEnabledChange(it)
        }
        if (dotEnabled) {
            Spacer(Modifier.height(6.dp))
            DotProviderSelector(dotProvider) { onDotProviderChange(it) }
        }
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(8.dp))
            SettingsToggle("DNS-over-QUIC (experimental)", ExperimentalEngineDisclosure.DOQ_UI, Icons.Filled.Bolt, doqEnabled) {
                onDoqEnabledChange(it)
            }
            if (doqEnabled) {
                Spacer(Modifier.height(6.dp))
                ExperimentalEngineNote(ExperimentalEngineDisclosure.DOQ_LABEL)
                Spacer(Modifier.height(6.dp))
                DoqProviderSelector(doqProvider) { onDoqProviderChange(it) }
            }
            Spacer(Modifier.height(8.dp))
            SettingsToggle("WireGuard DNS (experimental)", ExperimentalEngineDisclosure.WIREGUARD_UI, Icons.Filled.VpnKey, wireGuardEnabled) {
                onWireGuardEnabledChange(it)
            }
            if (wireGuardEnabled) {
                Spacer(Modifier.height(6.dp))
                ExperimentalEngineNote(ExperimentalEngineDisclosure.WIREGUARD_LABEL)
                Spacer(Modifier.height(6.dp))
                var wgEndpoint by remember { mutableStateOf(wireGuardEndpoint) }
            LaunchedEffect(wireGuardEndpoint) { wgEndpoint = wireGuardEndpoint }
            OutlinedTextField(
                value = wgEndpoint,
                onValueChange = { wgEndpoint = it },
                placeholder = { Text(stringResource(R.string.dns_endpoint_hint), color = TextDim, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                    cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                trailingIcon = {
                    if (wgEndpoint != wireGuardEndpoint) {
                        IconButton(onClick = { onWireGuardEndpointChange(wgEndpoint) }) {
                            Icon(Icons.Filled.Check, "Save WireGuard endpoint", tint = Green, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            var wgDnsIp by remember { mutableStateOf(wireGuardDnsIp) }
            LaunchedEffect(wireGuardDnsIp) { wgDnsIp = wireGuardDnsIp }
            OutlinedTextField(
                value = wgDnsIp,
                onValueChange = { wgDnsIp = it },
                placeholder = { Text(stringResource(R.string.dns_tunnel_ip_hint), color = TextDim, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                    cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                trailingIcon = {
                    if (wgDnsIp != wireGuardDnsIp) {
                        IconButton(onClick = { onWireGuardDnsIpChange(wgDnsIp) }) {
                            Icon(Icons.Filled.Check, "Save WireGuard DNS IP", tint = Green, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.dns_wireguard_keys_hint), color = TextDim, fontSize = 10.sp)
        }
        } // end BuildConfig.DEBUG gate for DoQ/WireGuard
        Spacer(Modifier.height(8.dp))
        SettingsToggle(
            "DNS Trap",
            "Catch hardcoded DNS + block DoH/DoT bypass",
            Icons.Filled.FilterAlt,
            dnsTrapEnabled
        ) { onDnsTrapEnabledChange(it) }
        Spacer(Modifier.height(8.dp))
        // Custom upstream DNS
        var customDns by remember { mutableStateOf(customUpstreamDns) }
        LaunchedEffect(customUpstreamDns) { customDns = customUpstreamDns }
        Text(stringResource(R.string.dns_custom_upstream), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.dns_custom_upstream_hint), color = TextDim, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = customDns,
            onValueChange = { customDns = it },
            placeholder = { Text(stringResource(R.string.dns_custom_upstream_example), color = TextDim, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            trailingIcon = {
                if (customDns != customUpstreamDns) {
                    IconButton(onClick = { onCustomUpstreamDnsChange(customDns) }) {
                        Icon(Icons.Filled.Check, "Save custom upstream DNS", tint = Green, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        SettingsRow("DNS benchmark", "Test latency to public DNS resolvers", Icons.Filled.Speed, onClick = onNavigateToDnsBenchmark)
        Spacer(Modifier.height(8.dp))
        SettingsRow("DNS leak test", "Verify queries go through HostShield", Icons.Filled.VerifiedUser, onClick = onNavigateToDnsLeakTest)
        Spacer(Modifier.height(8.dp))
        // Block response type selector
        Text(stringResource(R.string.dns_block_response), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "How blocked domains are answered",
            color = TextDim, fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        BlockResponseSelector(blockResponseType) { onBlockResponseTypeChange(it) }
        Spacer(Modifier.height(8.dp))
        SettingsToggle(
            "Extended DNS Errors",
            "Attach RFC 8914 EDE info code to blocked responses",
            Icons.Filled.Info,
            edeEnabled
        ) { onEdeEnabledChange(it) }

        // DNS Cache management
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.dns_cache_title), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        val cacheStats = com.hostshield.service.DnsVpnService.currentCacheStats
        if (cacheStats != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${cacheStats.size + cacheStats.negativeSize + cacheStats.failureSize} entries", color = TextDim, fontSize = 11.sp)
                Text("${(cacheStats.hitRate * 100).toInt()}% hit rate", color = Green, fontSize = 11.sp)
                Text("${cacheStats.staleHits} stale", color = TextDim, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            onClick = onClearDnsCache,
            shape = RoundedCornerShape(8.dp),
            color = Surface2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Cached, null, tint = Blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.dns_cache_clear), color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ExperimentalEngineNote(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.Warning, null, tint = Yellow, modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            color = Yellow,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}
