package com.parihardigitallabs.dailysathi

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private data class ShoppingEntry(val id: Long, val title: String, val done: Boolean)

@Composable
internal fun EverydayToolsScreen(hindi: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("daily_sathi_everyday_tools", 0) }
    var entries by remember {
        mutableStateOf(runCatching {
            val json = JSONArray(prefs.getString("shopping_items", "[]") ?: "[]")
            (0 until json.length()).map { i ->
                val item = json.getJSONObject(i)
                ShoppingEntry(item.optLong("id"), item.optString("title"), item.optBoolean("done"))
            }
        }.getOrDefault(emptyList()))
    }
    var newItem by remember { mutableStateOf("") }
    var selectedCalculator by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entries) {
        val json = JSONArray()
        entries.forEach { json.put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.done)) }
        prefs.edit().putString("shopping_items", json.toString()).apply()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 20.dp, 18.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(if (hindi) "रोज़मर्रा के टूल्स" else "Everyday tools",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (hindi) "छोटे काम, आसान समाधान। ये टूल्स ऑफलाइन काम करते हैं।"
                else "Small everyday problems, simple offline solutions.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(if (hindi) "कैलकुलेटर" else "Calculators", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolCard(if (hindi) "छूट कैलकुलेटर" else "Discount calculator",
                    if (hindi) "छूट के बाद कीमत जानें" else "Calculate the price after discount",
                    Icons.Default.Percent) { selectedCalculator = "discount" }
                ToolCard(if (hindi) "GST कैलकुलेटर" else "GST calculator",
                    if (hindi) "GST राशि और कुल कीमत निकालें" else "Calculate GST amount and total",
                    Icons.Default.Calculate) { selectedCalculator = "gst" }
                ToolCard(if (hindi) "प्रतिशत कैलकुलेटर" else "Percentage calculator",
                    if (hindi) "किसी संख्या का प्रतिशत निकालें" else "Find a percentage of an amount",
                    Icons.Default.Calculate) { selectedCalculator = "percentage" }
                ToolCard(if (hindi) "यूनिट कन्वर्टर" else "Unit converter",
                    if (hindi) "वज़न, दूरी और तरल माप बदलें" else "Convert weight, distance and liquid units",
                    Icons.Default.Straighten) { selectedCalculator = "units" }
            }
        }
        item {
            Text(if (hindi) "खरीदारी सूची" else "Shopping checklist",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newItem, onValueChange = { newItem = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text(if (hindi) "सामान जोड़ें" else "Add an item") }
                )
                IconButton(onClick = {
                    val title = newItem.trim()
                    if (title.isNotEmpty()) {
                        entries = entries + ShoppingEntry(System.currentTimeMillis(), title, false)
                        newItem = ""
                    }
                }, enabled = newItem.isNotBlank()) { Icon(Icons.Default.Add, contentDescription = if (hindi) "जोड़ें" else "Add") }
            }
            if (entries.isEmpty()) {
                Text(if (hindi) "आपकी सूची खाली है।" else "Your list is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(entries, key = { it.id }) { entry ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = entry.done, onCheckedChange = { checked ->
                        entries = entries.map { if (it.id == entry.id) it.copy(done = checked) else it }
                    })
                    Text(entry.title, Modifier.weight(1f), color = if (entry.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { entries = entries.filterNot { it.id == entry.id } }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = if (hindi) "हटाएँ" else "Remove")
                    }
                }
            }
        }
        item {
            Text(if (hindi) "आपातकालीन सहायता" else "Emergency help",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (hindi) "कॉल करने से पहले नंबर और उपलब्धता की पुष्टि करें। कॉल आपके फ़ोन डायलर में खुलेगी।"
                else "Check the number and local availability. Calls open in your phone dialer for confirmation.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EmergencyDialCard("112", if (hindi) "राष्ट्रीय आपातकालीन नंबर" else "National emergency number", hindi)
                EmergencyDialCard("108", if (hindi) "एम्बुलेंस सेवा" else "Ambulance service", hindi)
                EmergencyDialCard("101", if (hindi) "अग्निशमन सेवा" else "Fire service", hindi)
                EmergencyDialCard("100", if (hindi) "पुलिस सहायता" else "Police assistance", hindi)
            }
        }
        item {
            Text(if (hindi) "आपात स्थिति में स्थानीय उपलब्धता अलग हो सकती है। यह ऐप आपातकालीन सेवा का विकल्प नहीं है।"
                else "Local availability may vary. This app is not a replacement for emergency services.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    selectedCalculator?.let { type ->
        CalculatorDialog(type = type, hindi = hindi, onDismiss = { selectedCalculator = null })
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmergencyDialCard(number: String, description: String, hindi: Boolean) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Call, contentDescription = null)
        Text("  $number  ·  $description")
    }
}

@Composable
private fun CalculatorDialog(type: String, hindi: Boolean, onDismiss: () -> Unit) {
    var first by remember(type) { mutableStateOf("") }
    var second by remember(type) { mutableStateOf("") }
    val a = first.toDoubleOrNull()
    val b = second.toDoubleOrNull()
    val result = when (type) {
        "discount" -> if (a != null && b != null && a >= 0 && b in 0.0..100.0) {
            String.format(Locale.getDefault(), "%.2f", a * (1 - b / 100))
        } else null
        "gst" -> if (a != null && b != null && a >= 0 && b in 0.0..100.0) {
            String.format(Locale.getDefault(), "GST %.2f · Total %.2f", a * b / 100, a * (1 + b / 100))
        } else null
        "percentage" -> if (a != null && b != null) String.format(Locale.getDefault(), "%.2f", a * b / 100) else null
        "units" -> if (a != null && a >= 0) String.format(Locale.getDefault(),
            "%.3f g · %.3f m · %.3f mL", a * 1000, a * 1000, a * 1000) else null
        else -> null
    }
    val title = when (type) {
        "discount" -> if (hindi) "छूट कैलकुलेटर" else "Discount calculator"
        "gst" -> if (hindi) "GST कैलकुलेटर" else "GST calculator"
        "percentage" -> if (hindi) "प्रतिशत कैलकुलेटर" else "Percentage calculator"
        else -> if (hindi) "यूनिट कन्वर्टर" else "Unit converter"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = {
                    Text(if (type == "units") if (hindi) "किलोग्राम / किलोमीटर / लीटर" else "Kilograms / kilometres / litres"
                        else if (hindi) "राशि" else "Amount")
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                if (type != "units") OutlinedTextField(second, { second = it },
                    label = { Text(if (type == "percentage") if (hindi) "प्रतिशत" else "Percentage" else if (hindi) "दर (%)" else "Rate (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                if (result != null) Text(if (type == "discount") (if (hindi) "छूट के बाद कीमत: " else "Price after discount: ") + result
                    else if (type == "gst") result
                    else if (type == "units") (if (hindi) "ग्राम · मीटर · मिलीलीटर: " else "Grams · metres · millilitres: ") + result
                    else (if (hindi) "परिणाम: " else "Result: ") + result,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (hindi) "बंद करें" else "Close") } }
    )
}
