package com.hostshield.data.database

import androidx.room.TypeConverter
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth

class Converters {
    @TypeConverter fun fromSourceCategory(value: SourceCategory): String = value.name
    @TypeConverter fun toSourceCategory(value: String): SourceCategory = SourceCategory.valueOf(value)
    @TypeConverter fun fromRuleType(value: RuleType): String = value.name
    @TypeConverter fun toRuleType(value: String): RuleType = RuleType.valueOf(value)
    @TypeConverter fun fromSourceHealth(value: SourceHealth): String = value.name
    @TypeConverter fun toSourceHealth(value: String): SourceHealth = try { SourceHealth.valueOf(value) } catch (_: Exception) { SourceHealth.UNKNOWN }
}
