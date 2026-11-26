package ru.landilf.memeorganizer.data.storage.entities.logging

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "light_log_events", indices = [Index("timestampMs")])
data class LightLogEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val recordType: String,
    val payloadJson: String
)
