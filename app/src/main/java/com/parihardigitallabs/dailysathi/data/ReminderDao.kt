package com.parihardigitallabs.dailysathi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, COALESCE(dueAtMillis, createdAtMillis) ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}
