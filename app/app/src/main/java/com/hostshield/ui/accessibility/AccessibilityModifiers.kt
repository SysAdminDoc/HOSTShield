package com.hostshield.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.hostshield.R

// State descriptions are read aloud by TalkBack, so they must come from
// resources. They were baked English here, which meant every toggle in the app
// announced "On"/"Off"/"Selected" regardless of device language — mixed-language
// output for any non-English user. These are @Composable so they can resolve
// strings; call sites are unchanged.

fun Modifier.accessibilityHeading(): Modifier = semantics { heading() }

@Composable
fun Modifier.accessibilityAction(label: String, enabled: Boolean = true): Modifier =
    semantics {
        role = Role.Button
        contentDescription = label
        if (!enabled) disabled()
    }

@Composable
fun Modifier.accessibilityToggle(label: String, checked: Boolean, enabled: Boolean = true): Modifier {
    val state = when {
        !enabled -> stringResource(R.string.a11y_state_disabled)
        checked -> stringResource(R.string.a11y_state_on)
        else -> stringResource(R.string.a11y_state_off)
    }
    return semantics(mergeDescendants = true) {
        role = Role.Switch
        contentDescription = label
        stateDescription = state
        if (!enabled) disabled()
    }
}

@Composable
fun Modifier.accessibilitySelection(label: String, selected: Boolean): Modifier {
    val state = stringResource(
        if (selected) R.string.label_selected else R.string.label_not_selected
    )
    return semantics {
        role = Role.Tab
        contentDescription = label
        stateDescription = state
    }
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
@Composable
fun Modifier.accessibilityRadio(label: String, selected: Boolean): Modifier {
    val state = stringResource(
        if (selected) R.string.label_selected else R.string.label_not_selected
    )
    return semantics(mergeDescendants = true) {
        role = Role.RadioButton
        contentDescription = label
        stateDescription = state
    }
}

fun Modifier.accessibilityLiveRegion(label: String): Modifier =
    semantics {
        contentDescription = label
        liveRegion = LiveRegionMode.Polite
    }
