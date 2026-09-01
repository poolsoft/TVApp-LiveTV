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
        XmlTvProgramEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class TVAppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun iptvDao(): IptvDao
    abstract fun xmlTvDao(): XmlTvDao

    companion object {
        @Volatile
        private var instance: TVAppDatabase? = null

        fun getInstance(context: Context): TVAppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TVAppDatabase::class.java,
                "tv-app.db",
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_sourceId_originalIndex` " +
                        "ON `iptv_channels` (`sourceId`, `originalIndex`)",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `iptv_channels` " +
                        "ADD COLUMN `contentType` TEXT NOT NULL DEFAULT 'LIVE'",
                )
                db.execSQL(
                    "UPDATE `iptv_channels` SET `contentType` = 'VOD' WHERE " +
                        "LOWER(`streamUrl` || ' ' || COALESCE(`groupTitle`, '')) LIKE '%.mp4%' OR " +
                        "LOWER(`streamUrl` || ' ' || COALESCE(`groupTitle`, '')) LIKE '%.mkv%' OR " +
                        "LOWER(`streamUrl` || ' ' || COALESCE(`groupTitle`, '')) LIKE '%/movie/%' OR " +
                        "LOWER(`streamUrl` || ' ' || COALESCE(`groupTitle`, '')) LIKE '%/series/%' OR " +
                        "LOWER(`streamUrl` || ' ' || COALESCE(`groupTitle`, '')) LIKE '%/vod/%'",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_sourceId_contentType_originalIndex` " +
                        "ON `iptv_channels` (`sourceId`, `contentType`, `originalIndex`)",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `xmltv_programs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`channelId` TEXT NOT NULL, `channelName` TEXT NOT NULL, " +
                        "`normalizedChannelId` TEXT NOT NULL, " +
                        "`normalizedChannelName` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`startTimeMillis` INTEGER NOT NULL, `endTimeMillis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_xmltv_programs_normalizedChannelId_startTimeMillis_endTimeMillis` " +
                        "ON `xmltv_programs` (`normalizedChannelId`, `startTimeMillis`, `endTimeMillis`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_xmltv_programs_normalizedChannelName_startTimeMillis_endTimeMillis` " +
                        "ON `xmltv_programs` (`normalizedChannelName`, `startTimeMillis`, `endTimeMillis`)",
                )
            }
        }
    }
}
