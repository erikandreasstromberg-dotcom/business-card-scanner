package com.houseofsilver.cardscanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScanEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        fun getInstance(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "card_scanner.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }

        /**
         * Drops the unused `syncStatus` column. minSdk 28 ships SQLite < 3.35, which has no
         * DROP COLUMN, so the table is rebuilt and the rows copied across.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = "`id`, `contactName`, `contactEmail`, `contactCompany`, " +
                    "`contactRole`, `contactPhone`, `contactWebsite`, `contactLinkedin`, " +
                    "`contactAddress`, `rawText`, `ocrConfidence`, `imageUri`, `emailStatus`, " +
                    "`emailSentAt`, `emailSubjectSent`, `emailBodySent`, `templateVersion`, " +
                    "`context`, `scannedAt`, `source`"
                db.execSQL(
                    "CREATE TABLE `scans_new` (`id` TEXT NOT NULL, `contactName` TEXT NOT NULL, " +
                        "`contactEmail` TEXT NOT NULL, `contactCompany` TEXT, `contactRole` TEXT, " +
                        "`contactPhone` TEXT, `contactWebsite` TEXT, `contactLinkedin` TEXT, " +
                        "`contactAddress` TEXT, `rawText` TEXT NOT NULL, " +
                        "`ocrConfidence` REAL NOT NULL, `imageUri` TEXT, `emailStatus` TEXT NOT NULL, " +
                        "`emailSentAt` INTEGER, `emailSubjectSent` TEXT, `emailBodySent` TEXT, " +
                        "`templateVersion` INTEGER, `context` TEXT, `scannedAt` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("INSERT INTO `scans_new` ($columns) SELECT $columns FROM `scans`")
                db.execSQL("DROP TABLE `scans`")
                db.execSQL("ALTER TABLE `scans_new` RENAME TO `scans`")
            }
        }
    }
}
