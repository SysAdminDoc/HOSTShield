package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val ds get() = context.hostShieldDataStore

    internal object Keys {
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val HIGH_CONTRAST_AMOLED = booleanPreferencesKey("high_contrast_amoled")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val PINNED_DOMAINS = stringPreferencesKey("pinned_domains")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    val accentColor: Flow<String> = ds.data.map { it[Keys.ACCENT_COLOR] ?: "teal" }
    suspend fun setAccentColor(color: String) = ds.edit { it[Keys.ACCENT_COLOR] = color }

    val highContrastAmoled: Flow<Boolean> = ds.data.map { it[Keys.HIGH_CONTRAST_AMOLED] ?: false }
    suspend fun setHighContrastAmoled(enabled: Boolean) = ds.edit { it[Keys.HIGH_CONTRAST_AMOLED] = enabled }

    val showNotification: Flow<Boolean> = ds.data.map { it[Keys.SHOW_NOTIFICATION] ?: true }
    suspend fun setShowNotification(show: Boolean) = ds.edit { it[Keys.SHOW_NOTIFICATION] = show }

    val pinnedDomains: Flow<Set<String>> = ds.data.map {
        (it[Keys.PINNED_DOMAINS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
    }
    suspend fun setPinnedDomains(domains: Set<String>) = ds.edit {
        it[Keys.PINNED_DOMAINS] = domains.joinToString(",")
    }
    suspend fun pinDomain(domain: String) {
        val current = pinnedDomains.first()
        setPinnedDomains(current + domain.lowercase())
    }
    suspend fun unpinDomain(domain: String) {
        val current = pinnedDomains.first()
        setPinnedDomains(current - domain.lowercase())
    }

    // Search history
    val searchHistory: Flow<List<String>> = ds.data.map {
        (it[Keys.SEARCH_HISTORY] ?: "").split("\n").filter { s -> s.isNotBlank() }
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
}
