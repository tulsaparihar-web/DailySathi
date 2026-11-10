package com.parihardigitallabs.dailysathi

import com.parihardigitallabs.dailysathi.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReminderModelTest {
    @Test
    fun newReminderDefaultsToPendingPersonalReminder() {
        val reminder = Reminder(title = "Pay electricity bill")
        assertEquals("Pay electricity bill", reminder.title)
        assertEquals("Personal", reminder.category)
        assertFalse(reminder.isCompleted)
    }

    @Test
    fun dailyRecurrenceCatchesUpToNextFutureOccurrence() {
        val start = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 4, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val actual = nextOccurrence(start, "DAILY", now)
        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 5, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, actual)
        assertTrue(actual!! > now)
    }

    @Test
    fun weeklyRecurrenceReturnsNextFutureOccurrence() {
        val start = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 5, 8, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val actual = nextOccurrence(start, "WEEKLY", now)
        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 26, 8, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, actual)
    }

    @Test
    fun monthlyRecurrenceReturnsNextFutureOccurrence() {
        val start = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 10, 7, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 11, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val actual = nextOccurrence(start, "MONTHLY", now)
        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 10, 7, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, actual)
    }

    @Test
    fun nonRecurringReminderHasNoNextOccurrence() {
        assertNull(nextOccurrence(1_000L, "NONE", 2_000L))
    }

    @Test
    fun missingDueDateHasNoNextOccurrence() {
        assertNull(nextOccurrence(null, "DAILY", 2_000L))
    }
}
