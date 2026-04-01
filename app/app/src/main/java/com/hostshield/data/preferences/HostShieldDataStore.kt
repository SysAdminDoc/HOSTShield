package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore instance for all HostShield preferences.
 *
 * DataStore enforces one instance per file name — this extension property
 * is the sole owner. All domain preference classes share it via Hilt injection.
 */
internal val Context.hostShieldDataStore: DataStore<Preferences> by preferencesDataStore(name = "hostshield_prefs")
