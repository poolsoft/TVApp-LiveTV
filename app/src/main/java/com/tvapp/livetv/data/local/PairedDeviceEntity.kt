package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * REMOTEEDIT: a device that completed the pairing handshake. The plain bearer
 * token is never stored — only its SHA-256 hash — and neither the token nor
 * the pairing code is ever logged.
 */
@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceName: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val tokenHash: String,
)
