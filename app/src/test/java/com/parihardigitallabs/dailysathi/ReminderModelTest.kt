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
    fun monthlyRecurrenceClampsJanuary31ToFebruaryEndWithoutDrifting() {
        val start = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 31, 9, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val february = Calendar.getInstance().apply {
            set(2026, Calendar.FEBRUARY, 28, 9, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val first = nextOccurrence(start, "MONTHLY", start, 31)
        assertEquals(february, first)

        val march = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 31, 9, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val second = nextOccurrence(first, "MONTHLY", february, 31)
        assertEquals(march, second)
    }

    @Test
    fun monthlyRecurrenceUsesLeapDayWhenAnchorIs29() {
        val start = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 29, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = Calendar.getInstance().apply {
            set(2024, Calendar.FEBRUARY, 29, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, nextOccurrence(start, "MONTHLY", start, 29))
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
