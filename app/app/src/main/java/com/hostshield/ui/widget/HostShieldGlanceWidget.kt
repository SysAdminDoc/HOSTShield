package com.hostshield.ui.widget

import android.content.Context
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hostshield.MainActivity

// ---------- Colors ----------

private val DarkBackground = ColorProvider(Color(0xFF000000))
private val SurfaceColor = ColorProvider(Color(0xFF171D26))
private val TealAccent = ColorProvider(Color(0xFF7FFFEA))
private val TextPrimary = ColorProvider(Color(0xFFFFFFFF))
private val TextSecondary = ColorProvider(Color(0xFFD9E2F2))
private val InactiveRed = ColorProvider(Color(0xFFFF7AA8))

// ---------- Preference keys ----------

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
                // Header row: status text
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isActive) "Protected" else "Inactive",
                        style = TextStyle(
                            color = if (isActive) TealAccent else InactiveRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Stats row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                            style = TextStyle(color = TextSecondary, fontSize = 10.sp)
                        )
                    }

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
                            style = TextStyle(color = TextSecondary, fontSize = 10.sp)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DNS: $dnsServer",
                        style = TextStyle(color = TextSecondary, fontSize = 10.sp),
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
                    style = TextStyle(color = TextSecondary, fontSize = 12.sp)
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Block rate bar
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                ) {
                    val filledWidth = blockRate.coerceIn(0, 100)
                    if (filledWidth > 0) {
                        Box(
                            modifier = GlanceModifier
                                .height(6.dp)
                                .width(filledWidth.dp)
                                .background(TealAccent)
                        ) {}
                    }
                    if (filledWidth < 100) {
                        Box(
                            modifier = GlanceModifier
                                .height(6.dp)
                                .defaultWeight()
                                .background(SurfaceColor)
                        ) {}
                    }
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = "$blockRate% blocked",
                    style = TextStyle(color = TextSecondary, fontSize = 10.sp)
                )
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
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}
