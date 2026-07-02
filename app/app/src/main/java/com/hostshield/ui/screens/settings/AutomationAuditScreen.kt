package com.hostshield.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.BuildConfig
import com.hostshield.data.database.AutomationAuditDao
import com.hostshield.data.model.AutomationAuditEntry
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AutomationAuditScreen(
    viewModel: AutomationAuditViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().background(Black)
    ) {
        HostShieldBackHeader(
            title = "Automation",
            subtitle = "${entries.size} recent command events",
            onBack = onBack,
        )

        HostShieldSegmentedTabs(
            options = listOf(
                HostShieldSegmentOption(0, "Audit Log", Blue, Icons.AutoMirrored.Filled.ReceiptLong),
                HostShieldSegmentOption(1, "Commands", Teal, Icons.Filled.Terminal),
            ),
            selected = selectedTab,
            onSelected = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        when (selectedTab) {
            0 -> AuditLogTab(entries, dateFormat)
            1 -> CommandReferenceTab()
        }
    }
}

@Composable
private fun AuditLogTab(entries: List<AutomationAuditEntry>, dateFormat: SimpleDateFormat) {
    if (entries.isEmpty()) {
        HostShieldEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "No automation commands recorded",
            message = "Tasker, ADB, and trusted app commands will appear here with caller, result, and timing details.",
            accent = Blue,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(entries, key = { _, e -> e.id }) { _, entry ->
                AuditEntryCard(entry, dateFormat)
            }
        }
    }
}

@Composable
private fun CommandReferenceTab() {
    val context = LocalContext.current
    val appId = BuildConfig.APPLICATION_ID
    val receiverComponent = "$appId/.service.AutomationReceiver"

    val commands = remember(appId) {
        listOf(
            CommandEntry(
                "Enable blocking",
                "adb shell am broadcast -a com.hostshield.ACTION_ENABLE -n $receiverComponent",
                null
            ),
            CommandEntry(
                "Disable blocking",
                "adb shell am broadcast -a com.hostshield.ACTION_DISABLE -n $receiverComponent",
                null
            ),
            CommandEntry(
                "Toggle blocking",
                "adb shell am broadcast -a com.hostshield.ACTION_TOGGLE -n $receiverComponent",
                null
            ),
            CommandEntry(
                "Query status",
                "adb shell am broadcast -a com.hostshield.ACTION_STATUS -n $receiverComponent",
                "Broadcasts $appId.STATUS_RESULT with extras: enabled, blocked_count, method, version"
            ),
            CommandEntry(
                "Refresh blocklist",
                "adb shell am broadcast -a com.hostshield.ACTION_REFRESH_BLOCKLIST -n $receiverComponent",
                null
            ),
            CommandEntry(
                "Set profile",
                "adb shell am broadcast -a com.hostshield.ACTION_SET_PROFILE --es profile_name Work -n $receiverComponent",
                "Extra: profile_name (String, required)"
            ),
            CommandEntry(
                "Set DNS servers",
                "adb shell am broadcast -a com.hostshield.ACTION_SET_DNS --es dns_servers \"9.9.9.9,149.112.112.112\" -n $receiverComponent",
                "Extra: dns_servers (String, comma-separated)"
            ),
            CommandEntry(
                "Pause blocking",
                "adb shell am broadcast -a com.hostshield.ACTION_PAUSE --ei duration_minutes 5 -n $receiverComponent",
                "Extra: duration_minutes (int, 1–1440). Pass 0 to unpause."
            ),
            CommandEntry(
                "Apply firewall rules",
                "adb shell am broadcast -a com.hostshield.ACTION_APPLY_FIREWALL -n $receiverComponent",
                "Root mode only"
            ),
            CommandEntry(
                "Clear firewall rules",
                "adb shell am broadcast -a com.hostshield.ACTION_CLEAR_FIREWALL -n $receiverComponent",
                "Root mode only"
            ),
            CommandEntry(
                "Grant automation permission",
                "adb shell pm grant $appId $appId.permission.AUTOMATION",
                "Required for third-party app callers. Shell (ADB) and root callers are always trusted."
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Package: $appId",
                color = Blue,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(commands.size) { i ->
            CommandCard(commands[i], context)
        }
    }
}

private data class CommandEntry(
    val label: String,
    val command: String,
    val note: String?
)

@Composable
private fun CommandCard(entry: CommandEntry, context: Context) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("command", entry.command))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, "Copy", tint = Blue, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                entry.command,
                color = Green,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Surface2)
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
                maxLines = 1
            )
            if (entry.note != null) {
                Spacer(Modifier.height(4.dp))
                Text(entry.note, color = TextDim, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AuditEntryCard(entry: AutomationAuditEntry, dateFormat: SimpleDateFormat) {
    val resultColor = when (entry.result) {
        "OK" -> Green
        "DENIED" -> Red
        "RATE_LIMITED" -> Peach
        "ERROR" -> Red
        else -> TextDim
    }
    val actionShort = entry.action.removePrefix("com.hostshield.ACTION_").removePrefix("com.hostshield.")

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = resultColor.copy(alpha = 0.12f),
                modifier = Modifier.width(52.dp)
            ) {
                Text(
                    entry.result,
                    color = resultColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(actionShort, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "from ${entry.callerPackage} (uid ${entry.callerUid})",
                    color = TextDim, fontSize = 10.sp
                )
            }
            Text(
                dateFormat.format(Date(entry.timestamp)),
                color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}
