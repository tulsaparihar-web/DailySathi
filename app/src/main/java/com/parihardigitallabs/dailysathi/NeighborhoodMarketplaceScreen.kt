package com.parihardigitallabs.dailysathi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private val MarketplaceNavy = Color(0xFF123B50)
private val MarketplaceTeal = Color(0xFF168D87)
private val MarketplaceBlue = Color(0xFF2876C7)
private val MarketplaceMuted = Color(0xFF64748B)
private val MarketplaceSurface = Color.White

private val resaleCategories = listOf(
    "Furniture", "Kids items", "Electronics", "Books",
    "Home & Kitchen", "Clothing", "Sports & Hobby", "Other"
)

@Composable
internal fun NeighborhoodMarketplaceScreen(hindi: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { MarketplaceApi() }
    var section by remember { mutableStateOf("repair") }
    var radius by remember { mutableIntStateOf(3) }
    var category by remember { mutableStateOf("All") }
    var city by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var location by remember { mutableStateOf<Location?>(null) }
    var locationBusy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var repairProviders by remember { mutableStateOf<List<NearbyRepairProvider>>(emptyList()) }
    var listings by remember { mutableStateOf<List<NeighborhoodListing>>(emptyList()) }
    var session by remember { mutableStateOf<MarketplaceSession?>(null) }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var phoneDraft by remember { mutableStateOf("+91") }
    var otpDraft by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var contactBusyId by remember { mutableStateOf<String?>(null) }
    var pendingContactId by remember { mutableStateOf<String?>(null) }
    var reportTarget by remember { mutableStateOf<NeighborhoodListing?>(null) }
    var reportReason by remember { mutableStateOf("other") }
    var reportDetails by remember { mutableStateOf("") }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            readCurrentLocation(context, onLocation = { location = it; locationBusy = false; message = null },
                onFailure = { locationBusy = false; message = it })
        } else {
            locationBusy = false
            message = if (hindi) "आसपास खोजने के लिए लोकेशन अनुमति आवश्यक है। आप बाद में फिर कोशिश कर सकते हैं।"
                else "Location permission is needed for nearby results. You can try again whenever you like."
        }
    }

    fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            locationBusy = true
            readCurrentLocation(context, onLocation = { location = it; locationBusy = false; message = null },
                onFailure = { locationBusy = false; message = it })
        } else {
            locationBusy = true
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    fun loadNearby() {
        val current = location
        if (!api.isConfigured) {
            message = if (hindi) "ऑनलाइन मार्केटप्लेस जोड़ने के लिए Supabase सेटअप आवश्यक है।"
                else "Supabase setup is required to connect the online marketplace."
            return
        }
        if (current == null) {
            message = if (hindi) "आसपास के परिणाम देखने के लिए पहले लोकेशन प्राप्त करें।"
                else "Get your location first to see nearby results."
            return
        }
        loading = true
        message = null
        scope.launch {
            runCatching {
                if (section == "repair") {
                    api.listNearbyRepairProviders(current.latitude, current.longitude, radius.toDouble(),
                        category.takeIf { it != "All" })
                } else {
                    api.listNearbyListings(current.latitude, current.longitude, radius.toDouble(),
                        category.takeIf { it != "All" })
                }
            }.onSuccess { result ->
                if (section == "repair") {
                    @Suppress("UNCHECKED_CAST")
                    repairProviders = result as List<NearbyRepairProvider>
                } else {
                    @Suppress("UNCHECKED_CAST")
                    listings = result as List<NeighborhoodListing>
                }
                if (result.isEmpty()) message = if (hindi) "इस दायरे में अभी कोई लिस्टिंग नहीं मिली।"
                    else "No listings found in this radius yet."
            }.onFailure {
                message = it.message ?: if (hindi) "डेटा लोड नहीं हो सका।" else "Unable to load marketplace results."
            }
            loading = false
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(if (hindi) "आस-पास का मार्केटप्लेस" else "Neighbourhood marketplace",
            color = MarketplaceNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(if (hindi) "स्थानीय सेवाएँ और पड़ोस में खरीदें, बेचें या बदलें।"
            else "Find local help or buy, sell and swap with your neighbours.",
            color = MarketplaceMuted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = section == "repair", onClick = { section = "repair"; category = "All" },
                shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(if (hindi) "मरम्मत सेवाएँ" else "Repair")
            }
            SegmentedButton(selected = section == "resale", onClick = { section = "resale"; category = "All" },
                shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text(if (hindi) "खरीदें · बेचें" else "Buy · Sell")
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = MarketplaceSurface, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, tint = MarketplaceTeal, contentDescription = null)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(if (hindi) "आपके आसपास" else "Around you", color = MarketplaceNavy, fontWeight = FontWeight.SemiBold)
                        Text(if (location == null) {
                            if (hindi) "लोकेशन केवल खोज के समय उपयोग होगी" else "Location is used only when you search"
                        } else {
                            if (hindi) "लोकेशन उपलब्ध" else "Location ready"
                        }, color = MarketplaceMuted, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { requestLocation() }, enabled = !locationBusy) {
                        Text(if (locationBusy) (if (hindi) "प्राप्त हो रहा..." else "Getting...")
                            else if (hindi) "लोकेशन लें" else "Use location")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hindi) "दायरा" else "Radius", color = MarketplaceMuted, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    listOf(2, 3, 5).forEach { km ->
                        FilterChip(selected = radius == km, onClick = { radius = km },
                            label = { Text("$km km") }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                if (section == "repair") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Electrician", "Plumber", "Mason", "Carpenter", "AC repair", "Driver").forEach { value ->
                            FilterChip(selected = category == value, onClick = { category = value },
                                label = { Text(categoryLabel(value, hindi)) })
                        }
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("All") + resaleCategories).forEach { value ->
                            FilterChip(selected = category == value, onClick = { category = value },
                                label = { Text(if (value == "All") { if (hindi) "सभी" else "All" } else value) })
                        }
                    }
                }
                Button(onClick = { loadNearby() }, enabled = !loading && !locationBusy,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) (if (hindi) "खोज रहे हैं..." else "Searching...")
                        else if (hindi) "आसपास खोजें" else "Search nearby")
                }
            }
        }
        if (section == "resale") {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                if (session == null) showPhoneDialog = true else showCreateDialog = true
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (hindi) "नई वस्तु की लिस्टिंग जोड़ें" else "Post an item")
            }
        }
        message?.let {
            Surface(color = Color(0xFFFFF7E8), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(it, color = Color(0xFF8A4B08), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (section == "repair") {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(repairProviders, key = { it.id }) { provider ->
                    Surface(color = MarketplaceSurface, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(provider.businessName, modifier = Modifier.weight(1f), color = MarketplaceNavy, fontWeight = FontWeight.SemiBold)
                                if (provider.verificationLevel != "unverified") {
                                    AssistChip(onClick = {}, label = { Text(verificationLabel(provider.verificationLevel, hindi)) },
                                        leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null) })
                                }
                            }
                            Text(categoryLabel(provider.category, hindi) + " · " + provider.area + ", " + provider.city,
                                color = MarketplaceTeal, fontSize = 12.sp)
                            Text(String.format(java.util.Locale.US, "%.1f km · ★ %.1f (%d)",
                                provider.distanceKm, provider.averageRating, provider.reviewCount),
                                color = MarketplaceMuted, fontSize = 12.sp)
                            if (provider.description.isNotBlank()) Text(provider.description, color = MarketplaceMuted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + provider.phone))) },
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Call, contentDescription = null); Spacer(Modifier.width(5.dp))
                                    Text(if (hindi) "कॉल" else "Call")
                                }
                                OutlinedButton(onClick = {
                                    val digits = provider.phone.filter { it.isDigit() }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
                                }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Chat, contentDescription = null); Spacer(Modifier.width(5.dp))
                                    Text("WhatsApp")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(listings, key = { it.id }) { item ->
                    Surface(color = MarketplaceSurface, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.title, modifier = Modifier.weight(1f), color = MarketplaceNavy, fontWeight = FontWeight.SemiBold)
                                Text(when (item.listingType) {
                                    "free" -> if (hindi) "मुफ़्त" else "Free"
                                    "swap" -> if (hindi) "बदलें" else "Swap"
                                    else -> "₹" + item.priceInr.toLong()
                                }, color = MarketplaceTeal, fontWeight = FontWeight.Bold)
                            }
                            Text(item.category + " · " + item.area + ", " + item.city,
                                color = MarketplaceMuted, fontSize = 12.sp)
                            Text((if (hindi) "स्थिति: " else "Condition: ") + item.itemCondition.replace('_',' ') +
                                " · " + String.format(java.util.Locale.US, "%.1f km", item.distanceKm),
                                color = MarketplaceMuted, fontSize = 11.sp)
                            if (item.description.isNotBlank()) Text(item.description, color = MarketplaceNavy, fontSize = 13.sp)
                            if (item.swapPreferences.isNotBlank()) Text((if (hindi) "बदलाव: " else "Swap for: ") + item.swapPreferences,
                                color = MarketplaceMuted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (session == null) { pendingContactId = item.id; showPhoneDialog = true }
                                    else {
                                        contactBusyId = item.id
                                        scope.launch {
                                            runCatching { api.getNeighborhoodListingContact(session!!, item.id) }
                                                .onSuccess { phone -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                                                .onFailure { message = it.message ?: "Unable to retrieve seller contact." }
                                            contactBusyId = null
                                        }
                                    }
                                }, modifier = Modifier.weight(1f), enabled = contactBusyId != item.id) {
                                    Icon(Icons.Default.Call, contentDescription = null); Spacer(Modifier.width(5.dp))
                                    Text(if (contactBusyId == item.id) (if (hindi) "प्रतीक्षा..." else "Please wait")
                                        else if (hindi) "विक्रेता से संपर्क" else "Contact seller")
                                }
                                OutlinedButton(onClick = {
                                    if (session == null) { reportTarget = item; showPhoneDialog = true }
                                    else reportTarget = item
                                }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Flag, contentDescription = null); Spacer(Modifier.width(5.dp))
                                    Text(if (hindi) "रिपोर्ट" else "Report")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = { Text(if (hindi) "मोबाइल सत्यापन" else "Phone verification") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hindi) "पोस्ट करने और विक्रेता से संपर्क करने के लिए OTP से साइन इन करें।"
                        else "Sign in with a one-time code to post an item or contact a seller.", color = MarketplaceMuted, fontSize = 12.sp)
                    OutlinedTextField(value = phoneDraft, onValueChange = { phoneDraft = it.filter { c -> c.isDigit() || c == '+' }.take(16) },
                        label = { Text(if (hindi) "मोबाइल नंबर (+91 सहित)" else "Phone number (include +91)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = !authBusy && phoneDraft.count { it.isDigit() } in 10..15, onClick = {
                    authBusy = true
                    scope.launch {
                        runCatching { api.requestPhoneOtp(phoneDraft.trim()) }
                            .onSuccess { showPhoneDialog = false; showOtpDialog = true; message = if (hindi) "OTP भेजा गया है।" else "OTP sent." }
                            .onFailure { message = it.message ?: "Unable to send OTP." }
                        authBusy = false
                    }
                }) { Text(if (authBusy) "…" else if (hindi) "OTP भेजें" else "Send OTP") }
            },
            dismissButton = { TextButton(onClick = { showPhoneDialog = false }) { Text(if (hindi) "रद्द करें" else "Cancel") } }
        )
    }

    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { showOtpDialog = false },
            title = { Text(if (hindi) "OTP दर्ज करें" else "Enter OTP") },
            text = {
                OutlinedTextField(value = otpDraft, onValueChange = { otpDraft = it.filter(Char::isDigit).take(8) },
                    label = { Text(if (hindi) "SMS कोड" else "SMS code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = !authBusy && otpDraft.length >= 4, onClick = {
                    authBusy = true
                    scope.launch {
                        runCatching { api.verifyPhoneOtp(phoneDraft.trim(), otpDraft.trim()) }
                            .onSuccess {
                                session = it
                                showOtpDialog = false
                                otpDraft = ""
                                message = if (hindi) "मोबाइल सत्यापित। अब आप पोस्ट कर सकते हैं।" else "Phone verified. You can now post."
                                if (pendingContactId != null) {
                                    val listingId = pendingContactId!!
                                    pendingContactId = null
                                    runCatching { api.getNeighborhoodListingContact(it, listingId) }
                                        .onSuccess { phone -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                                        .onFailure { message = it.message ?: "Unable to retrieve seller contact." }
                                } else if (reportTarget == null) showCreateDialog = true
                            }
                            .onFailure { message = it.message ?: "OTP verification failed." }
                        authBusy = false
                    }
                }) { Text(if (authBusy) "…" else if (hindi) "सत्यापित करें" else "Verify") }
            },
            dismissButton = { TextButton(onClick = { showOtpDialog = false }) { Text(if (hindi) "बंद करें" else "Close") } }
        )
    }

    if (showCreateDialog) {
        CreateNeighborhoodListingDialog(hindi, onDismiss = { showCreateDialog = false }) { title, description, cat, type, condition, price, swap, phone, town, neighbourhood ->
            val current = location
            if (current == null) {
                message = if (hindi) "पोस्ट करने से पहले लोकेशन प्राप्त करें।" else "Get your location before posting."
            } else {
                showCreateDialog = false
                loading = true
                scope.launch {
                    runCatching {
                        api.createNeighborhoodListing(session!!, title, description, cat, type, condition,
                            price, swap, phone, town, neighbourhood, current.latitude, current.longitude)
                    }.onSuccess { message = if (hindi) "आपकी लिस्टिंग समीक्षा के लिए भेजी गई है। स्वीकृति के बाद दिखाई देगी।"
                        else "Your listing was submitted for review and will appear after approval." }
                        .onFailure { message = it.message ?: "Unable to create listing." }
                    loading = false
                }
            }
        }
    }

    reportTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text(if (hindi) "लिस्टिंग रिपोर्ट करें" else "Report listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    listOf("spam", "fraud", "unsafe", "prohibited", "other").forEach { value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = reportReason == value, onClick = { reportReason = value })
                            Text(when(value) {
                                "spam" -> if (hindi) "स्पैम" else "Spam"
                                "fraud" -> if (hindi) "धोखाधड़ी" else "Fraud"
                                "unsafe" -> if (hindi) "असुरक्षित" else "Unsafe"
                                "prohibited" -> if (hindi) "प्रतिबंधित वस्तु" else "Prohibited item"
                                else -> if (hindi) "अन्य" else "Other"
                            })
                        }
                    }
                    OutlinedTextField(value = reportDetails, onValueChange = { reportDetails = it.take(500) },
                        label = { Text(if (hindi) "विवरण (वैकल्पिक)" else "Details (optional)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = session
                    if (current == null) { reportTarget = null; showPhoneDialog = true }
                    else {
                        val target = reportTarget!!
                        reportTarget = null
                        scope.launch {
                            runCatching { api.reportNeighborhoodListing(current, target.id, reportReason, reportDetails) }
                                .onSuccess { message = if (hindi) "रिपोर्ट भेज दी गई।" else "Report submitted." }
                                .onFailure { message = it.message ?: "Unable to submit report." }
                            reportDetails = ""
                            reportReason = "other"
                        }
                    }
                }) { Text(if (hindi) "रिपोर्ट भेजें" else "Submit report") }
            },
            dismissButton = { TextButton(onClick = { reportTarget = null }) { Text(if (hindi) "रद्द करें" else "Cancel") } }
        )
    }
}

@Composable
private fun CreateNeighborhoodListingDialog(
    hindi: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Double, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(resaleCategories.first()) }
    var type by remember { mutableStateOf("sale") }
    var condition by remember { mutableStateOf("good") }
    var price by remember { mutableStateOf("") }
    var swap by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+91") }
    var city by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hindi) "नई लिस्टिंग" else "Create listing") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) },
                    label = { Text(if (hindi) "वस्तु का नाम" else "Item title") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it.take(1000) },
                    label = { Text(if (hindi) "विवरण" else "Description") }, minLines = 2)
                Text(if (hindi) "श्रेणी" else "Category", fontWeight = FontWeight.SemiBold)
                resaleCategories.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = category == item, onClick = { category = item })
                        Text(item, fontSize = 13.sp)
                    }
                }
                Text(if (hindi) "लिस्टिंग का प्रकार" else "Listing type", fontWeight = FontWeight.SemiBold)
                Row {
                    listOf("sale", "swap", "free").forEach { item ->
                        FilterChip(selected = type == item, onClick = { type = item },
                            label = { Text(when(item) { "sale" -> if (hindi) "बेचें" else "Sale"; "swap" -> if (hindi) "बदलें" else "Swap"; else -> if (hindi) "मुफ़्त" else "Free" }) },
                            modifier = Modifier.padding(end = 4.dp))
                    }
                }
                Text(if (hindi) "वस्तु की स्थिति" else "Item condition", fontWeight = FontWeight.SemiBold)
                Row {
                    listOf("like_new", "good", "fair").forEach { item ->
                        FilterChip(selected = condition == item, onClick = { condition = item },
                            label = { Text(when(item) { "like_new" -> if (hindi) "लगभग नई" else "Like new"; "good" -> if (hindi) "अच्छी" else "Good"; else -> if (hindi) "सामान्य" else "Fair" }) },
                            modifier = Modifier.padding(end = 4.dp))
                    }
                }
                if (type == "sale") OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                    label = { Text(if (hindi) "कीमत (₹)" else "Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                if (type == "swap") OutlinedTextField(value = swap, onValueChange = { swap = it.take(300) },
                    label = { Text(if (hindi) "किससे बदलना चाहेंगे?" else "What would you swap for?") })
                OutlinedTextField(value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(16) },
                    label = { Text(if (hindi) "संपर्क नंबर (+91 सहित)" else "Contact number (include +91)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(value = city, onValueChange = { city = it.take(80) },
                    label = { Text(if (hindi) "शहर" else "City") }, singleLine = true)
                OutlinedTextField(value = area, onValueChange = { area = it.take(100) },
                    label = { Text(if (hindi) "इलाका / कॉलोनी" else "Area / neighbourhood") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = title.trim().length >= 3 && phone.count { it.isDigit() } in 10..15 &&
                city.trim().length >= 2 && area.trim().length >= 2 &&
                (type != "sale" || (price.toDoubleOrNull() ?: -1.0) >= 0),
                onClick = { onSave(title, description, category, type, condition, price.toDoubleOrNull() ?: 0.0, swap, phone, city, area) }) {
                Text(if (hindi) "समीक्षा के लिए भेजें" else "Submit for review")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (hindi) "रद्द करें" else "Cancel") } }
    )
}

private fun categoryLabel(value: String, hindi: Boolean): String = if (!hindi) value else when(value) {
    "All" -> "सभी"
    "Electrician" -> "इलेक्ट्रीशियन"
    "Plumber" -> "प्लंबर"
    "Mason" -> "मिस्त्री"
    "Carpenter" -> "बढ़ई"
    "AC repair" -> "AC मरम्मत"
    "Driver" -> "ड्राइवर"
    else -> value
}

private fun verificationLabel(value: String, hindi: Boolean): String = if (!hindi) when(value) {
    "phone_verified" -> "Phone verified"
    "document_verified" -> "Documents checked"
    "admin_verified" -> "Admin verified"
    else -> "Unverified"
} else when(value) {
    "phone_verified" -> "मोबाइल सत्यापित"
    "document_verified" -> "दस्तावेज़ जाँचे"
    "admin_verified" -> "एडमिन सत्यापित"
    else -> "असत्यापित"
}

private fun readCurrentLocation(
    context: Context,
    onLocation: (Location) -> Unit,
    onFailure: (String) -> Unit
) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
    val cached = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
    if (cached != null && System.currentTimeMillis() - cached.time < 15 * 60 * 1000) {
        onLocation(cached)
        return
    }
    var completed = false
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!completed) {
                completed = true
                runCatching { manager.removeUpdates(this) }
                onLocation(location)
            }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    val started = providers.any { provider ->
        runCatching {
            manager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
            true
        }.getOrDefault(false)
    }
    if (!started) {
        onFailure("No location fix is available. Turn on device location and try again.")
    } else {
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            if (!completed) {
                completed = true
                runCatching { manager.removeUpdates(listener) }
                onFailure("Location is taking too long. Please try again.")
            }
        }, 15000L)
    }
}
