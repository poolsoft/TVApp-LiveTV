package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ChannelDao {
    @Query("SELECT * FROM user_channels")
    suspend fun getAllChannels(): List<UserChannelEntity>

    @Query("SELECT * FROM user_channels ORDER BY sortOrder")
    suspend fun getOrderedChannels(): List<UserChannelEntity>

    @Query("SELECT MAX(sortOrder) FROM user_channels")
    suspend fun maxSortOrder(): Int?

    @Upsert
    suspend fun upsertChannels(channels: List<UserChannelEntity>)

    @Query("DELETE FROM user_channels")
    suspend fun deleteAllChannels()

    @Query("UPDATE user_channels SET customNumber = :number WHERE sourceKey = :sourceKey")
    suspend fun setCustomNumber(sourceKey: String, number: Int?)

    @Query("UPDATE user_channels SET customName = :name WHERE sourceKey = :sourceKey")
    suspend fun setCustomName(sourceKey: String, name: String?)

    @Query("UPDATE user_channels SET favorite = :favorite WHERE sourceKey = :sourceKey")
    suspend fun setFavorite(sourceKey: String, favorite: Boolean)

    @Query("UPDATE user_channels SET hidden = :hidden WHERE sourceKey = :sourceKey")
    suspend fun setHidden(sourceKey: String, hidden: Boolean)

    @Query("UPDATE user_channels SET groupId = :groupId WHERE sourceKey = :sourceKey")
    suspend fun setGroup(sourceKey: String, groupId: Long?)

    @Query("UPDATE user_channels SET sortOrder = :sortOrder WHERE sourceKey = :sourceKey")
    suspend fun setSortOrder(sourceKey: String, sortOrder: Int)

    @Query("SELECT * FROM channel_groups ORDER BY sortOrder, name")
    suspend fun getGroups(): List<ChannelGroupEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: ChannelGroupEntity): Long

    @Upsert
    suspend fun upsertGroups(groups: List<ChannelGroupEntity>)

    @Query("DELETE FROM channel_groups")
    suspend fun deleteAllGroups()

    @Delete
    suspend fun deleteGroup(group: ChannelGroupEntity)
}
