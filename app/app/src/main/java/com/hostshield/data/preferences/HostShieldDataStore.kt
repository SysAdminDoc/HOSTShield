package com.hostshield.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore instance for all HostShield preferences.
 *
 * DataStore enforces one instance per file name — this extension property
 * is the sole owner. All domain preference classes share it via Hilt injection.
 *
 * A corruption handler resets the file to empty preferences instead of throwing
 * `CorruptionException` on every read: without it, a truncated/corrupted
 * `preferences_pb` (interrupted write, storage fault) would crash-loop every
 * preference consumer, worker, and ViewModel until the user cleared app data.
 */
internal val Context.hostShieldDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hostshield_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.e("HostShieldDataStore", "Preferences store corrupted; resetting to defaults", it)
        emptyPreferences()
    }
)
