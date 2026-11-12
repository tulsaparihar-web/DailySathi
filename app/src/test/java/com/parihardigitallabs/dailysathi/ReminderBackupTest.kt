package com.parihardigitallabs.dailysathi

import com.parihardigitallabs.dailysathi.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class ReminderBackupTest {
    @Test
    fun currentBackupRoundTripsRemindersAndLocalProviders() {
        val reminder = Reminder(
            id = 12L,
            title = "Doctor appointment",
            note = "Bring reports",
            dueAtMillis = 1_800_000_000_000L,
            category = "Health",
            recurrence = "MONTHLY",
            providerName = "City Clinic",
            location = "Jaipur",
            preparationNotes = "Arrive early",
            recurrenceDayOfMonth = 31
        )
        val provider = LocalServiceProvider(
            id = 34L,
            name = "Ramesh Electrician",
            service = "Electrician",
            phone = "+919876543210",
            area = "Jaipur",
            details = "Available evenings",
            isFavorite = true,
            userRating = 5,
            feedback = "Prompt and professional"
        )

        val restored = ReminderBackup.decode(ReminderBackup.encode(listOf(reminder), listOf(provider)))

        assertEquals(3, JSONObject(ReminderBackup.encode(listOf(reminder), listOf(provider))).getInt("formatVersion"))
        assertEquals(listOf(reminder), restored.reminders)
        assertEquals(listOf(provider), restored.providers)
        assertTrue(restored.includesProviders)
    }

    @Test
    fun versionTwoBackupRemainsCompatibleWithoutMonthlyAnchor() {
        val legacy = JSONObject()
            .put("app", "DailySathi")
            .put("formatVersion", 2)
            .put("reminders", org.json.JSONArray().put(
                JSONObject()
                    .put("id", 7L)
                    .put("title", "Monthly reminder")
                    .put("dueAtMillis", 1_800_000_000_000L)
                    .put("recurrence", "MONTHLY")
            ))
            .put("providers", org.json.JSONArray())

        val restored = ReminderBackup.decode(legacy.toString())

        assertEquals(1, restored.reminders.size)
        assertEquals(0, restored.reminders.single().recurrenceDayOfMonth)
        assertTrue(restored.includesProviders)
        assertTrue(restored.providers.isEmpty())
    }

    @Test
    fun versionOneBackupDoesNotOverwriteExistingProviderDirectory() {
        val legacy = JSONObject()
            .put("app", "DailySathi")
            .put("formatVersion", 1)
            .put("reminders", org.json.JSONArray())

        val restored = ReminderBackup.decode(legacy.toString())

        assertFalse(restored.includesProviders)
        assertTrue(restored.providers.isEmpty())
    }

    @Test
    fun duplicateReminderIdsAreRejected() {
        val item = JSONObject().put("id", 1L).put("title", "Duplicate")
        val invalid = JSONObject()
            .put("app", "DailySathi")
            .put("formatVersion", 3)
            .put("reminders", org.json.JSONArray().put(item).put(JSONObject(item.toString())))

        val failure = runCatching { ReminderBackup.decode(invalid.toString()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
    @Test
    fun unsupportedRecurrenceIsRejected() {
        val invalid = JSONObject()
            .put("app", "DailySathi")
            .put("formatVersion", 3)
            .put("reminders", org.json.JSONArray().put(
                JSONObject()
                    .put("id", 2L)
                    .put("title", "Bad recurrence")
                    .put("dueAtMillis", 1_800_000_000_000L)
                    .put("recurrence", "YEARLY")
            ))

        val failure = runCatching { ReminderBackup.decode(invalid.toString()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun recurringReminderWithoutDueDateIsRejected() {
        val invalid = JSONObject()
            .put("app", "DailySathi")
            .put("formatVersion", 3)
            .put("reminders", org.json.JSONArray().put(
                JSONObject()
                    .put("id", 3L)
                    .put("title", "Missing date")
                    .put("recurrence", "WEEKLY")
            ))

        val failure = runCatching { ReminderBackup.decode(invalid.toString()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun monthlyAnchorOnNonMonthlyReminderIsRejected() {
        val invalid = JSONObject()
            .put("app", "DailySathi")
            .put("formatVersion", 3)
            .put("reminders", org.json.JSONArray().put(
                JSONObject()
                    .put("id", 4L)
                    .put("title", "Invalid anchor")
                    .put("recurrenceDayOfMonth", 31)
            ))

        val failure = runCatching { ReminderBackup.decode(invalid.toString()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun negativeDueDateIsRejected() {
        val invalid = JSONObject()
            .put("app", "DailySathi")
            .put("formatVersion", 3)
            .put("reminders", org.json.JSONArray().put(
                JSONObject()
                    .put("id", 5L)
                    .put("title", "Invalid date")
                    .put("dueAtMillis", -1L)
            ))

        val failure = runCatching { ReminderBackup.decode(invalid.toString()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun malformedProviderDirectoryIsRejectedInsteadOfBecomingEmpty() {
        val invalid = org.json.JSONArray().put(
            JSONObject()
                .put("id", 1L)
                .put("name", "   ")
                .put("service", "Electrician")
                .put("phone", "1234567890")
        )

        val failure = runCatching { ReminderBackup.decodeProviders(invalid.toString()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun duplicateProviderIdsAreRejected() {
        val provider = JSONObject()
            .put("id", 9L)
            .put("name", "Local plumber")
            .put("service", "Plumber")
            .put("phone", "9876543210")
        val invalid = org.json.JSONArray().put(provider).put(JSONObject(provider.toString()))

        val failure = runCatching { ReminderBackup.decodeProviders(invalid.toString()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun providerRatingOutsideZeroToFiveIsRejected() {
        val invalid = org.json.JSONArray().put(
            JSONObject()
                .put("id", 10L)
                .put("name", "Local electrician")
                .put("service", "Electrician")
                .put("phone", "9876543210")
                .put("userRating", 6)
        )

        val failure = runCatching { ReminderBackup.decodeProviders(invalid.toString()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

}
