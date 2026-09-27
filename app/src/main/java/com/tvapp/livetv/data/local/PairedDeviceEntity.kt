package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * REMOTEEDIT: a device that completed the pairing handshake. The plain bearer
 * token is never stored — only its SHA-256 hash — and neither the token nor
 * the pairing code is ever logged.
 */
@Entity(
    tableName = "paired_devices",
    // Must exactly match the unique index MIGRATION_24_25 creates, or Room's
    // post-migration schema validation rejects the database.
    indices = [Index(value = ["tokenHash"], unique = true)],
)
data class PairedDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceName: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val tokenHash: String,
)
