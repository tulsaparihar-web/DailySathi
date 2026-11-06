package com.parihardigitallabs.dailysathi

import com.parihardigitallabs.dailysathi.data.Reminder
import org.json.JSONArray
import org.json.JSONObject

internal object ReminderBackup {
    fun encode(reminders: List<Reminder>): String {
        val root = JSONObject().put("app", "DailySathi").put("formatVersion", 1)
        val items = JSONArray()
        reminders.forEach { reminder ->
            items.put(JSONObject()
                .put("id", reminder.id)
                .put("title", reminder.title)
                .put("note", reminder.note)
                .put("dueAtMillis", reminder.dueAtMillis ?: JSONObject.NULL)
                .put("category", reminder.category)
                .put("isCompleted", reminder.isCompleted)
                .put("createdAtMillis", reminder.createdAtMillis)
                .put("recurrence", reminder.recurrence)
                .put("providerName", reminder.providerName)
                .put("location", reminder.location)
                .put("preparationNotes", reminder.preparationNotes))
        }
        root.put("reminders", items)
        return root.toString(2)
    }

    fun decode(json: String): List<Reminder> {
        val root = JSONObject(json)
        require(root.optString("app") == "DailySathi") { "Not a DailySathi backup file" }
        require(root.optInt("formatVersion", 0) == 1) { "Unsupported backup version" }
        val items = root.getJSONArray("reminders")
        val restored = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            Reminder(
                id = item.getLong("id"),
                title = item.getString("title").also { require(it.isNotBlank()) },
                note = item.optString("note"),
                dueAtMillis = if (item.isNull("dueAtMillis")) null else item.getLong("dueAtMillis"),
                category = item.optString("category", "Personal"),
                isCompleted = item.optBoolean("isCompleted", false),
                createdAtMillis = item.optLong("createdAtMillis", System.currentTimeMillis()),
                recurrence = item.optString("recurrence", "NONE"),
                providerName = item.optString("providerName"),
                location = item.optString("location"),
                preparationNotes = item.optString("preparationNotes")
            )
        }
        require(restored.map { it.id }.distinct().size == restored.size) { "Duplicate reminder IDs in backup" }
        require(restored.all { it.id > 0L }) { "Invalid reminder ID in backup" }
        return restored
    }
}
