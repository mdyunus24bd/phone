package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY favorite DESC, displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun getAllSnapshot(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :number LIMIT 1")
    suspend fun findByAddress(number: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<ContactEntity>)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("DELETE FROM contacts")
    suspend fun clearAll()
}

@Dao
interface ForwardingRuleDao {
    @Query("SELECT * FROM forwarding_rules ORDER BY conditionKey ASC")
    fun observeAll(): Flow<List<CallForwardingRuleEntity>>

    @Query("SELECT COUNT(*) FROM forwarding_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CallForwardingRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<CallForwardingRuleEntity>)

    @Query("UPDATE forwarding_rules SET enabled = 0, updatedAt = :ts")
    suspend fun disableAll(ts: Long)
}

@Dao
interface SpamRuleDao {
    @Query("SELECT * FROM spam_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SpamRuleEntity>>

    @Query("SELECT * FROM spam_rules")
    suspend fun getAllSnapshot(): List<SpamRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: SpamRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<SpamRuleEntity>)

    @Query("UPDATE spam_rules SET matchCount = matchCount + 1 WHERE id = :id")
    suspend fun incrementMatches(id: Long)

    @Delete
    suspend fun delete(rule: SpamRuleEntity)

    @Query("DELETE FROM spam_rules WHERE autoBlock = 1")
    suspend fun deleteAllAutoBlocked()

    @Query("DELETE FROM spam_rules")
    suspend fun clearAll()
}

@Dao
interface CallRecordingDao {
    @Query("SELECT * FROM call_recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CallRecordingEntity>>

    @Query("SELECT * FROM call_recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CallRecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: CallRecordingEntity): Long

    @Delete
    suspend fun delete(recording: CallRecordingEntity)

    @Query("DELETE FROM call_recordings")
    suspend fun clearAll()
}
