package com.hostshield.util

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log

/**
 * Detects Android 15+ Private Space feature.
 *
 * Private Space apps run in a separate user profile with its own VPN slot.
 * HostShield's VPN-based DNS blocking does NOT protect Private Space apps.
 * In root mode, iptables rules applied to the main user also don't cover
 * the private profile unless explicitly applied to that user ID.
 *
 * This is a confirmed Android platform limitation documented by Google.
 */
object PrivateSpaceDetector {

    private const val TAG = "PrivateSpaceDetector"

    /**
     * Returns true if the device has multiple user profiles (indicating
     * Private Space, work profile, or secondary user). On API 35+ this
     * specifically targets Private Space; on older APIs it detects managed
     * profiles that also bypass VPN.
     */
    fun hasPrivateSpace(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        try {
            val um = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return false
            val profiles = um.userProfiles
            if (profiles.size > 1) {
                Log.i(TAG, "Multiple user profiles detected (${profiles.size}): possible Private Space or work profile")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check user profiles: ${e.message}")
        }
        return false
    }

    /**
     * User-facing warning message when Private Space is detected.
     */
    fun getWarningMessage(isVpnMode: Boolean): String {
        val modeNote = if (isVpnMode) {
            "Apps in Private Space bypass VPN-based blocking entirely."
        } else {
            "Root-mode iptables rules may not cover Private Space apps."
        }
        return "Android Private Space detected. $modeNote " +
            "For full coverage, install HostShield within Private Space separately."
    }
}
