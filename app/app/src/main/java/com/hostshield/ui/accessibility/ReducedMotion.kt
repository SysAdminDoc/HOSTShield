package com.hostshield.ui.accessibility

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether decorative animations should run.
 *
 * False when the user has disabled animations system-wide ("Remove animations"
 * in accessibility settings, or Developer options → Animator duration scale 0).
 * Perpetual pulse/rotation effects must honor this: they are decorative, and
 * ignoring the setting also burns frames for the surface's whole lifetime.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }
}
