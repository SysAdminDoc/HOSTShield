package com.hostshield.ui.screens.settings

import com.hostshield.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.ContentCategory
import com.hostshield.service.ParentalControlManager
import com.hostshield.service.ParentalControlManager.AgeProfile
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@Composable
fun ParentalControlScreen(
    onBack: () -> Unit,
    viewModel: ParentalControlViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val profileName by viewModel.profile.collectAsStateWithLifecycle()
    val currentProfile = AgeProfile.fromName(profileName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HostShieldBackHeader(
            title = "Parental controls",
            subtitle = if (enabled) "Active with ${currentProfile.label} restrictions" else "Disabled until you turn it on",
            onBack = onBack,
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
        )

        // Enable toggle
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .background(Peach.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AdminPanelSettings, null, tint = Peach, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Protection status", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (enabled) "Active — ${currentProfile.label}" else "Disabled",
                        color = if (enabled) Green else TextDim, fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = enabled, onCheckedChange = { viewModel.setEnabled(it) },
                    modifier = Modifier
                        .testTag(HostShieldTestTags.Parental.EnableToggle)
                        .accessibilityToggle("Parental controls", enabled),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Peach, checkedTrackColor = Peach.copy(alpha = 0.25f),
                        uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3,
                    ),
                )
            }
        }

        if (enabled) {
            // Age profile selector
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HostShieldPanelHeader(
                        icon = Icons.Filled.Groups,
                        title = "Age profile",
                        subtitle = "Choose the default restriction level",
                        accent = Peach,
                    )
                    Spacer(Modifier.height(10.dp))
                    AgeProfile.entries.forEach { profile ->
                        val selected = profile == currentProfile
                        val restrictions = viewModel.getRestrictionsForProfile(profile)
                        Surface(
                            onClick = { viewModel.setProfile(profile.name) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Peach.copy(alpha = 0.08f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().accessibilitySelection("${profile.label} age profile", selected),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { viewModel.setProfile(profile.name) },
                                    modifier = Modifier.accessibilitySelection("${profile.label} age profile", selected),
                                    colors = RadioButtonDefaults.colors(selectedColor = Peach, unselectedColor = TextDim),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    if (restrictions.isNotEmpty()) {
                                        Text(
                                            "Blocks: ${restrictions.joinToString(", ") { it.displayName }}",
                                            color = TextDim, fontSize = 10.sp, lineHeight = 14.sp,
                                        )
                                    } else {
                                        Text("No restrictions", color = TextDim, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // PIN management
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HostShieldPanelHeader(
                        icon = Icons.Filled.Lock,
                        title = "PIN lock",
                        subtitle = if (viewModel.pinRequired) "Required before parental settings change" else "Not set",
                        accent = Yellow,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (viewModel.pinRequired) "PIN is set — required to change settings"
                        else "No PIN set — anyone can modify parental controls",
                        color = TextDim, fontSize = 11.sp,
                    )
                    if (viewModel.pinUpgradeRequired) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "PIN security upgrade required. Enter the current PIN to keep parental controls locked with current protection.",
                            color = Yellow,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    var pinInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        placeholder = { Text("4-digit PIN", color = TextDim, fontSize = 13.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(HostShieldTestTags.Parental.PinField),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Yellow, unfocusedBorderColor = Surface3,
                            cursorColor = Yellow, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setPin(pinInput)
                                pinInput = ""
                            },
                            enabled = pinInput.length == 4,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Yellow),
                            modifier = Modifier.testTag(HostShieldTestTags.Parental.SetPinButton),
                        ) {
                            Text("Set PIN", fontSize = 12.sp)
                        }
                        if (viewModel.pinRequired) {
                            OutlinedButton(
                                onClick = { viewModel.clearPin() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                            ) {
                                Text("Remove PIN", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Restricted categories for current profile
            val restrictions = viewModel.getRestrictionsForProfile(currentProfile)
            if (restrictions.isNotEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        HostShieldPanelHeader(
                            icon = Icons.Filled.Block,
                            title = "Blocked categories",
                            subtitle = currentProfile.label,
                            accent = Red,
                        )
                        Spacer(Modifier.height(8.dp))
                        restrictions.forEach { cat ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Block, null, tint = Red, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(cat.displayName, color = TextPrimary, fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Text("— ${cat.description}", color = TextDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Message
        viewModel.message?.let { msg ->
            val isError = viewModel.messageIsError
            HostShieldStatusBanner(
                icon = if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                title = if (isError) "Parental control update failed" else "Parental controls updated",
                message = msg,
                accent = if (isError) Red else Green,
                onDismiss = { viewModel.clearMessage() },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    // PIN verification dialog for disabling parental controls
    if (viewModel.showPinDialog) {
        var dialogPin by remember { mutableStateOf("") }
        val errorMessage = viewModel.pinError
        val lockoutMs = viewModel.pinLockoutMs
        var lockoutCountdown by remember(lockoutMs) { mutableLongStateOf(lockoutMs) }

        // Live-tick the lockout countdown
        LaunchedEffect(lockoutMs) {
            if (lockoutMs > 0) {
                val deadline = System.currentTimeMillis() + lockoutMs
                while (System.currentTimeMillis() < deadline) {
                    lockoutCountdown = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                    kotlinx.coroutines.delay(250)
                }
                lockoutCountdown = 0L
            }
        }
        val locked = lockoutCountdown > 0

        AlertDialog(
            onDismissRequest = {
                if (!viewModel.isPinUpgradeDialog()) {
                    viewModel.dismissPinDialog()
                }
            },
            title = { Text("Enter PIN", color = TextPrimary) },
            text = {
                Column {
                    Text(viewModel.pinDialogMessage(), color = TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = dialogPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                dialogPin = it
                            }
                        },
                        enabled = !locked,
                        placeholder = { Text("PIN", color = TextDim, fontSize = 13.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = {
                            when {
                                locked -> Text(
                                    "Locked out — retry in ${(lockoutCountdown / 1000) + 1}s",
                                    color = Red, fontSize = 11.sp,
                                )
                                errorMessage != null -> Text(errorMessage, color = Red, fontSize = 11.sp)
                                else -> {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(HostShieldTestTags.Parental.DialogPinField),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Yellow, unfocusedBorderColor = Surface3,
                            cursorColor = Yellow, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (dialogPin.length == 4 && !locked) {
                            viewModel.onPinSubmitted(dialogPin)
                            dialogPin = ""
                        }
                    },
                    enabled = dialogPin.length == 4 && !locked,
                    modifier = Modifier.testTag(HostShieldTestTags.Parental.DialogConfirmButton),
                ) {
                    Text("Confirm", color = Yellow)
                }
            },
            dismissButton = {
                if (!viewModel.isPinUpgradeDialog()) {
                    TextButton(onClick = { viewModel.dismissPinDialog() }) {
                        Text(stringResource(R.string.action_cancel), color = TextDim)
                    }
                }
            },
            containerColor = Surface2,
            shape = RoundedCornerShape(12.dp),
        )
    }
}
