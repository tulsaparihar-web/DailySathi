package com.parihardigitallabs.dailysathi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Reminder::class, Bill::class], version = 2, exportSchema = false)
abstract class DailySathiDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun billDao(): BillDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS bills (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        dueAtMillis INTEGER NOT NULL,
                        frequency TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isPaid INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        @Volatile private var instance: DailySathiDatabase? = null

        fun getInstance(context: Context): DailySathiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailySathiDatabase::class.java,
                    "dailysathi.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
