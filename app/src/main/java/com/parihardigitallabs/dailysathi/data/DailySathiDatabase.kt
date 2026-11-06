package com.parihardigitallabs.dailysathi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Reminder::class], version = 3, exportSchema = false)
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
        @Volatile private var instance: DailySathiDatabase? = null

        fun getInstance(context: Context): DailySathiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailySathiDatabase::class.java,
                    "dailysathi.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
