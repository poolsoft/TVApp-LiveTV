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
        IptvChannelStagingEntity::class,
        IptvChannelSearchEntity::class,
        XmlTvProgramEntity::class,
        XmlTvSourceEntity::class,
        XtreamEpgProgramEntity::class,
    ],
    version = 18,
    exportSchema = true,
)
abstract class TVAppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun iptvDao(): IptvDao
    abstract fun xmlTvDao(): XmlTvDao
    abstract fun xtreamEpgDao(): XtreamEpgDao

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
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
            )
                .addCallback(IPTV_SEARCH_CALLBACK)
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `xmltv_programs` " +
                        "ADD COLUMN `description` TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `iptv_channels` ADD COLUMN `subtitleUrl` TEXT",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_iptv_channels_sourceId_contentType_groupTitle_originalIndex` " +
                        "ON `iptv_channels` (`sourceId`, `contentType`, `groupTitle`, `originalIndex`)",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `iptv_sources` ADD COLUMN `serverUrl` TEXT")
                db.execSQL("ALTER TABLE `iptv_sources` ADD COLUMN `username` TEXT")
                db.execSQL("ALTER TABLE `iptv_sources` ADD COLUMN `password` TEXT")
                db.execSQL("ALTER TABLE `iptv_sources` ADD COLUMN `macAddress` TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `xtream_epg_programs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`channelId` TEXT NOT NULL, `channelName` TEXT NOT NULL, " +
                        "`normalizedChannelId` TEXT NOT NULL, " +
                        "`normalizedChannelName` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`startTimeMillis` INTEGER NOT NULL, `endTimeMillis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_xtream_epg_programs_normalizedChannelId_startTimeMillis_endTimeMillis` " +
                        "ON `xtream_epg_programs` " +
                        "(`normalizedChannelId`, `startTimeMillis`, `endTimeMillis`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_xtream_epg_programs_normalizedChannelName_startTimeMillis_endTimeMillis` " +
                        "ON `xtream_epg_programs` " +
                        "(`normalizedChannelName`, `startTimeMillis`, `endTimeMillis`)",
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `xmltv_sources` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `location` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `lastUpdatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_xmltv_sources_location` " +
                        "ON `xmltv_sources` (`location`)",
                )
                db.execSQL(
                    "ALTER TABLE `xmltv_programs` ADD COLUMN `sourceId` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_xmltv_programs_sourceId` " +
                        "ON `xmltv_programs` (`sourceId`)",
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_channels` ADD COLUMN `epgIdOverride` TEXT")
                db.execSQL("ALTER TABLE `user_channels` ADD COLUMN `epgSourceIdOverride` INTEGER")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `xmltv_sources` " +
                        "ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL("ALTER TABLE `xmltv_sources` ADD COLUMN `lastError` TEXT")
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `iptv_channel_search` USING FTS4(" +
                        "`sourceKey` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                        "`tvgName` TEXT NOT NULL, `groupTitle` TEXT NOT NULL, " +
                        "tokenize=unicode61 `remove_diacritics=2`, notindexed=`sourceKey`)",
                )
                db.execSQL(
                    "INSERT INTO `iptv_channel_search` " +
                        "(`sourceKey`, `displayName`, `tvgName`, `groupTitle`) " +
                        "SELECT `sourceKey`, `displayName`, COALESCE(`tvgName`, ''), " +
                        "COALESCE(`groupTitle`, '') FROM `iptv_channels`",
                )
                createIptvSearchTriggers(db)
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `iptv_channels` " +
                        "ADD COLUMN `matchKey` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "UPDATE `iptv_channels` SET `matchKey` = " +
                        "LOWER(TRIM(COALESCE(`groupTitle`, ''))) || '|' || " +
                        "LOWER(TRIM(`displayName`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_sourceId_matchKey` " +
                        "ON `iptv_channels` (`sourceId`, `matchKey`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `iptv_channel_staging` (" +
                        "`sessionId` TEXT NOT NULL, `originalIndex` INTEGER NOT NULL, " +
                        "`identityHash` TEXT NOT NULL, `resolvedSourceKey` TEXT, " +
                        "`tvgId` TEXT, `tvgName` TEXT, " +
                        "`displayName` TEXT NOT NULL, `streamUrl` TEXT NOT NULL, " +
                        "`logoUrl` TEXT, `groupTitle` TEXT, `userAgent` TEXT, " +
                        "`referrer` TEXT, `subtitleUrl` TEXT, `contentType` TEXT NOT NULL, " +
                        "`matchKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`sessionId`, `originalIndex`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channel_staging_sessionId` " +
                        "ON `iptv_channel_staging` (`sessionId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channel_staging_createdAt` " +
                        "ON `iptv_channel_staging` (`createdAt`)",
                )
            }
        }

        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `catchUpMode` TEXT")
                db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `catchUpSource` TEXT")
                db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `catchUpDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `iptv_channel_staging` ADD COLUMN `catchUpMode` TEXT")
                db.execSQL("ALTER TABLE `iptv_channel_staging` ADD COLUMN `catchUpSource` TEXT")
                db.execSQL("ALTER TABLE `iptv_channel_staging` ADD COLUMN `catchUpDays` INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_iptv_channel_staging_sessionId_resolvedSourceKey` " +
                        "ON `iptv_channel_staging` (`sessionId`, `resolvedSourceKey`)",
                )
            }
        }

        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_channels_sourceId_tvgId` " +
                        "ON `iptv_channels` (`sourceId`, `tvgId`)",
                )
            }
        }

        internal val IPTV_SEARCH_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createIptvSearchTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                createIptvSearchTriggers(db)
            }
        }

        private fun createIptvSearchTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `iptv_channel_search_insert` " +
                    "AFTER INSERT ON `iptv_channels` BEGIN " +
                    "INSERT INTO `iptv_channel_search` " +
                    "(`sourceKey`, `displayName`, `tvgName`, `groupTitle`) VALUES " +
                    "(new.`sourceKey`, new.`displayName`, COALESCE(new.`tvgName`, ''), " +
                    "COALESCE(new.`groupTitle`, '')); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `iptv_channel_search_delete` " +
                    "AFTER DELETE ON `iptv_channels` BEGIN " +
                    "DELETE FROM `iptv_channel_search` WHERE `sourceKey` = old.`sourceKey`; END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `iptv_channel_search_update` " +
                    "AFTER UPDATE OF `sourceKey`, `displayName`, `tvgName`, `groupTitle` " +
                    "ON `iptv_channels` BEGIN " +
                    "DELETE FROM `iptv_channel_search` WHERE `sourceKey` = old.`sourceKey`; " +
                    "INSERT INTO `iptv_channel_search` " +
                    "(`sourceKey`, `displayName`, `tvgName`, `groupTitle`) VALUES " +
                    "(new.`sourceKey`, new.`displayName`, COALESCE(new.`tvgName`, ''), " +
                    "COALESCE(new.`groupTitle`, '')); END",
            )
        }
    }
}
