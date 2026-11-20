package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val phoneNumber: String = "",
    val label: String = "Mobile",
    val favorite: Boolean = false,
    val encryptedNotes: String = "",
    val photoUri: String = "",
    val googleAccountName: String = "",
    val deviceContactId: Long = 0L,
    val avatarColorIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity storing SIM call history records with call type, duration,
 * timestamp, embedded contact info snapshot, optional FK to [ContactEntity],
 * call quality telemetry, and recording/spam metadata.
 */
@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["callType"]),
        Index(value = ["direction"]),
        Index(value = ["phoneNumber"]),
        Index(value = ["contact_contactId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contact_contactId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val phoneNumber: String,
    val displayName: String = "",
    val callType: CallType = CallType.SIM,
    val routeLabel: String = "",
    val direction: CallDirection = CallDirection.OUTGOING,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSec: Long = 0L,
    @Embedded(prefix = "contact_") val contactInfo: CallContactInfo = CallContactInfo(),
    val mosScore: Float = 0f,
    val avgLatencyMs: Int = 0,
    val avgJitterMs: Int = 0,
    val packetLossPct: Float = 0f,
    val codec: String = "",
    val encryptedTransport: Boolean = false,
    val spamScore: Int = 0,
    val spamReason: String = "",
    val recordingId: Long? = null,
    val deviceTag: String = "",
    val quickNote: String = ""
) {
    @get:Ignore
    val networkType: String
        get() = callType.name

    @get:Ignore
    val resolvedContactName: String
        get() = contactInfo.name.ifBlank { displayName }
}

@Entity(tableName = "forwarding_rules")
data class CallForwardingRuleEntity(
    @PrimaryKey val conditionKey: String, // "ALWAYS", "BUSY", "UNANSWERED", "UNREACHABLE"
    val title: String,
    val targetDestination: String = "",
    val networkType: String = "SIM",
    val enabled: Boolean = false,
    val activatePrefix: String,
    val deactivateCode: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "spam_rules")
data class SpamRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pattern: String,
    val label: String,
    val riskScore: Int = 95,
    val autoBlock: Boolean = true,
    val matchCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "call_recordings")
data class CallRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val phoneNumber: String,
    val displayName: String = "",
    val networkType: String = "SIM",
    val fileName: String,
    val storageUri: String = "",
    val storagePath: String = "",
    val autoRecorded: Boolean = false,
    val durationSec: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val encryptedAtRest: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
