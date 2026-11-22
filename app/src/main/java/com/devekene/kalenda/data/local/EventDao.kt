package com.devekene.kalenda.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE isDeleted = 0 ORDER BY startTimeMillis ASC")
    fun observeAllActiveEvents(): Flow<List<EventEntity>>

    @Query(
        "SELECT * FROM events WHERE isDeleted = 0 AND endTimeMillis >= :rangeStart AND startTimeMillis <= :rangeEnd ORDER BY startTimeMillis ASC"
    )
    fun observeEventsInRange(rangeStart: Long, rangeEnd: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE isDeleted = 0 ORDER BY startTimeMillis ASC")
    suspend fun getAllActiveEventsSync(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getEventById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE googleCalendarEventId = :googleId AND isDeleted = 0 LIMIT 1")
    suspend fun getEventByGoogleId(googleId: Long): EventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<EventEntity>)

    @Query("UPDATE events SET isDeleted = 1, syncStatus = :syncStatus, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun softDeleteEvent(id: String, syncStatus: String, updatedAt: Long)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun hardDeleteEvent(id: String)
}
