package com.tvapp.livetv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"],
    notIndexed = ["sourceKey"],
)
@Entity(tableName = "iptv_channel_search")
data class IptvChannelSearchEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    val sourceKey: String,
    val displayName: String,
    val tvgName: String,
    val groupTitle: String,
)
