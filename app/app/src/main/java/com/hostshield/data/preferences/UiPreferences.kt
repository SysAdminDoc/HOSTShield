package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SavedDenseListFilter(
    val screen: String,
    val label: String,
    val payload: String,
    val updatedAt: Long
)

@Singleton
class UiPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val ds get() = context.hostShieldDataStore
    private val maxSavedFiltersPerScreen = 8

    internal object Keys {
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val HIGH_CONTRAST_AMOLED = booleanPreferencesKey("high_contrast_amoled")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val PINNED_DOMAINS = stringPreferencesKey("pinned_domains")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val SAVED_DENSE_LIST_FILTERS = stringPreferencesKey("saved_dense_list_filters")
    }

    val accentColor: Flow<String> = ds.data.map { it[Keys.ACCENT_COLOR] ?: "teal" }
    suspend fun setAccentColor(color: String) = ds.edit { it[Keys.ACCENT_COLOR] = color }

    val highContrastAmoled: Flow<Boolean> = ds.data.map { it[Keys.HIGH_CONTRAST_AMOLED] ?: false }
    suspend fun setHighContrastAmoled(enabled: Boolean) = ds.edit { it[Keys.HIGH_CONTRAST_AMOLED] = enabled }

    val dynamicColor: Flow<Boolean> = ds.data.map { it[Keys.DYNAMIC_COLOR] ?: false }
    suspend fun setDynamicColor(enabled: Boolean) = ds.edit { it[Keys.DYNAMIC_COLOR] = enabled }

    val themeMode: Flow<String> = ds.data.map { it[Keys.THEME_MODE] ?: "dark" }
    suspend fun setThemeMode(mode: String) = ds.edit { it[Keys.THEME_MODE] = mode }

    val showNotification: Flow<Boolean> = ds.data.map { it[Keys.SHOW_NOTIFICATION] ?: true }
    suspend fun setShowNotification(show: Boolean) = ds.edit { it[Keys.SHOW_NOTIFICATION] = show }

    val pinnedDomains: Flow<Set<String>> = ds.data.map {
        (it[Keys.PINNED_DOMAINS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
    }
    suspend fun setPinnedDomains(domains: Set<String>) = ds.edit {
        it[Keys.PINNED_DOMAINS] = domains.joinToString(",")
    }
    // Mutate inside a single ds.edit {} (like addSearchQuery) — a read-then-write via
    // pinnedDomains.first() + setPinnedDomains() is non-atomic and can drop a
    // concurrent pin/unpin.
    suspend fun pinDomain(domain: String) = ds.edit {
        val current = (it[Keys.PINNED_DOMAINS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
        it[Keys.PINNED_DOMAINS] = (current + domain.lowercase()).joinToString(",")
    }
    suspend fun unpinDomain(domain: String) = ds.edit {
        val current = (it[Keys.PINNED_DOMAINS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
        it[Keys.PINNED_DOMAINS] = (current - domain.lowercase()).joinToString(",")
    }

    // Search history
    val searchHistory: Flow<List<String>> = ds.data.map {
        (it[Keys.SEARCH_HISTORY] ?: "").split("\n").filter { s -> s.isNotBlank() }
    }

    suspend fun setSearchHistory(queries: List<String>) = ds.edit {
        val normalized = queries
            .map { query -> query.trim().lowercase() }
            .filter { query -> query.length >= 2 }
            .distinct()
            .take(10)
        it[Keys.SEARCH_HISTORY] = normalized.joinToString("\n")
    }

    suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim().lowercase()
        if (trimmed.length < 2) return
        ds.edit {
            val current = (it[Keys.SEARCH_HISTORY] ?: "").split("\n").filter { s -> s.isNotBlank() }
            val updated = (listOf(trimmed) + current.filter { s -> s != trimmed }).take(10)
            it[Keys.SEARCH_HISTORY] = updated.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() = ds.edit { it[Keys.SEARCH_HISTORY] = "" }

    fun savedDenseListFilters(screen: String): Flow<List<SavedDenseListFilter>> = ds.data.map { prefs ->
        decodeSavedFilters(prefs[Keys.SAVED_DENSE_LIST_FILTERS])
            .filter { it.screen == screen }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun saveDenseListFilter(screen: String, label: String, payload: String) {
        val normalizedScreen = screen.trim()
        val normalizedLabel = label.trim().lineSequence().firstOrNull().orEmpty().take(64)
        val normalizedPayload = payload.trim()
        if (normalizedScreen.isBlank() || normalizedLabel.isBlank() || normalizedPayload.isBlank()) return

        ds.edit { prefs ->
            val next = SavedDenseListFilter(
                screen = normalizedScreen,
                label = normalizedLabel,
                payload = normalizedPayload,
                updatedAt = System.currentTimeMillis()
            )
            val existing = decodeSavedFilters(prefs[Keys.SAVED_DENSE_LIST_FILTERS])
                .filterNot { it.screen == normalizedScreen && it.payload == normalizedPayload }
            val currentScreen = (listOf(next) + existing.filter { it.screen == normalizedScreen })
                .sortedByDescending { it.updatedAt }
                .take(maxSavedFiltersPerScreen)
            val otherScreens = existing.filter { it.screen != normalizedScreen }
            prefs[Keys.SAVED_DENSE_LIST_FILTERS] = encodeSavedFilters(otherScreens + currentScreen)
        }
    }

    suspend fun clearDenseListFilters(screen: String) = ds.edit { prefs ->
        val remaining = decodeSavedFilters(prefs[Keys.SAVED_DENSE_LIST_FILTERS])
            .filterNot { it.screen == screen }
        prefs[Keys.SAVED_DENSE_LIST_FILTERS] = encodeSavedFilters(remaining)
    }

    private fun decodeSavedFilters(raw: String?): List<SavedDenseListFilter> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val screen = item.optString("screen").trim()
                    val label = item.optString("label").trim()
                    val payload = item.optString("payload").trim()
                    if (screen.isBlank() || label.isBlank() || payload.isBlank()) continue
                    add(
                        SavedDenseListFilter(
                            screen = screen,
                            label = label,
                            payload = payload,
                            updatedAt = item.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun encodeSavedFilters(filters: List<SavedDenseListFilter>): String {
        val json = JSONArray()
        filters.sortedWith(compareBy<SavedDenseListFilter> { it.screen }.thenByDescending { it.updatedAt })
            .forEach { filter ->
                json.put(
                    JSONObject()
                        .put("screen", filter.screen)
                        .put("label", filter.label)
                        .put("payload", filter.payload)
                        .put("updatedAt", filter.updatedAt)
                )
            }
        return json.toString()
    }
}
