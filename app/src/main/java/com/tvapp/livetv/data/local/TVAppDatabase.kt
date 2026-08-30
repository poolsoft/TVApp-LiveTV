package com.tvapp.livetv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserChannelEntity::class,
        ChannelGroupEntity::class,
        IptvSourceEntity::class,
        IptvChannelEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class TVAppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun iptvDao(): IptvDao

    companion object {
        @Volatile
        private var instance: TVAppDatabase? = null

        fun getInstance(context: Context): TVAppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TVAppDatabase::class.java,
                "tv-app.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `iptv_sources` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `location` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`lastUpdatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_iptv_sources_location` " +
                        "ON `iptv_sources` (`location`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `iptv_channels` (" +
                        "`sourceKey` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, " +
                        "`tvgId` TEXT, `tvgName` TEXT, `displayName` TEXT NOT NULL, " +
                        "`streamUrl` TEXT NOT NULL, `logoUrl` TEXT, `groupTitle` TEXT, " +
                        "`userAgent` TEXT, `referrer` TEXT, `originalIndex` INTEGER NOT NULL, " +
                        "`lastSeenAt` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`), " +
                        "FOREIGN KEY(`sourceId`) REFERENCES `iptv_sources`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_sourceId` " +
                        "ON `iptv_channels` (`sourceId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_originalIndex` " +
                        "ON `iptv_channels` (`originalIndex`)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `iptv_channels` " +
                        "ADD COLUMN `selected` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_selected` " +
                        "ON `iptv_channels` (`selected`)",
                )
            }
        }
    }
}
