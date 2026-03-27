package com.hostshield.util

import com.hostshield.data.database.ProfileDao
import com.hostshield.data.model.BlockingProfile
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v1.7.0 — Named Schedule Presets (Roadmap #35)
// Pre-built schedule templates for common blocking profiles:
// Focus, Sleep, Family, Work, Kids modes.

data class SchedulePreset(
    val name: String,
    val description: String,
    val scheduleStart: String,      // HH:mm
    val scheduleEnd: String,        // HH:mm
    val daysOfWeek: String,         // 0-6 comma-separated (0=Sunday)
    val sourceCategories: List<String>  // ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO
)

@Singleton
class SchedulePresets @Inject constructor(
    private val profileDao: ProfileDao
) {

    companion object {
        private val WEEKDAYS = "1,2,3,4,5"
        private val ALL_DAYS = "0,1,2,3,4,5,6"

        private val BUILT_IN_PRESETS = listOf(
            SchedulePreset(
                name = "Focus Mode",
                description = "Block distractions during work hours on weekdays",
                scheduleStart = "09:00",
                scheduleEnd = "17:00",
                daysOfWeek = WEEKDAYS,
                sourceCategories = listOf("ADS", "TRACKERS", "SOCIAL")
            ),
            SchedulePreset(
                name = "Sleep Mode",
                description = "Reduce screen temptation and block harmful content overnight",
                scheduleStart = "22:00",
                scheduleEnd = "07:00",
                daysOfWeek = ALL_DAYS,
                sourceCategories = listOf("ADS", "TRACKERS", "ADULT", "SOCIAL")
            ),
            SchedulePreset(
                name = "Family Mode",
                description = "All-day protection against ads, trackers, malware, and adult content",
                scheduleStart = "00:00",
                scheduleEnd = "23:59",
                daysOfWeek = ALL_DAYS,
                sourceCategories = listOf("ADS", "TRACKERS", "MALWARE", "ADULT")
            ),
            SchedulePreset(
                name = "Work Mode",
                description = "Strict productivity blocking during business hours on weekdays",
                scheduleStart = "08:00",
                scheduleEnd = "18:00",
                daysOfWeek = WEEKDAYS,
                sourceCategories = listOf("ADS", "TRACKERS", "SOCIAL", "CRYPTO")
            ),
            SchedulePreset(
                name = "Kids Mode",
                description = "Maximum protection for children — blocks all harmful categories",
                scheduleStart = "06:00",
                scheduleEnd = "21:00",
                daysOfWeek = ALL_DAYS,
                sourceCategories = listOf("ADS", "TRACKERS", "MALWARE", "ADULT", "SOCIAL", "CRYPTO")
            )
        )
    }

    /**
     * Returns all built-in schedule presets.
     */
    fun getPresets(): List<SchedulePreset> = BUILT_IN_PRESETS

    /**
     * Creates and inserts a new [BlockingProfile] from the given preset.
     *
     * @param preset  The schedule preset template to apply.
     * @param sourceIds Comma-separated host-source IDs that match the preset's
     *                  [SchedulePreset.sourceCategories]. The caller is responsible
     *                  for resolving category names to actual source IDs.
     * @return The newly created [BlockingProfile] with its generated database ID.
     */
    suspend fun applyPreset(preset: SchedulePreset, sourceIds: String): BlockingProfile {
        val profile = BlockingProfile(
            name = preset.name,
            isActive = false,
            sourceIds = sourceIds,
            scheduleStart = preset.scheduleStart,
            scheduleEnd = preset.scheduleEnd,
            daysOfWeek = preset.daysOfWeek
        )
        val id = profileDao.insert(profile)
        return profile.copy(id = id)
    }
}
