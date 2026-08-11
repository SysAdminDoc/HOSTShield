package com.hostshield.ui.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

fun Modifier.accessibilityHeading(): Modifier = semantics { heading() }

fun Modifier.accessibilityAction(label: String, enabled: Boolean = true): Modifier =
    semantics {
        role = Role.Button
        contentDescription = label
        if (!enabled) disabled()
    }

fun Modifier.accessibilityToggle(label: String, checked: Boolean, enabled: Boolean = true): Modifier =
    semantics(mergeDescendants = true) {
        role = Role.Switch
        contentDescription = label
        stateDescription = when {
            !enabled -> "Disabled"
            checked -> "On"
            else -> "Off"
        }
        if (!enabled) disabled()
    }

fun Modifier.accessibilitySelection(label: String, selected: Boolean): Modifier =
    semantics {
        role = Role.Tab
        contentDescription = label
        stateDescription = if (selected) "Selected" else "Not selected"
    }

/**
 * Semantics for one option in a radio group.
 *
 * Use on the whole row and leave the inner [androidx.compose.material3.RadioButton]
 * non-interactive: applying selection semantics to both makes TalkBack announce
 * every option twice, and a size-constrained clickable radio also falls below the
 * 48dp minimum interactive target. [Role.RadioButton] is also the correct role
 * here — [accessibilitySelection] reports Role.Tab.
 */
fun Modifier.accessibilityRadio(label: String, selected: Boolean): Modifier =
    semantics(mergeDescendants = true) {
        role = Role.RadioButton
        contentDescription = label
        stateDescription = if (selected) "Selected" else "Not selected"
    }

fun Modifier.accessibilityLiveRegion(label: String): Modifier =
    semantics {
        contentDescription = label
        liveRegion = LiveRegionMode.Polite
    }
