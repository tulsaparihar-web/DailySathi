package com.parihardigitallabs.dailysathi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, COALESCE(dueAtMillis, createdAtMillis) ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND dueAtMillis IS NOT NULL")
    suspend fun getPendingWithDueDates(): List<Reminder>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(reminders: List<Reminder>)

    @Transaction
    suspend fun replaceAll(reminders: List<Reminder>) {
        deleteAll()
        if (reminders.isNotEmpty()) insertAll(reminders)
    }
}
