package com.parihardigitallabs.dailysathi

import com.parihardigitallabs.dailysathi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
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

data class NearbyRepairProvider(
    val id: String,
    val businessName: String,
    val category: String,
    val contactName: String,
    val phone: String,
    val city: String,
    val area: String,
    val description: String,
    val verificationLevel: String,
    val averageRating: Double,
    val reviewCount: Int,
    val distanceKm: Double
)

data class NeighborhoodListing(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val listingType: String,
    val itemCondition: String,
    val priceInr: Double,
    val swapPreferences: String,
    val contactPhone: String,
    val city: String,
    val area: String,
    val createdAt: String,
    val distanceKm: Double
)

data class ProviderCommunityReview(
    val rating: Int,
    val reviewText: String,
    val createdAt: String
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
        area: String,
        searchText: String = ""
    ): List<MarketplaceProvider> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("/rest/v1/service_providers?select=id,business_name,category,contact_name,phone,city,area,description")
            append("&status=eq.approved")
            if (category.isNotBlank() && category != "All") append("&category=eq.${encode(category)}")
            if (city.isNotBlank()) append("&city=ilike.*${encode(city)}*")
            if (area.isNotBlank()) append("&area=ilike.*${encode(area)}*")
            if (searchText.isNotBlank()) {
                val term = encode(searchText.trim())
                append("&or=(business_name.ilike.*$term*,contact_name.ilike.*$term*,category.ilike.*$term*)")
            }
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


    suspend fun listNearbyRepairProviders(
        latitude: Double, longitude: Double, radiusKm: Double = 3.0, category: String? = null
    ): List<NearbyRepairProvider> = withContext(Dispatchers.IO) {
        require(radiusKm in 2.0..5.0) { "Search radius must be between 2 and 5 km." }
        val body = JSONObject()
            .put("p_latitude", latitude)
            .put("p_longitude", longitude)
            .put("p_radius_km", radiusKm)
            .put("p_category", category?.takeIf { it.isNotBlank() && it != "All" })
        val rows = request("/rest/v1/rpc/find_nearby_service_providers", "POST", body) as? JSONArray
            ?: JSONArray()
        (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            NearbyRepairProvider(
                id = item.getString("id"),
                businessName = item.getString("business_name"),
                category = item.getString("category"),
                contactName = item.optString("contact_name"),
                phone = item.optString("phone"),
                city = item.optString("city"),
                area = item.optString("area"),
                description = item.optString("description"),
                verificationLevel = item.optString("verification_level", "unverified"),
                averageRating = item.optDouble("average_rating", 0.0),
                reviewCount = item.optInt("review_count", 0),
                distanceKm = item.optDouble("distance_km", 0.0)
            )
        }
    }

    suspend fun listNearbyListings(
        latitude: Double, longitude: Double, radiusKm: Double = 3.0, category: String? = null
    ): List<NeighborhoodListing> = withContext(Dispatchers.IO) {
        require(radiusKm in 2.0..5.0) { "Search radius must be between 2 and 5 km." }
        val body = JSONObject()
            .put("p_latitude", latitude)
            .put("p_longitude", longitude)
            .put("p_radius_km", radiusKm)
            .put("p_category", category?.takeIf { it.isNotBlank() && it != "All" })
        val rows = request("/rest/v1/rpc/find_nearby_neighborhood_listings", "POST", body) as? JSONArray
            ?: JSONArray()
        (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            NeighborhoodListing(
                id = item.getString("id"),
                title = item.getString("title"),
                description = item.optString("description"),
                category = item.getString("category"),
                listingType = item.getString("listing_type"),
                itemCondition = item.getString("item_condition"),
                priceInr = item.optDouble("price_inr", 0.0),
                swapPreferences = item.optString("swap_preferences"),
                contactPhone = item.optString("contact_phone"),
                city = item.optString("city"),
                area = item.optString("area"),
                createdAt = item.optString("created_at"),
                distanceKm = item.optDouble("distance_km", 0.0)
            )
        }
    }

    suspend fun createNeighborhoodListing(
        session: MarketplaceSession,
        title: String, description: String, category: String, listingType: String,
        itemCondition: String, priceInr: Double, swapPreferences: String,
        contactPhone: String, city: String, area: String,
        latitude: Double, longitude: Double
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_title", title.trim())
            .put("p_description", description.trim())
            .put("p_category", category)
            .put("p_listing_type", listingType)
            .put("p_item_condition", itemCondition)
            .put("p_price_inr", priceInr)
            .put("p_swap_preferences", swapPreferences.trim())
            .put("p_contact_phone", contactPhone.trim())
            .put("p_city", city.trim())
            .put("p_area", area.trim())
            .put("p_latitude", latitude)
            .put("p_longitude", longitude)
        request("/rest/v1/rpc/create_neighborhood_listing", "POST", body, session.accessToken) as? String
            ?: error("The listing could not be created.")
    }

    suspend fun getProviderReviews(providerId: String): List<ProviderCommunityReview> =
        withContext(Dispatchers.IO) {
            val rows = request(
                "/rest/v1/rpc/get_service_provider_reviews",
                "POST",
                JSONObject().put("p_provider_id", providerId)
            ) as? JSONArray ?: JSONArray()
            (0 until rows.length()).map { index ->
                val item = rows.getJSONObject(index)
                ProviderCommunityReview(
                    rating = item.optInt("rating"),
                    reviewText = item.optString("review_text"),
                    createdAt = item.optString("created_at")
                )
            }
        }

    suspend fun submitProviderReview(
        session: MarketplaceSession, providerId: String, rating: Int, reviewText: String
    ) = withContext(Dispatchers.IO) {
        require(rating in 1..5)
        request(
            "/rest/v1/service_provider_reviews",
            "POST",
            JSONObject()
                .put("provider_id", providerId)
                .put("reviewer_id", session.userId)
                .put("rating", rating)
                .put("review_text", reviewText.take(500)),
            session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    suspend fun reportNeighborhoodListing(
        session: MarketplaceSession, listingId: String, reason: String, details: String = ""
    ) = withContext(Dispatchers.IO) {
        require(reason in setOf("spam", "fraud", "unsafe", "prohibited", "other"))
        request(
            "/rest/v1/neighborhood_listing_reports",
            "POST",
            JSONObject()
                .put("listing_id", listingId)
                .put("reporter_id", session.userId)
                .put("reason", reason)
                .put("details", details.take(500)),
            session.accessToken,
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
                else -> JSONTokener(response).nextValue()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
