package com.hostshield.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hostshield.data.database.HostShieldDatabase
import com.hostshield.data.database.Migrations
import com.hostshield.data.model.AutomationAuditEntry
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.BlockingProfile
import com.hostshield.data.preferences.hostShieldDataStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomationReceiverConnectedTest {

    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val customDnsKey = stringPreferencesKey("custom_upstream_dns")
    private val enabledKey = booleanPreferencesKey("is_enabled")
    private val blockMethodKey = stringPreferencesKey("block_method")
    private val firstLaunchKey = booleanPreferencesKey("first_launch")

    @Before
    fun resetState() {
        AutomationReceiver.clearRateLimitsForTest()
        runBlocking {
            targetContext.hostShieldDataStore.edit { prefs ->
                prefs.clear()
                prefs[firstLaunchKey] = false
                prefs[blockMethodKey] = BlockMethod.DISABLED.name
                prefs[enabledKey] = false
                prefs[customDnsKey] = ""
            }
            clearAutomationTables()
        }
    }

    @After
    fun cleanup() {
        AutomationReceiver.clearRateLimitsForTest()
        runBlocking {
            clearAutomationTables()
            targetContext.hostShieldDataStore.edit { prefs ->
                prefs[enabledKey] = false
                prefs[customDnsKey] = ""
            }
        }
    }

    @Test
    fun statusBroadcastReturnsCurrentStateAndAuditsOk() {
        val latch = CountDownLatch(1)
        var result: Intent? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                result = intent
                latch.countDown()
            }
        }

        registerStatusReceiver(receiver)
        try {
            sendAutomationBroadcast(AutomationActionContract.ACTION_STATUS)

            assertTrue("STATUS_RESULT was not delivered", latch.await(10, TimeUnit.SECONDS))
            val status = requireNotNull(result)
            assertFalse(status.getBooleanExtra("enabled", true))
            assertEquals(BlockMethod.DISABLED.name, status.getStringExtra("method"))
            assertNotNull(status.getStringExtra("version"))
            runBlocking {
                waitForAudit(AutomationActionContract.ACTION_STATUS, "OK")
            }
        } finally {
            targetContext.unregisterReceiver(receiver)
        }
    }

    @Test
    fun setDnsUpdatesPreferenceAndImmediateRepeatIsRateLimited() = runBlocking {
        sendAutomationBroadcast(AutomationActionContract.ACTION_SET_DNS) {
            putExtra(AutomationActionContract.EXTRA_DNS_SERVERS, "1.1.1.1,9.9.9.9")
        }
        waitForPreference(customDnsKey, "1.1.1.1,9.9.9.9")
        waitForAudit(AutomationActionContract.ACTION_SET_DNS, "OK")

        sendAutomationBroadcast(AutomationActionContract.ACTION_SET_DNS) {
            putExtra(AutomationActionContract.EXTRA_DNS_SERVERS, "8.8.8.8")
        }

        waitForAudit(AutomationActionContract.ACTION_SET_DNS, "RATE_LIMITED")
        assertEquals("1.1.1.1,9.9.9.9", currentString(customDnsKey))
    }

    @Test
    fun setProfileActivatesProfileByCaseInsensitiveName() {
        runBlocking {
            val profileName = "Travel Mode ${System.currentTimeMillis()}"
            val db = openDb()
            try {
                db.profileDao().insert(BlockingProfile(name = profileName, isActive = false))
            } finally {
                db.close()
            }

            sendAutomationBroadcast(AutomationActionContract.ACTION_SET_PROFILE) {
                putExtra(AutomationActionContract.EXTRA_PROFILE_NAME, profileName.lowercase())
            }

            withTimeout(10_000) {
                while (activeProfileName() != profileName) {
                    delay(100)
                }
            }
            waitForAudit(AutomationActionContract.ACTION_SET_PROFILE, "OK")
        }
    }

    @Test
    fun legacyPauseZeroExtraResumesImmediatelyAndAuditsCanonicalAction() {
        runBlocking {
            sendAutomationBroadcast("com.hostshield.action.PAUSE") {
                putExtra(AutomationActionContract.LEGACY_EXTRA_PAUSE_MINUTES, 0)
            }

            waitForPreference(enabledKey, true)
            waitForAudit(AutomationActionContract.ACTION_PAUSE, "OK")
        }
    }

    @Test
    fun manifestRequiresAutomationPermissionForExternalBroadcasts() {
        val component = ComponentName(targetContext.packageName, AutomationReceiver::class.java.name)
        val receiverInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            targetContext.packageManager.getReceiverInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            targetContext.packageManager.getReceiverInfo(component, 0)
        }

        assertEquals("${targetContext.packageName}.permission.AUTOMATION", receiverInfo.permission)
    }

    private fun sendAutomationBroadcast(
        action: String,
        configure: Intent.() -> Unit = {},
    ) {
        targetContext.sendBroadcast(automationIntent(action).apply(configure))
    }

    private fun automationIntent(action: String): Intent =
        Intent(action).apply {
            component = ComponentName(targetContext.packageName, AutomationReceiver::class.java.name)
        }

    private fun registerStatusReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter(AutomationActionContract.STATUS_RESULT)
        val permission = "${targetContext.packageName}.permission.AUTOMATION"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            targetContext.registerReceiver(
                receiver,
                filter,
                permission,
                null,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            targetContext.registerReceiver(receiver, filter, permission, null)
        }
    }

    private suspend fun waitForAudit(action: String, result: String): AutomationAuditEntry =
        withTimeout(10_000) {
            var match: AutomationAuditEntry? = null
            while (match == null) {
                val db = openDb()
                try {
                    match = db.automationAuditDao().getRecent(50).first()
                        .firstOrNull { it.action == action && it.result == result }
                } finally {
                    db.close()
                }
                if (match == null) delay(100)
            }
            match
        }

    private suspend fun clearAutomationTables() {
        val db = openDb()
        try {
            db.openHelper.writableDatabase.execSQL("DELETE FROM automation_audit_log")
            db.openHelper.writableDatabase.execSQL("DELETE FROM profiles")
        } finally {
            db.close()
        }
    }

    private suspend fun activeProfileName(): String? {
        val db = openDb()
        return try {
            db.profileDao().getActiveProfile()?.name
        } finally {
            db.close()
        }
    }

    private suspend fun <T> waitForPreference(
        key: androidx.datastore.preferences.core.Preferences.Key<T>,
        expected: T,
    ) {
        withTimeout(10_000) {
            while (targetContext.hostShieldDataStore.data.first()[key] != expected) {
                delay(100)
            }
        }
    }

    private suspend fun currentString(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
    ): String = targetContext.hostShieldDataStore.data.first()[key] ?: ""

    private fun openDb(): HostShieldDatabase =
        Room.databaseBuilder(targetContext, HostShieldDatabase::class.java, "hostshield.db")
            .addMigrations(*Migrations.ALL)
            .build()
}
