package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for querying, filtering, aggregating, and managing SIM call history.
 */
@Dao
interface CallHistoryDao {

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Transaction
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun observeAllWithContact(): Flow<List<CallHistoryWithContact>>

    @Query("SELECT * FROM call_logs WHERE callType = :callType ORDER BY timestamp DESC")
    fun observeByCallType(callType: CallType): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE direction = :direction ORDER BY timestamp DESC")
    fun observeByDirection(direction: CallDirection): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE contact_contactId = :contactId ORDER BY timestamp DESC")
    fun observeByContactId(contactId: Long): Flow<List<CallLogEntity>>

    @Query(
        """
        SELECT * FROM call_logs
        WHERE phoneNumber LIKE '%' || :query || '%'
           OR displayName LIKE '%' || :query || '%'
           OR contact_name LIKE '%' || :query || '%'
           OR routeLabel LIKE '%' || :query || '%'
           OR quickNote LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        """
    )
    fun searchHistory(query: String): Flow<List<CallLogEntity>>

    @Query(
        """
        SELECT callType AS callType,
               COUNT(*) AS totalCalls,
               COALESCE(SUM(durationSec), 0) AS totalDurationSec,
               COALESCE(AVG(CASE WHEN mosScore > 0 THEN mosScore ELSE NULL END), 0.0) AS averageMos
        FROM call_logs
        GROUP BY callType
        """
    )
    fun observeSummaryByCallType(): Flow<List<CallTypeSummary>>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    suspend fun getAllSnapshot(): List<CallLogEntity>

    @Query("SELECT * FROM call_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CallLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CallLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CallLogEntity>)

    @Update
    suspend fun update(entry: CallLogEntity)

    @Query("UPDATE call_logs SET quickNote = :note WHERE id = :id")
    suspend fun updateQuickNote(id: Long, note: String)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM call_logs")
    suspend fun clearAll()
}
