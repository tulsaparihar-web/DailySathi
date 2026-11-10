package com.parihardigitallabs.dailysathi

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parihardigitallabs.dailysathi.data.DailySathiDatabase
import com.parihardigitallabs.dailysathi.data.Reminder
import kotlinx.coroutines.launch

private val DailyBlue = Color(0xFF2876C7)
private val Navy = Color(0xFF123B50)
private val Teal = Color(0xFF168D87)
private val SoftBg = Color(0xFFF5F8FA)
private val Slate = Color(0xFF253746)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = DailySathiDatabase.getInstance(applicationContext)
        setContent { DailySathiTheme { DailySathiApp(database) } }
    }
}

@Composable
private fun DailySathiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DailyBlue,
            secondary = Teal,
            background = SoftBg,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Slate,
            onSurface = Slate
        ),
        content = content
    )
}

private enum class MainTab(val label: String) {
    HOME("Home"), CALENDAR("Calendar"), TASKS("Tasks"), SERVICES("Services"), SETTINGS("Settings")
}

@Composable
private fun DailySathiApp(database: DailySathiDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reminders by database.reminderDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var showAdd by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    val preferences = remember { context.getSharedPreferences("daily_sathi_preferences", android.content.Context.MODE_PRIVATE) }
    var hindi by remember { mutableStateOf(preferences.getBoolean("hindi_language", false)) }
    LaunchedEffect(hindi) { preferences.edit().putBoolean("hindi_language", hindi).apply() }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var restorePreview by remember { mutableStateOf<List<Reminder>?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = ReminderBackup.encode(reminders)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    ?: error("Unable to write backup file")
                backupMessage = if (hindi) "बैकअप सफलतापूर्वक सेव हुआ।" else "Backup saved successfully."
            }.onFailure {
                backupMessage = (if (hindi) "बैकअप विफल: " else "Backup failed: ") + (it.message ?: "Unknown error")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to read backup file")
                restorePreview = ReminderBackup.decode(json)
            }.onFailure {
                backupMessage = (if (hindi) "रीस्टोर विफल: " else "Restore failed: ") + (it.message ?: "Invalid backup")
            }
        }
    }

    Scaffold(
        containerColor = SoftBg,
        floatingActionButton = {
            if (tab == MainTab.HOME || tab == MainTab.TASKS) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = DailyBlue,
                    contentColor = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "Add") }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                MainTab.entries.forEach { item ->
                    val icon = when (item) {
                        MainTab.HOME -> Icons.Default.Home
                        MainTab.CALENDAR -> Icons.Default.CalendarMonth
                        MainTab.TASKS -> Icons.Default.CheckCircle
                        MainTab.SERVICES -> Icons.Default.Handyman
                        MainTab.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(if (hindi) itemHindi(item) else item.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.HOME -> HomeScreen(
                reminders, hindi, Modifier.padding(padding),
                onAdd = { showAdd = true },
                onServices = { tab = MainTab.SERVICES },
                onToggle = { item ->
                    scope.launch {
                        database.reminderDao().update(item.copy(isCompleted = !item.isCompleted))
                        if (item.isCompleted) item.dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, item.id, it) }
                        else ReminderAlarmScheduler.cancel(context, item.id)
                    }
                }
            )
            MainTab.CALENDAR -> CalendarScreen(
                reminders = reminders, hindi = hindi, modifier = Modifier.padding(padding),
                onToggle = { item ->
                    scope.launch {
                        database.reminderDao().update(item.copy(isCompleted = !item.isCompleted))
                        if (item.isCompleted) item.dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, item.id, it) }
                        else ReminderAlarmScheduler.cancel(context, item.id)
                    }
                }
            )
            MainTab.TASKS -> TasksScreen(
                reminders, hindi, Modifier.padding(padding),
                onToggle = { item ->
                    scope.launch {
                        database.reminderDao().update(item.copy(isCompleted = !item.isCompleted))
                        if (item.isCompleted) item.dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, item.id, it) }
                        else ReminderAlarmScheduler.cancel(context, item.id)
                    }
                },
                onDelete = { item -> scope.launch { database.reminderDao().delete(item); ReminderAlarmScheduler.cancel(context, item.id) } },
                onEdit = { editingReminder = it }
            )
            MainTab.SERVICES -> LocalServicesScreen(hindi, Modifier.padding(padding))
            MainTab.SETTINGS -> SettingsScreen(
                hindi, { hindi = it }, Modifier.padding(padding),
                onExport = { exportLauncher.launch("DailySathi-Backup.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) }
            )
        }
    }


    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text(if (hindi) "बैकअप और रीस्टोर" else "Backup & Restore") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { backupMessage = null }) { Text("OK") } }
        )
    }
    restorePreview?.let { restored ->
        AlertDialog(
            onDismissRequest = { restorePreview = null },
            title = { Text(if (hindi) "बैकअप रीस्टोर करें?" else "Restore this backup?") },
            text = {
                Text(if (hindi) "यह डिवाइस के मौजूदा " + reminders.size + " रिमाइंडर हटाकर " + restored.size + " बैकअप रिमाइंडर से बदल देगा।"
                    else "This will replace the " + reminders.size + " reminders on this device with " + restored.size + " reminders from the backup.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val itemsToRestore = restored
                    restorePreview = null
                    scope.launch {
                        runCatching {
                            // Commit the database replacement first; if it fails, existing alarms remain intact.
                            database.reminderDao().replaceAll(itemsToRestore)
                            reminders.forEach { ReminderAlarmScheduler.cancel(context, it.id) }
                            itemsToRestore.filter { !it.isCompleted }.forEach { item ->
                                item.dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, item.id, it) }
                            }
                            backupMessage = if (hindi) itemsToRestore.size.toString() + " रिमाइंडर रीस्टोर हुए।"
                                else "Restored " + itemsToRestore.size + " reminders successfully."
                        }.onFailure {
                            backupMessage = (if (hindi) "रीस्टोर विफल: " else "Restore failed: ") + (it.message ?: "Database error")
                        }
                    }
                }) { Text(if (hindi) "रीस्टोर करें" else "Restore") }
            },
            dismissButton = {
                TextButton(onClick = { restorePreview = null }) { Text(if (hindi) "रद्द करें" else "Cancel") }
            }
        )
    }
    if (showAdd) {
        AddReminderDialog(
            hindi = hindi,
            onDismiss = { showAdd = false },
            onSave = { title, note, dueAtMillis, recurrence, category, providerName, location, preparationNotes ->
                scope.launch {
                    val id = database.reminderDao().insert(Reminder(title = title, note = note, dueAtMillis = dueAtMillis, recurrence = recurrence, category = category, providerName = providerName, location = location, preparationNotes = preparationNotes))
                    dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, id, it) }
                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        (context as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 501)
                    }
                    showAdd = false
                }
            }
        )
    }
    editingReminder?.let { item ->
        AddReminderDialog(
            hindi = hindi,
            initialReminder = item,
            onDismiss = { editingReminder = null },
            onSave = { title, note, dueAtMillis, recurrence, category, providerName, location, preparationNotes ->
                scope.launch {
                    val updated = item.copy(title = title, note = note, dueAtMillis = dueAtMillis, recurrence = recurrence, category = category, providerName = providerName, location = location, preparationNotes = preparationNotes)
                    database.reminderDao().update(updated)
                    ReminderAlarmScheduler.cancel(context, item.id)
                    if (!updated.isCompleted) dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, item.id, it) }
                    editingReminder = null
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    reminders: List<Reminder>,
    hindi: Boolean,
    modifier: Modifier,
    onAdd: () -> Unit,
    onServices: () -> Unit,
    onToggle: (Reminder) -> Unit
) {
    val pending = reminders.count { !it.isCompleted }
    val completed = reminders.count { it.isCompleted }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("PARIHAR DIGITAL LABS", color = Teal, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("DailySathi", color = Navy, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (hindi) "आपका हर दिन, व्यवस्थित।" else "Your day, organized.",
                        color = Slate.copy(alpha = .72f), fontSize = 14.sp)
                }
                Surface(shape = CircleShape, color = Color.White, shadowElevation = 2.dp) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = Navy)
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Navy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hindi) "नमस्ते!" else "Hello there!", color = Color.White.copy(alpha = .8f))
                    Text(
                        if (hindi) "आज की छोटी बातें,\nकल की बड़ी राहत।" else "Make room for what\nmatters today.",
                        color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp
                    )
                    Text(
                        if (hindi) "$pending रिमाइंडर बाकी हैं" else "$pending reminders pending",
                        color = Color.White.copy(alpha = .8f), fontSize = 13.sp
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(if (hindi) "बाकी" else "Pending", pending.toString(),
                    Icons.Default.NotificationsActive, DailyBlue, Modifier.weight(1f))
                SummaryCard(if (hindi) "पूरे हुए" else "Completed", completed.toString(),
                    Icons.Default.TaskAlt, Teal, Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (hindi) "त्वरित पहुँच" else "Quick access",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
                TextButton(onClick = onAdd) { Text(if (hindi) "जोड़ें +" else "Add +") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickCard(if (hindi) "रिमाइंडर" else "Reminders",
                        if (hindi) "याद रखें" else "Stay on track",
                        Icons.Default.Notifications, DailyBlue, Modifier.weight(1f), onAdd)
                    QuickCard(if (hindi) "स्थानीय सेवाएँ" else "Local Services",
                        if (hindi) "कारीगर और मददगार खोजें" else "Find trusted local help",
                        Icons.Default.Handyman, Teal, Modifier.weight(1f), onServices)
                }
                Text(if (hindi) "इलेक्ट्रीशियन, प्लंबर, मिस्त्री, बढ़ई, सफाई सहायक और अन्य सेवाओं के लिए Services टैब खोलें।"
                    else "Find electricians, plumbers, masons, carpenters, cleaners and more in the Services tab.",
                    color = Slate.copy(alpha = .65f), fontSize = 12.sp)
            }
        }
        item { Text(if (hindi) "आपके रिमाइंडर" else "Your reminders",
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy) }
        if (reminders.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null,
                            tint = DailyBlue, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(if (hindi) "अभी कोई रिमाइंडर नहीं" else "Nothing on your list yet",
                            fontWeight = FontWeight.SemiBold)
                        Text(if (hindi) "अपना पहला रिमाइंडर जोड़ें।" else "Add your first reminder to get started.",
                            color = Slate.copy(alpha = .65f), fontSize = 13.sp)
                        TextButton(onClick = onAdd) { Text(if (hindi) "रिमाइंडर जोड़ें" else "Add a reminder") }
                    }
                }
            }
        } else {
            items(reminders.take(5), key = { it.id }) { reminder -> ReminderRow(reminder, hindi, onToggle) }
        }
        item {
            Text("A product of Parihar Digital Labs", color = Slate.copy(alpha = .5f),
                fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryCard(
    title: String, value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, modifier: Modifier
) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.White) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = color.copy(alpha = .12f)) {
                Icon(icon, contentDescription = null, tint = color,
                    modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Column {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(title, fontSize = 12.sp, color = Slate.copy(alpha = .65f))
            }
        }
    }
}

@Composable
private fun QuickCard(
    title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, modifier: Modifier, onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = .12f)) {
                Icon(icon, contentDescription = null, tint = color,
                    modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                Text(subtitle, fontSize = 10.sp, color = Slate.copy(alpha = .65f))
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, hindi: Boolean, onToggle: (Reminder) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = reminder.isCompleted, onCheckedChange = { onToggle(reminder) })
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontWeight = FontWeight.SemiBold,
                    color = if (reminder.isCompleted) Slate.copy(alpha = .45f) else Navy)
                if (reminder.note.isNotBlank()) {
                    Text(reminder.note, fontSize = 12.sp, color = Slate.copy(alpha = .6f))
                }
            }
            Text(
                if (reminder.isCompleted) (if (hindi) "पूर्ण" else "Done") else (if (hindi) "बाकी" else "Pending"),
                fontSize = 11.sp, color = if (reminder.isCompleted) Teal else DailyBlue
            )
        }
    }
}

@Composable
private fun ReminderExtraDetails(reminder: Reminder, hindi: Boolean) {
    val categoryLabel = when (reminder.category) {
        "Health" -> if (hindi) "स्वास्थ्य" else "Health"
        "Appointment" -> if (hindi) "अपॉइंटमेंट" else "Appointment"
        "Medication" -> if (hindi) "दवा" else "Medication"
        else -> if (hindi) "व्यक्तिगत" else "Personal"
    }
    if (reminder.category != "Personal" || reminder.providerName.isNotBlank() ||
        reminder.location.isNotBlank() || reminder.preparationNotes.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(categoryLabel, color = Teal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            if (reminder.providerName.isNotBlank()) {
                Text((if (hindi) "डॉक्टर / क्लिनिक / दवा: " else "Doctor / clinic / medicine: ") + reminder.providerName,
                    color = Slate.copy(alpha = .72f), fontSize = 11.sp)
            }
            if (reminder.location.isNotBlank()) {
                Text((if (hindi) "स्थान: " else "Location: ") + reminder.location,
                    color = Slate.copy(alpha = .72f), fontSize = 11.sp)
            }
            if (reminder.preparationNotes.isNotBlank()) {
                Text((if (hindi) "नोट: " else "Notes: ") + reminder.preparationNotes,
                    color = Slate.copy(alpha = .72f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TasksScreen(
    reminders: List<Reminder>, hindi: Boolean, modifier: Modifier,
    onToggle: (Reminder) -> Unit, onDelete: (Reminder) -> Unit, onEdit: (Reminder) -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)) {
        Text(if (hindi) "कार्य और रिमाइंडर" else "Tasks & reminders",
            fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(if (hindi) "अपनी सूची व्यवस्थित रखें।" else "Keep your list organized.",
            color = Slate.copy(alpha = .65f))
        Spacer(Modifier.height(16.dp))
        if (reminders.isEmpty()) {
            Text(if (hindi) "अभी कोई कार्य नहीं। + दबाकर जोड़ें।" else "No tasks yet. Tap + to add one.",
                color = Slate.copy(alpha = .65f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(reminders, key = { it.id }) { reminder ->
                    Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(reminder.isCompleted, { onToggle(reminder) })
                            Column(Modifier.weight(1f)) {
                                Text(reminder.title, fontWeight = FontWeight.Medium, color = Navy)
                                reminder.dueAtMillis?.let {
                                    Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it)),
                                        fontSize = 11.sp, color = Slate.copy(alpha = .6f))
                                }
                                ReminderExtraDetails(reminder, hindi)
                            }
                            IconButton(onClick = { onEdit(reminder) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DailyBlue)
                            }
                            IconButton(onClick = { onDelete(reminder) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CalendarScreen(reminders: List<Reminder>, hindi: Boolean, modifier: Modifier, onToggle: (Reminder) -> Unit) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dated = reminders.filter { it.dueAtMillis != null }
        .sortedBy { it.dueAtMillis }
        .groupBy { formatter.format(Date(it.dueAtMillis!!)) }
    val undated = reminders.filter { it.dueAtMillis == null }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text(if (hindi) "कैलेंडर" else "Calendar", fontSize = 26.sp,
                fontWeight = FontWeight.Bold, color = Navy)
            Text(if (hindi) "आपकी तारीख़ वाले रिमाइंडर" else "Your scheduled reminders",
                color = Slate.copy(alpha = .65f))
        }
        if (dated.isEmpty()) {
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null,
                            tint = DailyBlue, modifier = Modifier.size(38.dp))
                        Text(if (hindi) "अभी कोई तारीख़ तय नहीं" else "No scheduled reminders yet",
                            fontWeight = FontWeight.SemiBold)
                        Text(if (hindi) "रिमाइंडर में तारीख़ और समय जोड़ें।" else "Add a date and time to any reminder.",
                            color = Slate.copy(alpha = .65f), fontSize = 12.sp)
                    }
                }
            }
        } else {
            dated.forEach { (day, dayItems) ->
                item(key = "heading-" + day) {
                    val firstDue = dayItems.firstNotNullOfOrNull { it.dueAtMillis } ?: 0L
                    val dayStart = Calendar.getInstance().apply {
                        timeInMillis = firstDue
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val heading = when {
                        dayStart < todayStart -> if (hindi) "बीती तारीख़ · " + day else "Overdue · " + day
                        dayStart == todayStart -> if (hindi) "आज · " + day else "Today · " + day
                        else -> day
                    }
                    Text(heading, color = if (dayStart < todayStart) Color(0xFFB45309) else Navy,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                items(dayItems, key = { "calendar-" + it.id }) { item ->
                    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(item.isCompleted, { onToggle(item) })
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold, color = Navy)
                                Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.dueAtMillis!!)),
                                    fontSize = 12.sp, color = Slate.copy(alpha = .65f))
                                ReminderExtraDetails(item, hindi)
                            }
                        }
                    }
                }
            }
        }
        if (undated.isNotEmpty()) {
            item { Text(if (hindi) "बिना तारीख़ वाले कार्य" else "Unscheduled tasks",
                color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            items(undated, key = { "unscheduled-" + it.id }) { item ->
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.isCompleted, { onToggle(item) })
                        Text(item.title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private data class LocalServiceProvider(
    val id: Long,
    val name: String,
    val service: String,
    val phone: String,
    val area: String,
    val details: String
)

@Composable
private fun LocalServicesScreen(hindi: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("daily_sathi_services", android.content.Context.MODE_PRIVATE) }
    var area by remember { mutableStateOf("") }
    var providerQuery by remember { mutableStateOf("") }
    var providerCategory by remember { mutableStateOf("All") }
    var selectedMode by remember { mutableStateOf("MAPS") }
    var showAddProvider by remember { mutableStateOf(false) }
    var providers by remember {
        mutableStateOf(runCatching {
            val saved = org.json.JSONArray(preferences.getString("providers", "[]") ?: "[]")
            (0 until saved.length()).map { index ->
                val item = saved.getJSONObject(index)
                LocalServiceProvider(
                    item.getLong("id"), item.getString("name"), item.getString("service"),
                    item.getString("phone"), item.optString("area"), item.optString("details")
                )
            }
        }.getOrDefault(emptyList()))
    }
    val categories = listOf(
        Triple("Electrician", "इलेक्ट्रीशियन", Icons.Default.ElectricalServices),
        Triple("Plumber", "प्लंबर", Icons.Default.Plumbing),
        Triple("Mason", "राजमिस्त्री", Icons.Default.Construction),
        Triple("Carpenter", "बढ़ई", Icons.Default.Build),
        Triple("Painter", "पेंटर", Icons.Default.FormatPaint),
        Triple("House cleaner / Helper", "घरेलू सहायक", Icons.Default.CleaningServices),
        Triple("AC repair", "AC रिपेयर", Icons.Default.AcUnit),
        Triple("Appliance repair", "उपकरण मरम्मत", Icons.Default.HomeRepairService),
        Triple("Driver", "ड्राइवर", Icons.Default.DirectionsCar)
    )
    val filteredProviders = providers.filter { provider ->
        (providerCategory == "All" || provider.service.equals(providerCategory, ignoreCase = true)) &&
            (providerQuery.isBlank() || listOf(provider.name, provider.service, provider.area, provider.details)
                .any { it.contains(providerQuery.trim(), ignoreCase = true) })
    }
    fun saveProviders(items: List<LocalServiceProvider>) {
        providers = items
        val json = org.json.JSONArray()
        items.forEach { provider ->
            json.put(org.json.JSONObject()
                .put("id", provider.id).put("name", provider.name)
                .put("service", provider.service).put("phone", provider.phone)
                .put("area", provider.area).put("details", provider.details))
        }
        preferences.edit().putString("providers", json.toString()).apply()
    }
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Text(if (hindi) "स्थानीय सेवाएँ" else "Local Services",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(if (hindi) "मैप्स पर खोजें या अपनी स्थानीय सेवा सूची बनाएँ।" else "Search on Maps or build your own local provider list.",
            color = Slate.copy(alpha = .68f), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedMode == "MAPS", onClick = { selectedMode = "MAPS" },
                label = { Text(if (hindi) "मैप्स पर खोजें" else "Search Maps") },
                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) })
            FilterChip(selected = selectedMode == "DIRECTORY", onClick = { selectedMode = "DIRECTORY" },
                label = { Text(if (hindi) "प्रदाता डायरेक्टरी" else "Provider Directory") },
                leadingIcon = { Icon(Icons.Default.People, contentDescription = null) })
        }
        if (selectedMode == "MAPS") {
            OutlinedTextField(
                value = area, onValueChange = { area = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (hindi) "इलाका / शहर (वैकल्पिक)" else "Area / city (optional)") },
                placeholder = { Text(if (hindi) "खाली छोड़ें और आसपास खोजें" else "Leave blank to search nearby") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
            )
            Spacer(Modifier.height(8.dp))
            Text(if (hindi) "सेवा चुनें" else "Choose a service",
                color = Navy, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 20.dp)) {
                items(categories) { category ->
                    Surface(
                        onClick = {
                            val searchText = category.first + " near " + area.ifBlank { "me" }
                            val url = "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(searchText)
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        shape = RoundedCornerShape(16.dp), color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = Teal.copy(alpha = .12f)) {
                                Icon(category.third, contentDescription = null, tint = Teal,
                                    modifier = Modifier.padding(10.dp).size(23.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(if (hindi) category.second else category.first,
                                    color = Navy, fontWeight = FontWeight.SemiBold)
                                Text(if (hindi) "नक्शे पर आसपास खोजें" else "Search nearby on Maps",
                                    color = Slate.copy(alpha = .6f), fontSize = 11.sp)
                            }
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = DailyBlue)
                        }
                    }
                }
                item {
                    Text(if (hindi) "खोज Google Maps में खुलेगी। लिस्टिंग और रेटिंग Maps पर निर्भर हैं।"
                        else "Search opens in Google Maps. Listings and ratings are provided by Maps.",
                        color = Slate.copy(alpha = .55f), fontSize = 11.sp)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (hindi) "आपकी सेवा सूची" else "Your provider directory",
                        color = Navy, fontWeight = FontWeight.SemiBold)
                    Text(if (hindi) "इस डिवाइस पर सेव प्रदाता" else "Providers saved on this device",
                        color = Slate.copy(alpha = .6f), fontSize = 11.sp)
                }
                Button(onClick = { showAddProvider = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(if (hindi) "जोड़ें" else "Add")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = providerQuery, onValueChange = { providerQuery = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (hindi) "नाम, सेवा या इलाका खोजें" else "Search name, service or area") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = providerCategory == "All", onClick = { providerCategory = "All" },
                    label = { Text(if (hindi) "सभी" else "All") })
                categories.take(3).forEach { category ->
                    FilterChip(selected = providerCategory == category.first,
                        onClick = { providerCategory = if (providerCategory == category.first) "All" else category.first },
                        label = { Text(if (hindi) category.second else category.first) })
                }
            }
            if (providers.isEmpty()) {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Handyman, contentDescription = null, tint = Teal,
                            modifier = Modifier.size(40.dp))
                        Text(if (hindi) "अभी कोई सेवा प्रदाता नहीं" else "No providers added yet",
                            color = Navy, fontWeight = FontWeight.SemiBold)
                        Text(if (hindi) "स्थानीय सेवा प्रदाता की जानकारी जोड़कर अपनी सूची बनाएँ।"
                            else "Add local professionals to create a handy contact list.",
                            color = Slate.copy(alpha = .65f), fontSize = 12.sp)
                        OutlinedButton(onClick = { showAddProvider = true }) {
                            Text(if (hindi) "पहला प्रदाता जोड़ें" else "Add your first provider")
                        }
                    }
                }
            } else {
                if (filteredProviders.isEmpty()) {
                    Text(if (hindi) "कोई मिलान नहीं मिला।" else "No matching providers found.",
                        color = Slate.copy(alpha = .65f), fontSize = 13.sp)
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filteredProviders, key = { it.id }) { provider ->
                        Surface(color = Color.White, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(provider.name, color = Navy, fontWeight = FontWeight.Bold)
                                        Text(provider.service, color = Teal, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { saveProviders(providers.filterNot { it.id == provider.id }) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = if (hindi) "हटाएँ" else "Delete",
                                            tint = Color(0xFFB45309))
                                    }
                                }
                                if (provider.area.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null,
                                            tint = Slate.copy(alpha = .6f), modifier = Modifier.size(15.dp))
                                        Text(provider.area, color = Slate.copy(alpha = .7f), fontSize = 12.sp)
                                    }
                                }
                                if (provider.details.isNotBlank()) Text(provider.details, color = Slate, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + provider.phone)))
                                    }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Call, contentDescription = null)
                                        Spacer(Modifier.width(5.dp))
                                        Text(if (hindi) "कॉल" else "Call")
                                    }
                                    OutlinedButton(onClick = {
                                        val digits = provider.phone.filter { it.isDigit() }
                                        val url = "https://wa.me/$digits"
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Chat, contentDescription = null)
                                        Spacer(Modifier.width(5.dp))
                                        Text("WhatsApp")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(if (hindi) "ध्यान दें: ये प्रविष्टियाँ केवल इसी डिवाइस पर सेव होती हैं; अभी सार्वजनिक ऑनलाइन डायरेक्टरी नहीं है।"
                else "Note: These entries are stored on this device only; a shared online directory is not connected yet.",
                color = Slate.copy(alpha = .55f), fontSize = 11.sp)
        }
    }
    if (showAddProvider) {
        AddLocalProviderDialog(hindi = hindi, categories = categories.map { it.first }, onDismiss = { showAddProvider = false }) { name, service, phone, providerArea, details ->
            saveProviders(providers + LocalServiceProvider(
                id = System.currentTimeMillis(), name = name, service = service,
                phone = phone, area = providerArea, details = details
            ))
            showAddProvider = false
        }
    }
}

@Composable
private fun AddLocalProviderDialog(
    hindi: Boolean, categories: List<String>, onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf(categories.first()) }
    var phone by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hindi) "सेवा प्रदाता जोड़ें" else "Add a service provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(if (hindi) "नाम" else "Provider name") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(service)
                    }
                    DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        categories.forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = {
                                service = item
                                categoryExpanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(value = phone, onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(16) },
                    label = { Text(if (hindi) "मोबाइल नंबर" else "Phone number") }, singleLine = true)
                OutlinedTextField(value = area, onValueChange = { area = it },
                    label = { Text(if (hindi) "इलाका / शहर" else "Area / city") }, singleLine = true)
                OutlinedTextField(value = details, onValueChange = { details = it },
                    label = { Text(if (hindi) "विवरण (वैकल्पिक)" else "Details (optional)") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), service, phone.trim(), area.trim(), details.trim()) },
                enabled = name.isNotBlank() && phone.count { it.isDigit() } >= 10) {
                Text(if (hindi) "सेव करें" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (hindi) "रद्द करें" else "Cancel") }
        }
    )
}

@Composable
private fun PlaceholderScreen(
    title: String, description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier
) {
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = DailyBlue, modifier = Modifier.size(48.dp))
                Text(description, color = Slate, fontSize = 14.sp)
                Text("Coming in the next milestone", color = Teal,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    hindi: Boolean, onLanguageChange: (Boolean) -> Unit, modifier: Modifier,
    onExport: () -> Unit, onImport: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(if (hindi) "सेटिंग्स" else "Settings",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (hindi) "ऐप की भाषा" else "App language", fontWeight = FontWeight.SemiBold)
                    Text(if (hindi) "हिंदी" else "English", color = Slate.copy(alpha = .6f), fontSize = 12.sp)
                }
                Switch(checked = hindi, onCheckedChange = onLanguageChange)
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (hindi) "बैकअप और रीस्टोर" else "Backup & Restore",
                    fontWeight = FontWeight.SemiBold, color = Navy)
                Text(if (hindi) "अपने रिमाइंडर और अपॉइंटमेंट का JSON बैकअप सुरक्षित रखें।"
                    else "Save your reminders and appointments to a JSON backup file.",
                    color = Slate.copy(alpha = .65f), fontSize = 12.sp)
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hindi) "बैकअप एक्सपोर्ट करें" else "Export backup")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hindi) "बैकअप इम्पोर्ट करें" else "Restore from backup")
                }
                Text(if (hindi) "रीस्टोर करने पर मौजूदा रिमाइंडर बदल जाएंगे।"
                    else "Restoring a backup replaces the reminders currently on this device.",
                    color = Color(0xFFB45309), fontSize = 11.sp)
            }
        }
        Text("Parihar Digital Labs", fontWeight = FontWeight.Bold, color = Navy)
        Text("Technology that makes life easier.", color = Slate.copy(alpha = .65f))
        Text("DailySathi v0.1.0", color = Slate.copy(alpha = .5f), fontSize = 12.sp)
    }
}

@Composable
private fun AddReminderDialog(
    hindi: Boolean, initialReminder: Reminder? = null,
    onDismiss: () -> Unit, onSave: (String, String, Long?, String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialReminder?.title.orEmpty()) }
    var note by remember { mutableStateOf(initialReminder?.note.orEmpty()) }
    var dueAtMillis by remember { mutableStateOf(initialReminder?.dueAtMillis) }
    var recurrence by remember { mutableStateOf(initialReminder?.recurrence ?: "NONE") }
    var category by remember { mutableStateOf(initialReminder?.category ?: "Personal") }
    var providerName by remember { mutableStateOf(initialReminder?.providerName.orEmpty()) }
    var appointmentLocation by remember { mutableStateOf(initialReminder?.location.orEmpty()) }
    var preparationNotes by remember { mutableStateOf(initialReminder?.preparationNotes.orEmpty()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    fun chooseDateTime() {
        val calendar = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        DatePickerDialog(context, { _, year, month, day ->
            calendar.set(Calendar.YEAR, year); calendar.set(Calendar.MONTH, month); calendar.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(context, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute)
                dueAtMillis = calendar.timeInMillis
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialReminder == null) { if (hindi) "नया रिमाइंडर" else "New reminder" } else { if (hindi) "रिमाइंडर संपादित करें" else "Edit reminder" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(if (hindi) "क्या याद रखना है?" else "What do you need to remember?") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(if (hindi) "विवरण (वैकल्पिक)" else "Note (optional)") },
                    minLines = 2
                )
                Box {
                    OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (hindi) "श्रेणी: " else "Category: ") + when (category) {
                            "Health" -> if (hindi) "स्वास्थ्य" else "Health"
                            "Appointment" -> if (hindi) "अपॉइंटमेंट" else "Appointment"
                            "Medication" -> if (hindi) "दवा" else "Medication"
                            else -> if (hindi) "व्यक्तिगत" else "Personal"
                        })
                    }
                    DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        listOf("Personal", "Health", "Appointment", "Medication").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(when (option) {
                                    "Health" -> if (hindi) "स्वास्थ्य" else "Health"
                                    "Appointment" -> if (hindi) "अपॉइंटमेंट" else "Appointment"
                                    "Medication" -> if (hindi) "दवा" else "Medication"
                                    else -> if (hindi) "व्यक्तिगत" else "Personal"
                                }) },
                                onClick = { category = option; categoryExpanded = false }
                            )
                        }
                    }
                }
                if (category == "Health" || category == "Appointment" || category == "Medication") {
                    OutlinedTextField(
                        value = providerName, onValueChange = { providerName = it },
                        label = { Text(if (hindi) "डॉक्टर / क्लिनिक / दवा का नाम" else "Doctor / clinic / medicine name (optional)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = appointmentLocation, onValueChange = { appointmentLocation = it },
                        label = { Text(if (hindi) "स्थान / क्लिनिक का पता" else "Location / clinic address (optional)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = preparationNotes, onValueChange = { preparationNotes = it },
                        label = { Text(if (hindi) "तैयारी / विशेष नोट्स" else "Preparation / extra notes (optional)") },
                        minLines = 2
                    )
                }
                OutlinedButton(onClick = { chooseDateTime() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(dueAtMillis?.let { dateFormatter.format(Date(it)) }
                        ?: if (hindi) "तारीख और समय चुनें (वैकल्पिक)" else "Add date & time (optional)")
                }
                if (dueAtMillis != null) {
                    TextButton(onClick = { dueAtMillis = null }) {
                        Text(if (hindi) "तारीख हटाएँ" else "Remove date")
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { recurrenceExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = dueAtMillis != null
                    ) {
                        Text(
                            (if (hindi) "दोहराव: " else "Repeat: ") + when (recurrence) {
                                "DAILY" -> if (hindi) "हर दिन" else "Daily"
                                "WEEKLY" -> if (hindi) "हर सप्ताह" else "Weekly"
                                "MONTHLY" -> if (hindi) "हर महीने" else "Monthly"
                                else -> if (hindi) "नहीं" else "Never"
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = recurrenceExpanded,
                        onDismissRequest = { recurrenceExpanded = false }
                    ) {
                        listOf("NONE", "DAILY", "WEEKLY", "MONTHLY").forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (option) {
                                        "DAILY" -> if (hindi) "हर दिन" else "Daily"
                                        "WEEKLY" -> if (hindi) "हर सप्ताह" else "Weekly"
                                        "MONTHLY" -> if (hindi) "हर महीने" else "Monthly"
                                        else -> if (hindi) "दोहराएँ नहीं" else "Does not repeat"
                                    })
                                },
                                onClick = { recurrence = option; recurrenceExpanded = false }
                            )
                        }
                    }
                }
                if (dueAtMillis == null && recurrence != "NONE") {
                    Text(if (hindi) "दोहराव के लिए पहले तारीख़ और समय चुनें।" else "Choose a date and time to enable repeating reminders.",
                        color = Color(0xFFB45309), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), note.trim(), dueAtMillis, if (dueAtMillis == null) "NONE" else recurrence, category, providerName.trim(), appointmentLocation.trim(), preparationNotes.trim()) },
                enabled = title.isNotBlank()
            ) { Text(if (hindi) "सहेजें" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (hindi) "रद्द करें" else "Cancel") }
        }
    )
}

private fun itemHindi(tab: MainTab): String = when (tab) {
    MainTab.HOME -> "होम"
    MainTab.CALENDAR -> "कैलेंडर"
    MainTab.TASKS -> "कार्य"
    MainTab.SERVICES -> "सेवाएँ"
    MainTab.SETTINGS -> "सेटिंग्स"
}
