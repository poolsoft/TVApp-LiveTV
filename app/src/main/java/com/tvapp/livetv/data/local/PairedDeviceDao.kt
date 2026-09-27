package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PairedDeviceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(device: PairedDeviceEntity): Long

    @Query("SELECT * FROM paired_devices ORDER BY id")
    suspend fun devices(): List<PairedDeviceEntity>

    @Query("SELECT * FROM paired_devices WHERE tokenHash = :tokenHash LIMIT 1")
    suspend fun byTokenHash(tokenHash: String): PairedDeviceEntity?

    @Query("UPDATE paired_devices SET lastSeenAt = :seenAt WHERE tokenHash = :tokenHash")
    suspend fun touch(tokenHash: String, seenAt: Long)

    @Query("DELETE FROM paired_devices WHERE id = :deviceId")
    suspend fun delete(deviceId: Long): Int
}
