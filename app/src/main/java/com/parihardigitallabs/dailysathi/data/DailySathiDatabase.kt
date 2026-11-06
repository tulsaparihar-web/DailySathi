package com.parihardigitallabs.dailysathi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Reminder::class], version = 1, exportSchema = false)
abstract class DailySathiDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var instance: DailySathiDatabase? = null

        fun getInstance(context: Context): DailySathiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailySathiDatabase::class.java,
                    "dailysathi.db"
                ).build().also { instance = it }
            }
    }
}
