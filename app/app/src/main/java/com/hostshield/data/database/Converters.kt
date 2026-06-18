package com.hostshield.data.database

import android.util.Log
import androidx.room.TypeConverter
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth

class Converters {
    @TypeConverter fun fromSourceCategory(value: SourceCategory): String = value.name
    @TypeConverter fun toSourceCategory(value: String): SourceCategory =
        try { SourceCategory.valueOf(value) } catch (_: Exception) { fallback("SourceCategory", value, SourceCategory.CUSTOM) }
    @TypeConverter fun fromRuleType(value: RuleType): String = value.name
    @TypeConverter fun toRuleType(value: String): RuleType =
        try { RuleType.valueOf(value) } catch (_: Exception) { fallback("RuleType", value, RuleType.BLOCK) }
    @TypeConverter fun fromSourceHealth(value: SourceHealth): String = value.name
    @TypeConverter fun toSourceHealth(value: String): SourceHealth =
        try { SourceHealth.valueOf(value) } catch (_: Exception) { fallback("SourceHealth", value, SourceHealth.UNKNOWN) }

    private fun <T> fallback(typeName: String, rawValue: String, fallback: T): T {
        val safeValue = rawValue.take(MAX_LOG_VALUE_CHARS)
        Log.w(TAG, "Unknown $typeName '$safeValue'; using $fallback")
        return fallback
    }

    private companion object {
        const val TAG = "RoomConverters"
        const val MAX_LOG_VALUE_CHARS = 80
    }
}
