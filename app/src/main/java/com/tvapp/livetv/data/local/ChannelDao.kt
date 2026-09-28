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

    @Query("SELECT * FROM user_channels WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getChannel(sourceKey: String): UserChannelEntity?

    @Query("SELECT MAX(sortOrder) FROM user_channels")
    suspend fun maxSortOrder(): Int?

    /** REMOTEEDIT: revision-aware read for conflict checks. */
    @Query("SELECT revision FROM user_channels WHERE sourceKey = :sourceKey")
    suspend fun revisionOf(sourceKey: String): Long?

    /** REMOTEEDIT: batch revision lookup for remote paging — one IN query
     *  instead of one query per channel row (pages hold hundreds of keys). */
    @Query("SELECT sourceKey, revision FROM user_channels WHERE sourceKey IN (:keys)")
    suspend fun revisionsOf(keys: Collection<String>): List<SourceKeyRevision>

    /** REMOTEEDIT: revision-aware favorites toggle used by PATCH. */
    @Query(
        "UPDATE user_channels SET favorite = :favorite, revision = revision + 1 " +
            "WHERE sourceKey = :sourceKey",
    )
    suspend fun setFavoriteBumpingRevision(sourceKey: String, favorite: Boolean)

    /** REMOTEEDIT: revision-aware hidden toggle used by PATCH. */
    @Query(
        "UPDATE user_channels SET hidden = :hidden, revision = revision + 1 " +
            "WHERE sourceKey = :sourceKey",
    )
    suspend fun setHiddenBumpingRevision(sourceKey: String, hidden: Boolean)

    /** REMOTEEDIT: revision-aware name edit used by PATCH. */
    @Query(
        "UPDATE user_channels SET customName = :name, revision = revision + 1 " +
            "WHERE sourceKey = :sourceKey",
    )
    suspend fun setCustomNameBumpingRevision(sourceKey: String, name: String?)

    /** REMOTEEDIT: revision-aware number edit used by PATCH. */
    @Query(
        "UPDATE user_channels SET customNumber = :number, revision = revision + 1 " +
            "WHERE sourceKey = :sourceKey",
    )
    suspend fun setCustomNumberBumpingRevision(sourceKey: String, number: Int?)

    /** REMOTEEDIT: revision-aware group assignment used by PATCH. */
    @Query(
        "UPDATE user_channels SET groupId = :groupId, revision = revision + 1 " +
            "WHERE sourceKey = :sourceKey",
    )
    suspend fun setGroupBumpingRevision(sourceKey: String, groupId: Long?)

    /** REMOTEEDIT: revision-aware reorder used by PATCH and batch moves. */
    @Query(
        "UPDATE user_channels SET sortOrder = :sortOrder, revision = revision + 1 " +
            "WHERE sourceKey = :sourceKey",
    )
    suspend fun setSortOrderBumpingRevision(sourceKey: String, sortOrder: Int)

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

    @Query("UPDATE user_channels SET epgIdOverride = :epgId, epgSourceIdOverride = :sourceId WHERE sourceKey = :sourceKey")
    suspend fun setEpgOverride(sourceKey: String, epgId: String?, sourceId: Long?)

    @Query("UPDATE user_channels SET playbackEngineOverride = :engine WHERE sourceKey = :sourceKey")
    suspend fun setPlaybackEngineOverride(sourceKey: String, engine: String?)

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
