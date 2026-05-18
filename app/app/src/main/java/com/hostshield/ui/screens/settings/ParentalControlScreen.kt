package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.ContentCategory
import com.hostshield.service.ParentalControlManager
import com.hostshield.service.ParentalControlManager.AgeProfile
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ParentalControlViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val manager: ParentalControlManager,
) : ViewModel() {

    val enabled = prefs.parentalEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val profile = prefs.parentalAgeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ADULT")

    var pinRequired by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var showPinDialog by mutableStateOf(false)
        private set
    var pinError by mutableStateOf<String?>(null)
        private set
    var pinLockoutMs by mutableStateOf(0L)
        private set
    var pinAction by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { pinRequired = manager.isPinSet() }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            if (value) {
                manager.enable(AgeProfile.fromName(profile.value))
            } else {
                if (manager.isPinSet()) {
                    showPinDialog = true
                    pinError = null
                    pinLockoutMs = manager.lockoutRemainingMs()
                    pinAction = "disable"
                } else {
                    manager.disable()
                }
            }
        }
    }

    fun dismissPinDialog() {
        showPinDialog = false
        pinAction = null
        pinError = null
        pinLockoutMs = 0L
    }

    fun onPinSubmitted(pin: String) {
        viewModelScope.launch {
            when (val r = manager.verifyPinDetailed(pin)) {
                is ParentalControlManager.PinResult.Success -> {
                    showPinDialog = false
                    pinError = null
                    pinLockoutMs = 0L
                    when (pinAction) {
                        "disable" -> manager.disable()
                    }
                    pinAction = null
                }
                is ParentalControlManager.PinResult.LockedOut -> {
                    pinError = "Too many attempts"
                    pinLockoutMs = r.retryAfterMs
                }
                is ParentalControlManager.PinResult.Wrong -> {
                    pinError = "Incorrect PIN"
                    pinLockoutMs = 0L
                }
                is ParentalControlManager.PinResult.NoPin -> {
                    // PIN was cleared between dialog open and submit — just disable
                    showPinDialog = false
                    manager.disable()
                }
            }
        }
    }

    fun setProfile(profileName: String) {
        viewModelScope.launch {
            manager.setProfile(AgeProfile.fromName(profileName))
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            if (manager.setPin(pin)) {
                pinRequired = true
                message = "PIN set successfully"
            } else {
                message = "Invalid PIN — must be 4 digits"
            }
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            manager.clearPin()
            pinRequired = false
            message = "PIN removed"
        }
    }

    fun getRestrictionsForProfile(profile: AgeProfile): Set<ContentCategory> =
        manager.getRestrictionsForProfile(profile)

    fun clearMessage() { message = null }
}

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
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text(
                "Parental Controls",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.accessibilityHeading()
            )
        }

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
                    Text("Parental Controls", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                    Text(
                        "Age Profile",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.accessibilityHeading()
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = Yellow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("PIN Lock", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (viewModel.pinRequired) "PIN is set — required to change settings"
                        else "No PIN set — anyone can modify parental controls",
                        color = TextDim, fontSize = 11.sp,
                    )
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
                        Text(
                            "Blocked Categories (${currentProfile.label})",
                            color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
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
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (msg.contains("Invalid")) Red.copy(alpha = 0.08f) else Green.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearMessage() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, "Dismiss parental control message", tint = TextDim, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // PIN verification dialog for disabling parental controls
    if (viewModel.showPinDialog) {
        var dialogPin by remember { mutableStateOf("") }
        val errorMessage = viewModel.pinError
        val lockoutMs = viewModel.pinLockoutMs
        var lockoutCountdown by remember(lockoutMs) { mutableStateOf(lockoutMs) }

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
            onDismissRequest = { viewModel.dismissPinDialog() },
            title = { Text("Enter PIN", color = TextPrimary) },
            text = {
                Column {
                    Text("Enter your 4-digit PIN to disable parental controls.", color = TextDim, fontSize = 13.sp)
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
                TextButton(onClick = { viewModel.dismissPinDialog() }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = Surface2,
            shape = RoundedCornerShape(12.dp),
        )
    }
}
