package com.parihardigitallabs.dailysathi

import com.parihardigitallabs.dailysathi.data.Reminder
import org.json.JSONArray
import org.json.JSONObject

internal data class DailySathiBackup(
    val reminders: List<Reminder>,
    val providers: List<LocalServiceProvider>,
    val includesProviders: Boolean
)

internal object ReminderBackup {
    fun encode(reminders: List<Reminder>, providers: List<LocalServiceProvider>): String {
        val root = JSONObject().put("app", "DailySathi").put("formatVersion", 2)
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
        root.put("providers", encodeProviders(providers))
        return root.toString(2)
    }

    fun encodeProviders(providers: List<LocalServiceProvider>): JSONArray {
        val items = JSONArray()
        providers.forEach { provider ->
            items.put(JSONObject()
                .put("id", provider.id)
                .put("name", provider.name)
                .put("service", provider.service)
                .put("phone", provider.phone)
                .put("area", provider.area)
                .put("details", provider.details)
                .put("isFavorite", provider.isFavorite)
                .put("userRating", provider.userRating)
                .put("feedback", provider.feedback))
        }
        return items
    }

    fun decode(json: String): DailySathiBackup {
        val root = JSONObject(json)
        require(root.optString("app") == "DailySathi") { "Not a DailySathi backup file" }
        val version = root.optInt("formatVersion", 0)
        require(version in 1..2) { "Unsupported backup version" }
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

        val hasProviders = version >= 2 && root.has("providers")
        val providerItems = if (hasProviders) root.getJSONArray("providers") else JSONArray()
        val providers = (0 until providerItems.length()).map { index ->
            val item = providerItems.getJSONObject(index)
            val name = item.getString("name").trim()
            val service = item.getString("service").trim()
            val phone = item.getString("phone").trim()
            require(name.isNotBlank() && service.isNotBlank() && phone.isNotBlank()) {
                "Provider name, service and phone are required"
            }
            val rating = item.optInt("userRating", 0)
            require(rating in 0..5) { "Invalid provider rating" }
            LocalServiceProvider(
                id = item.getLong("id"),
                name = name,
                service = service,
                phone = phone,
                area = item.optString("area"),
                details = item.optString("details"),
                isFavorite = item.optBoolean("isFavorite", false),
                userRating = rating,
                feedback = item.optString("feedback")
            )
        }
        require(providers.all { it.id > 0L }) { "Invalid provider ID in backup" }
        require(providers.map { it.id }.distinct().size == providers.size) { "Duplicate provider IDs in backup" }
        return DailySathiBackup(restored, providers, hasProviders)
    }
}
