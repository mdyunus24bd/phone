package com.example.data.local

import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.TypeConverter

/**
 * Strongly typed network origin for each call history record (SIM cellular).
 */
enum class CallType {
    SIM;

    companion object {
        fun fromRaw(raw: String?): CallType {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SIM
        }
    }
}

/**
 * Strongly typed call direction / disposition.
 */
enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED,
    BLOCKED;

    companion object {
        fun fromRaw(raw: String?): CallDirection {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OUTGOING
        }
    }
}

/**
 * Snapshot of contact information embedded directly in each call history row,
 * with an optional foreign-key link ([contactId]) to [ContactEntity].
 */
data class CallContactInfo(
    val contactId: Long? = null,
    val name: String = "",
    val phoneNumber: String = "",
    val label: String = ""
)

/**
 * Relational projection combining a [CallLogEntity] with its live [ContactEntity] record.
 */
data class CallHistoryWithContact(
    @Embedded val call: CallLogEntity,
    @Relation(
        parentColumn = "contact_contactId",
        entityColumn = "id"
    )
    val linkedContact: ContactEntity?
)

/**
 * Aggregated call history analytics grouped by [CallType].
 */
data class CallTypeSummary(
    val callType: CallType,
    val totalCalls: Int,
    val totalDurationSec: Long,
    val averageMos: Float
)

/**
 * Room type converters for strongly typed call history enums.
 */
class CallHistoryTypeConverters {
    @TypeConverter
    fun fromCallType(value: CallType): String = value.name

    @TypeConverter
    fun toCallType(raw: String?): CallType = CallType.fromRaw(raw)

    @TypeConverter
    fun fromCallDirection(value: CallDirection): String = value.name

    @TypeConverter
    fun toCallDirection(raw: String?): CallDirection = CallDirection.fromRaw(raw)
}
