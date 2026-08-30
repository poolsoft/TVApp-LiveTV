package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_channels",
    foreignKeys = [
        ForeignKey(
            entity = ChannelGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("groupId"), Index("sortOrder")],
)
data class UserChannelEntity(
    @PrimaryKey val sourceKey: String,
    val sourceType: String,
    val originalDisplayNumber: String,
    val lastKnownName: String,
    val customNumber: Int? = null,
    val customName: String? = null,
    val sortOrder: Int,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val groupId: Long? = null,
    val lastSeenAt: Long,
)
