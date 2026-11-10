package com.parihardigitallabs.dailysathi

import com.parihardigitallabs.dailysathi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MarketplaceProvider(
    val id: String,
    val businessName: String,
    val category: String,
    val contactName: String,
    val phone: String,
    val city: String,
    val area: String,
    val description: String
)

data class MarketplaceSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String
)

class MarketplaceApi {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") && apiKey.isNotBlank()

    suspend fun requestPhoneOtp(phone: String) = withContext(Dispatchers.IO) {
        request(
            path = "/auth/v1/otp",
            method = "POST",
            body = JSONObject().put("phone", phone).put("create_user", true)
        )
    }

    suspend fun verifyPhoneOtp(phone: String, code: String): MarketplaceSession = withContext(Dispatchers.IO) {
        val result = request(
            path = "/auth/v1/verify",
            method = "POST",
            body = JSONObject().put("phone", phone).put("token", code).put("type", "sms")
        )
        val response = result as? JSONObject ?: error("Invalid OTP verification response")
        val user = response.getJSONObject("user")
        MarketplaceSession(
            accessToken = response.getString("access_token"),
            refreshToken = response.optString("refresh_token"),
            userId = user.getString("id")
        )
    }

    suspend fun listApprovedProviders(
        session: MarketplaceSession?,
        category: String,
        city: String,
        area: String
    ): List<MarketplaceProvider> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("/rest/v1/service_providers?select=id,business_name,category,contact_name,phone,city,area,description")
            append("&status=eq.approved")
            if (category.isNotBlank() && category != "All") append("&category=eq.${encode(category)}")
            if (city.isNotBlank()) append("&city=ilike.*${encode(city)}*")
            if (area.isNotBlank()) append("&area=ilike.*${encode(area)}*")
            append("&order=created_at.desc&limit=100")
        }
        val result = request(path = query, method = "GET", accessToken = session?.accessToken)
        val rows = result as? JSONArray ?: JSONArray()
        (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            MarketplaceProvider(
                id = item.getString("id"),
                businessName = item.getString("business_name"),
                category = item.getString("category"),
                contactName = item.getString("contact_name"),
                phone = item.getString("phone"),
                city = item.getString("city"),
                area = item.getString("area"),
                description = item.optString("description")
            )
        }
    }

    suspend fun submitProvider(
        businessName: String,
        category: String,
        contactName: String,
        phone: String,
        city: String,
        area: String,
        description: String
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("business_name", businessName)
            .put("category", category)
            .put("contact_name", contactName)
            .put("phone", phone)
            .put("city", city)
            .put("area", area)
            .put("description", description)
            .put("status", "pending")
        request(
            path = "/rest/v1/service_providers",
            method = "POST",
            body = body,
            prefer = "return=minimal"
        )
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        accessToken: String? = null,
        prefer: String? = null
    ): Any = run {
        check(isConfigured) { "Marketplace backend is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties." }
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${accessToken ?: apiKey}")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (prefer != null) setRequestProperty("Prefer", prefer)
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("message").ifBlank { response } }
                    .getOrDefault(response)
                error(message.ifBlank { "Marketplace request failed ($status)" })
            }
            when {
                response.isBlank() -> JSONObject()
                response.trimStart().startsWith("[") -> JSONArray(response)
                else -> JSONObject(response)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
