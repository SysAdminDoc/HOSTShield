package com.hostshield.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserRuleDaoExpiryRobolectricTest {
    private lateinit var database: HostShieldDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HostShieldDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enabled rule queries exclude expired entries and reconciliation disables them`() = runBlocking {
        val now = 1_000_000L
        database.userRuleDao().insertAll(
            listOf(
                UserRule(
                    hostname = "active.example",
                    type = RuleType.BLOCK,
                    expiresAt = now + 1_000L,
                ),
                UserRule(
                    hostname = "expired.example",
                    type = RuleType.BLOCK,
                    expiresAt = now,
                ),
                UserRule(
                    hostname = "never.example",
                    type = RuleType.BLOCK,
                ),
            )
        )

        assertEquals(
            listOf("active.example", "never.example"),
            database.userRuleDao().getEnabledByType(RuleType.BLOCK, now)
                .map { it.hostname },
        )
        assertEquals(1, database.userRuleDao().disableExpired(now))
        assertFalse(
            database.userRuleDao().getAllRulesList()
                .single { it.hostname == "expired.example" }
                .enabled
        )
    }
}
