package com.hostshield.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.data.model.BlockMethod
import com.hostshield.ui.theme.*

// HostShield v1.0.0 - Premium Onboarding

@Composable
fun OnboardingScreen(
    isRootAvailable: Boolean,
    onComplete: (BlockMethod, Boolean) -> Unit,
    onRequestVpnPermission: ((Boolean) -> Unit) -> Unit = {}
) {
    var page by remember { mutableIntStateOf(0) }
    var selectedMethod by remember {
        mutableStateOf(if (isRootAvailable) BlockMethod.ROOT_HOSTS else BlockMethod.VPN)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            },
            label = "pages"
        ) { currentPage ->
            when (currentPage) {
                0 -> WelcomePage { page = 1 }
                1 -> MethodPage(
                    isRootAvailable = isRootAvailable,
                    selectedMethod = selectedMethod,
                    onSelectMethod = { selectedMethod = it },
                    onNext = { page = 2 }
                )
                2 -> ReadyPage(
                    method = selectedMethod,
                    onActivate = { onComplete(selectedMethod, true) },
                    onSkip = { onComplete(selectedMethod, false) },
                    onRequestVpnPermission = onRequestVpnPermission
                )
            }
        }

        // Page dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { idx ->
                Box(
                    modifier = Modifier
                        .size(if (idx == page) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (idx == page) Teal else Surface3)
                        .animateContentSize(spring())
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            // Glow via Canvas (no blur artifact)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            TealGlow.copy(alpha = glowPulse * 0.35f),
                            TealGlow.copy(alpha = glowPulse * 0.1f),
                            Color.Transparent
                        ), center = center, radius = size.minDimension / 2f
                    ), radius = size.minDimension / 2f, center = center
                )
            }
            // Rotating ring
            Canvas(modifier = Modifier.size(150.dp)) {
                rotate(ringRotation) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Teal.copy(alpha = 0.5f),
                            0.3f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Teal.copy(alpha = 0.5f)
                        ),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            // Shield orb
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Surface3, Surface1, Surface0))
                    )
                    .border(1.dp, Teal.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Shield, null, tint = Teal, modifier = Modifier.size(52.dp))
            }
        }

        Spacer(Modifier.height(40.dp))

        Text("HostShield", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "System-wide ad blocking\nfor your Android device",
            color = TextSecondary, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("Get Started", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun MethodPage(
    isRootAvailable: Boolean,
    selectedMethod: BlockMethod,
    onSelectMethod: (BlockMethod) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Choose Mode", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("How should HostShield block ads?", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))

        MethodOption(
            icon = Icons.Filled.AdminPanelSettings,
            title = "Root Mode",
            description = "Modifies system hosts file. Most efficient with zero battery impact.",
            selected = selectedMethod == BlockMethod.ROOT_HOSTS,
            enabled = isRootAvailable,
            disabledReason = if (!isRootAvailable) "Root not detected" else null,
            onClick = { onSelectMethod(BlockMethod.ROOT_HOSTS) }
        )
        Spacer(Modifier.height(12.dp))
        MethodOption(
            icon = Icons.Filled.VpnLock,
            title = "VPN Mode",
            description = "Local DNS filtering via VPN. No root required. Enables per-app stats.",
            selected = selectedMethod == BlockMethod.VPN,
            enabled = true,
            onClick = { onSelectMethod(BlockMethod.VPN) }
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun MethodOption(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    disabledReason: String? = null,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Teal.copy(alpha = 0.06f) else Surface1,
        animationSpec = tween(200), label = "bg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> Teal.copy(alpha = 0.5f)
            else -> Surface3.copy(alpha = 0.6f)
        },
        animationSpec = tween(200), label = "border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                icon, null,
                tint = if (selected) Teal else if (enabled) TextSecondary else TextDim,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = if (enabled) TextPrimary else TextDim, fontWeight = FontWeight.SemiBold)
                    disabledReason?.let { reason ->
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Yellow.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(reason, color = Yellow, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(description, color = if (enabled) TextSecondary else TextDim, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.CheckCircle, null, tint = Teal, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ReadyPage(
    method: BlockMethod,
    onActivate: () -> Unit,
    onSkip: () -> Unit,
    onRequestVpnPermission: ((Boolean) -> Unit) -> Unit = {}
) {
    var isActivating by remember { mutableStateOf(false) }
    var vpnDenied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Green.copy(alpha = 0.25f), Green.copy(alpha = 0.05f), Color.Transparent),
                        center = center, radius = size.minDimension / 2f
                    ), radius = size.minDimension / 2f, center = center
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Green.copy(alpha = 0.08f))
                    .border(1.dp, Green.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(44.dp))
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Ready to Go", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        val methodName = if (method == BlockMethod.ROOT_HOSTS) "Root" else "VPN"
        Text(
            "HostShield will use $methodName mode with 3 pre-enabled sources. " +
            "Tap Activate to start blocking ads and trackers immediately.",
            color = TextSecondary, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp
        )

        Spacer(Modifier.height(20.dp))

        listOf(
            "StevenBlack Unified" to "~79K domains",
            "AdAway Default" to "~400 domains",
            "Peter Lowe's List" to "~3K domains"
        ).forEach { (name, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface1)
                    .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Teal)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Text(count, color = TextDim, fontSize = 11.sp)
            }
        }

        if (vpnDenied) {
            Spacer(Modifier.height(12.dp))
            Text(
                "VPN permission is required for ad blocking without root. Please try again.",
                color = Red, textAlign = TextAlign.Center, fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = {
                if (method == BlockMethod.VPN) {
                    isActivating = true
                    vpnDenied = false
                    onRequestVpnPermission { granted ->
                        isActivating = false
                        if (granted) {
                            onActivate()
                        } else {
                            vpnDenied = true
                        }
                    }
                } else {
                    onActivate()
                }
            },
            enabled = !isActivating,
            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (isActivating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (isActivating) "Setting up..." else "Activate Protection",
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onSkip) {
            Text("Skip for now", color = TextDim, fontSize = 13.sp)
        }
    }
}
