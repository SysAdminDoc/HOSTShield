package com.hostshield.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hostshield.MainActivity
import com.hostshield.R

// ---------- Colors ----------

private val DarkBackground = ColorProvider(
    day = android.graphics.Color.parseColor("#1E1E2E"),
    night = android.graphics.Color.parseColor("#1E1E2E")
)

private val SurfaceColor = ColorProvider(
    day = android.graphics.Color.parseColor("#313244"),
    night = android.graphics.Color.parseColor("#313244")
)

private val TealAccent = ColorProvider(
    day = android.graphics.Color.parseColor("#94E2D5"),
    night = android.graphics.Color.parseColor("#94E2D5")
)

private val TextPrimary = ColorProvider(
    day = android.graphics.Color.parseColor("#CDD6F4"),
    night = android.graphics.Color.parseColor("#CDD6F4")
)

private val TextSecondary = ColorProvider(
    day = android.graphics.Color.parseColor("#A6ADC8"),
    night = android.graphics.Color.parseColor("#A6ADC8")
)

private val InactiveRed = ColorProvider(
    day = android.graphics.Color.parseColor("#F38BA8"),
    night = android.graphics.Color.parseColor("#F38BA8")
)

// ---------- Preference keys ----------

private const val WIDGET_PREFS = "hostshield_widget_prefs"

private object WidgetKeys {
    const val KEY_ACTIVE = "is_active"
    const val KEY_BLOCKED = "blocked_count"
    const val KEY_ALLOWED = "allowed_count"
    const val KEY_DNS_SERVER = "dns_server"
    const val KEY_LAST_UPDATED = "last_updated"
}

// ===================================================================
// 1. Toggle + Stats Combo Widget
// ===================================================================

class HostShieldGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val isActive = prefs[androidx.datastore.preferences.core.booleanPreferencesKey(WidgetKeys.KEY_ACTIVE)] ?: false
        val blockedCount = prefs[androidx.datastore.preferences.core.longPreferencesKey(WidgetKeys.KEY_BLOCKED)] ?: 0L
        val allowedCount = prefs[androidx.datastore.preferences.core.longPreferencesKey(WidgetKeys.KEY_ALLOWED)] ?: 0L
        val dnsServer = prefs[androidx.datastore.preferences.core.stringPreferencesKey(WidgetKeys.KEY_DNS_SERVER)] ?: "None"
        val lastUpdated = prefs[androidx.datastore.preferences.core.stringPreferencesKey(WidgetKeys.KEY_LAST_UPDATED)] ?: "--:--"

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(DarkBackground)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row: shield icon + toggle
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shield status icon
                    Image(
                        provider = ImageProvider(
                            if (isActive) R.drawable.ic_shield_on else R.drawable.ic_shield_off
                        ),
                        contentDescription = if (isActive) "Shield active" else "Shield inactive",
                        modifier = GlanceModifier.size(32.dp)
                    )

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    Text(
                        text = if (isActive) "Protected" else "Inactive",
                        style = TextStyle(
                            color = if (isActive) TealAccent else InactiveRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    // Toggle button
                    CircleIconButton(
                        imageProvider = ImageProvider(
                            if (isActive) R.drawable.ic_stop else R.drawable.ic_play
                        ),
                        contentDescription = if (isActive) "Stop VPN" else "Start VPN",
                        backgroundColor = if (isActive) TealAccent else SurfaceColor,
                        contentColor = DarkBackground,
                        onClick = actionSendBroadcast<HostShieldGlanceReceiver>(
                            Intent("com.hostshield.ACTION_TOGGLE_VPN")
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Stats row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Blocked count
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatCount(blockedCount),
                            style = TextStyle(
                                color = TealAccent,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "blocked",
                            style = TextStyle(
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }

                    // Allowed count
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatCount(allowedCount),
                            style = TextStyle(
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "allowed",
                            style = TextStyle(
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // DNS server + last updated
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DNS: $dnsServer",
                        style = TextStyle(
                            color = TextSecondary,
                            fontSize = 10.sp
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = lastUpdated,
                        style = TextStyle(
                            color = TextSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.End
                        )
                    )
                }
            }
        }
    }
}

class HostShieldGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = HostShieldGlanceWidget()
}

// ===================================================================
// 3. Stats-Only Compact Widget
// ===================================================================

class HostShieldStatsGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { StatsWidgetContent() }
    }

    @Composable
    private fun StatsWidgetContent() {
        val prefs = currentState<Preferences>()
        val blockedCount = prefs[androidx.datastore.preferences.core.longPreferencesKey(WidgetKeys.KEY_BLOCKED)] ?: 0L
        val allowedCount = prefs[androidx.datastore.preferences.core.longPreferencesKey(WidgetKeys.KEY_ALLOWED)] ?: 0L
        val total = blockedCount + allowedCount
        val blockRate = if (total > 0) ((blockedCount * 100) / total).toInt() else 0

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(DarkBackground)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large blocked count
                Text(
                    text = formatCount(blockedCount),
                    style = TextStyle(
                        color = TealAccent,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                Text(
                    text = "blocked today",
                    style = TextStyle(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Block rate bar
                BlockRateBar(blockRate)

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = "$blockRate% blocked",
                    style = TextStyle(
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun BlockRateBar(percentage: Int) {
        val barWidth = 100 // conceptual full width
        val filledWidth = (percentage.coerceIn(0, 100))

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            if (filledWidth > 0) {
                Box(
                    modifier = GlanceModifier
                        .height(6.dp)
                        .width((filledWidth).dp)
                        .background(TealAccent)
                ) {}
            }
            if (filledWidth < barWidth) {
                Box(
                    modifier = GlanceModifier
                        .height(6.dp)
                        .defaultWeight()
                        .background(SurfaceColor)
                ) {}
            }
        }
    }
}

class HostShieldStatsGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = HostShieldStatsGlanceWidget()
}

// ---------- Utility ----------

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}
