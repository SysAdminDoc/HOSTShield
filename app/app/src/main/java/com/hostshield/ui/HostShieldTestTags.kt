package com.hostshield.ui

/**
 * Stable Compose semantics tags for high-value flows covered by instrumented UI tests.
 *
 * These are intentionally narrow: production UI remains text/accessibility-first, while
 * tests avoid brittle icon order and duplicated labels.
 */
object HostShieldTestTags {
    object Home {
        const val ShieldOrb = "home:shield_orb"
    }

    object Nav {
        fun route(route: String): String = "nav:${slug(route)}"
        fun layout(layout: String): String = "nav:layout:${slug(layout)}"
    }

    object Sources {
        const val AddButton = "sources:add_button"
        const val NameField = "sources:name_field"
        const val UrlField = "sources:url_field"
        const val ConfirmAddButton = "sources:confirm_add"
    }

    object Rules {
        const val AddButton = "rules:add_button"
        const val HostnameField = "rules:hostname_field"
        const val ConfirmAddButton = "rules:confirm_add"
    }

    object Logs {
        const val SearchField = "logs:search_field"
    }

    object DenseList {
        const val SaveFilter = "dense_list:save_filter"
        const val ClearSavedFilters = "dense_list:clear_saved_filters"
        fun savedFilter(screen: String, label: String): String =
            "dense_list:saved:${slug(screen)}:${slug(label)}"
        fun jump(screen: String, target: String): String =
            "dense_list:jump:${slug(screen)}:${slug(target)}"
    }

    object Settings {
        fun section(title: String): String = "settings:section:${slug(title)}"
        fun row(title: String): String = "settings:row:${slug(title)}"
        fun toggle(title: String): String = "settings:toggle:${slug(title)}"
    }

    object Parental {
        const val EnableToggle = "parental:enable_toggle"
        const val PinField = "parental:pin_field"
        const val SetPinButton = "parental:set_pin"
        const val DialogPinField = "parental:dialog_pin"
        const val DialogConfirmButton = "parental:dialog_confirm"
    }

    private fun slug(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
}
