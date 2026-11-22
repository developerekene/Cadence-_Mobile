package com.devekene.kalenda.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val encryptedTitle: String,
    val encryptedDescription: String,
    val encryptedLocation: String,
    val category: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val isAllDay: Boolean,
    val reminderMinutesBefore: Int,
    val recurrenceRule: String,
    val syncStatus: String,
    val updatedAtMillis: Long,
    val isDeleted: Boolean = false,
    val isBiometricLocked: Boolean = false,
    val googleCalendarEventId: Long? = null
)
