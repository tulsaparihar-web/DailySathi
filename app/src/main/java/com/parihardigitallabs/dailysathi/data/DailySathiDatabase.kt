package com.parihardigitallabs.dailysathi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Reminder::class], version = 5, exportSchema = false)
abstract class DailySathiDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        // Version 2 was briefly used by a development build that added a Bills table.
        // Keep the version path valid without bringing Bills back into the app.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) = Unit
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN providerName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE reminders ADD COLUMN location TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE reminders ADD COLUMN preparationNotes TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN recurrenceDayOfMonth INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: DailySathiDatabase? = null

        fun getInstance(context: Context): DailySathiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailySathiDatabase::class.java,
                    "dailysathi.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
