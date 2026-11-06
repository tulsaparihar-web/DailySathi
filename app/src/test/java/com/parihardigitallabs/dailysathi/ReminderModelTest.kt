package com.parihardigitallabs.dailysathi

import com.parihardigitallabs.dailysathi.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReminderModelTest {
    @Test
    fun newReminderDefaultsToPendingPersonalReminder() {
        val reminder = Reminder(title = "Pay electricity bill")
        assertEquals("Pay electricity bill", reminder.title)
        assertEquals("Personal", reminder.category)
        assertFalse(reminder.isCompleted)
    }
}
