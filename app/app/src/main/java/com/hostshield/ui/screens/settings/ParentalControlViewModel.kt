package com.hostshield.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.ContentCategory
import com.hostshield.service.ParentalControlManager
import com.hostshield.service.ParentalControlManager.AgeProfile
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
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
    private companion object {
        const val ACTION_DISABLE = "disable"
        const val ACTION_ENABLE = "enable"
        const val ACTION_CLEAR_PIN = "clear_pin"
        const val ACTION_UPGRADE_PIN = "upgrade_pin"
        const val ACTION_SET_PIN_PREFIX = "set_pin:"
        const val ACTION_PROFILE_PREFIX = "profile:"
    }

    val enabled = prefs.parentalEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val profile = prefs.parentalAgeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ADULT")

    var pinRequired by mutableStateOf(false)
        private set
    var pinUpgradeRequired by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var showPinDialog by mutableStateOf(false)
        private set
    var pinError by mutableStateOf<String?>(null)
        private set
    var pinLockoutMs by mutableLongStateOf(0L)
        private set
    var pinAction by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            pinRequired = manager.isPinSet()
            pinUpgradeRequired = manager.isPinRehashRequired()
            if (pinRequired && pinUpgradeRequired) {
                openPinDialog(ACTION_UPGRADE_PIN)
            }
        }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            if (manager.isPinSet()) {
                openPinDialog(if (value) ACTION_ENABLE else ACTION_DISABLE)
            } else if (value) {
                manager.enable(AgeProfile.fromName(profile.value))
            } else {
                manager.disable()
            }
        }
    }

    private fun openPinDialog(action: String) {
        showPinDialog = true
        pinError = null
        pinLockoutMs = manager.lockoutRemainingMs()
        pinAction = action
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
                    val action = pinAction
                    showPinDialog = false
                    pinError = null
                    pinLockoutMs = 0L
                    runVerifiedAction(action)
                    pinRequired = manager.isPinSet()
                    pinUpgradeRequired = manager.isPinRehashRequired()
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
                    val action = pinAction
                    showPinDialog = false
                    runVerifiedAction(action)
                    pinRequired = manager.isPinSet()
                    pinUpgradeRequired = manager.isPinRehashRequired()
                    pinAction = null
                }
            }
        }
    }

    private suspend fun runVerifiedAction(action: String?) {
        when {
            action == ACTION_DISABLE -> manager.disable()
            action == ACTION_ENABLE -> manager.enable(AgeProfile.fromName(profile.value))
            action == ACTION_CLEAR_PIN -> {
                manager.clearPin()
                message = "PIN removed"
            }
            action == ACTION_UPGRADE_PIN -> {
                message = "PIN upgraded successfully"
            }
            action?.startsWith(ACTION_SET_PIN_PREFIX) == true -> {
                val newPin = action.removePrefix(ACTION_SET_PIN_PREFIX)
                if (manager.setPin(newPin)) {
                    message = "PIN set successfully"
                } else {
                    message = "Invalid PIN - must be 4 digits"
                }
            }
            action?.startsWith(ACTION_PROFILE_PREFIX) == true -> {
                manager.setProfile(AgeProfile.fromName(action.removePrefix(ACTION_PROFILE_PREFIX)))
            }
        }
    }

    fun setProfile(profileName: String) {
        viewModelScope.launch {
            if (manager.isPinSet()) {
                openPinDialog(ACTION_PROFILE_PREFIX + profileName)
            } else {
                manager.setProfile(AgeProfile.fromName(profileName))
            }
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            if (pin.length != 4 || !pin.all { it.isDigit() }) {
                message = "Invalid PIN - must be 4 digits"
            } else if (manager.isPinSet()) {
                openPinDialog(ACTION_SET_PIN_PREFIX + pin)
            } else if (manager.setPin(pin)) {
                pinRequired = true
                pinUpgradeRequired = false
                message = "PIN set successfully"
            } else {
                message = "Invalid PIN - must be 4 digits"
            }
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            if (manager.isPinSet()) {
                openPinDialog(ACTION_CLEAR_PIN)
            } else {
                manager.clearPin()
                pinRequired = false
                pinUpgradeRequired = false
                message = "PIN removed"
            }
        }
    }

    fun getRestrictionsForProfile(profile: AgeProfile): Set<ContentCategory> =
        manager.getRestrictionsForProfile(profile)

    fun clearMessage() { message = null }

    fun isPinUpgradeDialog(): Boolean = pinAction == ACTION_UPGRADE_PIN

    fun pinDialogMessage(): String = when {
        pinAction == ACTION_UPGRADE_PIN -> "Enter your 4-digit PIN to upgrade the PIN lock."
        pinAction == ACTION_CLEAR_PIN -> "Enter your 4-digit PIN to remove the PIN lock."
        pinAction == ACTION_ENABLE -> "Enter your 4-digit PIN to enable parental controls."
        pinAction?.startsWith(ACTION_SET_PIN_PREFIX) == true -> "Enter your current PIN to set a new PIN."
        pinAction?.startsWith(ACTION_PROFILE_PREFIX) == true -> "Enter your 4-digit PIN to change parental settings."
        else -> "Enter your 4-digit PIN to disable parental controls."
    }
}
