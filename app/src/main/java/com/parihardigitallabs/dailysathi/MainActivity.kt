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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parihardigitallabs.dailysathi.data.DailySathiDatabase
import com.parihardigitallabs.dailysathi.data.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        setContent {
            DailySathiTheme {
                DailySathiApp(database, openCalendarOnLaunch = intent.getBooleanExtra("open_calendar", false))
            }
        }
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
    HOME("Home"), CALENDAR("Calendar"), TASKS("Tasks"), SERVICES("Services"), TOOLS("Tools"), SETTINGS("Settings")
}

@Composable
private fun DailySathiApp(database: DailySathiDatabase, openCalendarOnLaunch: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reminders by database.reminderDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember {
        mutableStateOf(if (openCalendarOnLaunch) MainTab.CALENDAR else MainTab.HOME)
    }
    var showAdd by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var reminderPendingDelete by remember { mutableStateOf<Reminder?>(null) }
    val preferences = remember { context.getSharedPreferences("daily_sathi_preferences", android.content.Context.MODE_PRIVATE) }
    var hindi by remember { mutableStateOf(preferences.getBoolean("hindi_language", false)) }
    LaunchedEffect(hindi) { preferences.edit().putBoolean("hindi_language", hindi).apply() }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var restorePreview by remember { mutableStateOf<DailySathiBackup?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val providerPrefs = context.getSharedPreferences("daily_sathi_services", android.content.Context.MODE_PRIVATE)
                val providerList = ReminderBackup.decodeProviders(
                    providerPrefs.getString("providers", "[]") ?: "[]"
                )
                val json = ReminderBackup.encode(reminders, providerList)
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
                        MainTab.TOOLS -> Icons.Default.Calculate
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
                onNotifications = { tab = MainTab.CALENDAR },
                onToggle = { item ->
                    scope.launch {
                        val markCompleted = !item.isCompleted
                        database.reminderDao().setCompleted(item.id, markCompleted)
                        if (markCompleted) {
                            ReminderAlarmScheduler.cancel(context, item.id)
                        } else {
                            // Read the latest due date: a recurring alarm may have advanced
                            // it after this list item was originally composed.
                            database.reminderDao().getById(item.id)?.dueAtMillis?.let {
                                ReminderAlarmScheduler.schedule(context, item.id, it)
                            }
                        }
                    }
                }
            )
            MainTab.CALENDAR -> CalendarScreen(
                reminders = reminders, hindi = hindi, modifier = Modifier.padding(padding),
                onToggle = { item ->
                    scope.launch {
                        val markCompleted = !item.isCompleted
                        database.reminderDao().setCompleted(item.id, markCompleted)
                        if (markCompleted) {
                            ReminderAlarmScheduler.cancel(context, item.id)
                        } else {
                            // Read the latest due date: a recurring alarm may have advanced
                            // it after this list item was originally composed.
                            database.reminderDao().getById(item.id)?.dueAtMillis?.let {
                                ReminderAlarmScheduler.schedule(context, item.id, it)
                            }
                        }
                    }
                }
            )
            MainTab.TASKS -> TasksScreen(
                reminders, hindi, Modifier.padding(padding),
                onToggle = { item ->
                    scope.launch {
                        val markCompleted = !item.isCompleted
                        database.reminderDao().setCompleted(item.id, markCompleted)
                        if (markCompleted) {
                            ReminderAlarmScheduler.cancel(context, item.id)
                        } else {
                            // Read the latest due date: a recurring alarm may have advanced
                            // it after this list item was originally composed.
                            database.reminderDao().getById(item.id)?.dueAtMillis?.let {
                                ReminderAlarmScheduler.schedule(context, item.id, it)
                            }
                        }
                    }
                },
                onDelete = { item -> reminderPendingDelete = item },
                onEdit = { editingReminder = it }
            )
            MainTab.SERVICES -> LocalServicesScreen(hindi, Modifier.padding(padding))
            MainTab.TOOLS -> EverydayToolsScreen(hindi, Modifier.padding(padding))
            MainTab.SETTINGS -> SettingsScreen(
                hindi, { hindi = it }, Modifier.padding(padding),
                onExport = { exportLauncher.launch("DailySathi-Backup.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) }
            )
        }
    }


    reminderPendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { reminderPendingDelete = null },
            title = { Text(if (hindi) "रिमाइंडर हटाएँ?" else "Delete reminder?") },
            text = { Text(if (hindi) "“${item.title}” को स्थायी रूप से हटाया जाएगा।" else "“${item.title}” will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    reminderPendingDelete = null
                    scope.launch {
                        database.reminderDao().delete(item)
                        ReminderAlarmScheduler.cancel(context, item.id)
                    }
                }) { Text(if (hindi) "हटाएँ" else "Delete", color = Color(0xFFB45309)) }
            },
            dismissButton = {
                TextButton(onClick = { reminderPendingDelete = null }) { Text(if (hindi) "रद्द करें" else "Cancel") }
            }
        )
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text(if (hindi) "बैकअप और रीस्टोर" else "Backup & Restore") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { backupMessage = null }) { Text("OK") } }
        )
    }
    restorePreview?.let { backup ->
        val restored = backup.reminders
        AlertDialog(
            onDismissRequest = { restorePreview = null },
            title = { Text(if (hindi) "बैकअप रीस्टोर करें?" else "Restore this backup?") },
            text = {
                Text(if (hindi) "यह डिवाइस के मौजूदा " + reminders.size + " रिमाइंडर और स्थानीय सेवा सूची को बैकअप की जानकारी से बदल देगा।"
                    else "This will replace the " + reminders.size + " reminders and local service directory with the backup contents.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val itemsToRestore = restored
                    val providersToRestore = backup.providers
                    val restoreProviders = backup.includesProviders
                    restorePreview = null
                    scope.launch {
                        runCatching {
                            val providerPrefs = if (restoreProviders) {
                                context.getSharedPreferences("daily_sathi_services", android.content.Context.MODE_PRIVATE)
                            } else null
                            val previousProvidersJson = providerPrefs?.getString("providers", null)
                            var providersWriteAttempted = false
                            try {
                                // Persist the provider side first, off the UI thread. If either persistence
                                // reports failure or Room replacement fails, restore the prior JSON.
                                if (providerPrefs != null) {
                                    val providersJson = ReminderBackup.encodeProviders(providersToRestore).toString()
                                    // SharedPreferences may update its in-memory map even when commit()
                                    // returns false, so rollback after every attempted write.
                                    providersWriteAttempted = true
                                    val saved = withContext(Dispatchers.IO) {
                                        providerPrefs.edit().putString("providers", providersJson).commit()
                                    }
                                    check(saved) { "Unable to save restored service providers" }
                                }
                                database.reminderDao().replaceAll(itemsToRestore)
                            } catch (failure: Throwable) {
                                if (providersWriteAttempted && providerPrefs != null) {
                                    try {
                                        val rolledBack = withContext(Dispatchers.IO) {
                                            val editor = providerPrefs.edit()
                                            if (previousProvidersJson == null) editor.remove("providers")
                                            else editor.putString("providers", previousProvidersJson)
                                            editor.commit()
                                        }
                                        check(rolledBack) { "Unable to roll back the service directory" }
                                    } catch (rollbackFailure: Throwable) {
                                        failure.addSuppressed(rollbackFailure)
                                    }
                                }
                                throw failure
                            }
                            reminders.forEach { ReminderAlarmScheduler.cancel(context, it.id) }
                            itemsToRestore.filter { !it.isCompleted }.forEach { item ->
                                item.dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, item.id, it) }
                            }
                            backupMessage = if (hindi) itemsToRestore.size.toString() + " रिमाइंडर और " + (if (restoreProviders) providersToRestore.size else 0) + " सेवा प्रदाता रीस्टोर हुए।"
                                else "Restored " + itemsToRestore.size + " reminders and " + (if (restoreProviders) providersToRestore.size else 0) + " service providers successfully."
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
                    val recurrenceDay = if (recurrence == "MONTHLY" && dueAtMillis != null) Calendar.getInstance().apply { timeInMillis = dueAtMillis }.get(Calendar.DAY_OF_MONTH) else 0
                    val id = database.reminderDao().insert(Reminder(title = title, note = note, dueAtMillis = dueAtMillis, recurrence = recurrence, category = category, providerName = providerName, location = location, preparationNotes = preparationNotes, recurrenceDayOfMonth = recurrenceDay))
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
                    val recurrenceDay = if (recurrence != "MONTHLY" || dueAtMillis == null) 0 else if (item.recurrence == "MONTHLY" && item.dueAtMillis == dueAtMillis && item.recurrenceDayOfMonth in 1..31) item.recurrenceDayOfMonth else Calendar.getInstance().apply { timeInMillis = dueAtMillis }.get(Calendar.DAY_OF_MONTH)
                    val updated = item.copy(title = title, note = note, dueAtMillis = dueAtMillis, recurrence = recurrence, category = category, providerName = providerName, location = location, preparationNotes = preparationNotes, recurrenceDayOfMonth = recurrenceDay)
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
    onNotifications: () -> Unit,
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
                    IconButton(onClick = onNotifications) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = if (hindi) "आगामी रिमाइंडर" else "Upcoming reminders", tint = Navy)
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
    val reminderDateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
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
                                    Text(reminderDateFormatter.format(Date(it)),
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var todayStart by remember { mutableLongStateOf(startOfLocalDay(System.currentTimeMillis())) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                todayStart = startOfLocalDay(System.currentTimeMillis())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Rebuild this view model only when the reminder list changes, not for unrelated UI state.
    val dated = remember(reminders, formatter) {
        reminders.asSequence()
            .filter { it.dueAtMillis != null }
            .sortedBy { it.dueAtMillis }
            .groupBy { formatter.format(Date(it.dueAtMillis!!)) }
    }
    val undated = remember(reminders) { reminders.filter { it.dueAtMillis == null } }
    val calendarTimeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
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
                                Text(calendarTimeFormatter.format(Date(item.dueAtMillis!!)),
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

internal data class LocalServiceProvider(
    val id: Long,
    val name: String,
    val service: String,
    val phone: String,
    val area: String,
    val details: String,
    val isFavorite: Boolean = false,
    val userRating: Int = 0,
    val feedback: String = ""
)

@Composable
private fun LocalServicesScreen(hindi: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("daily_sathi_services", android.content.Context.MODE_PRIVATE) }
    var area by remember { mutableStateOf("") }
    var providerQuery by remember { mutableStateOf("") }
    var providerCategory by remember { mutableStateOf("All") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("MAPS") }
    var showAddProvider by remember { mutableStateOf(false) }
    var providerBeingEdited by remember { mutableStateOf<LocalServiceProvider?>(null) }
    var providerPendingDelete by remember { mutableStateOf<LocalServiceProvider?>(null) }
    var selectedProvider by remember { mutableStateOf<LocalServiceProvider?>(null) }
    var directoryMessage by remember { mutableStateOf<String?>(null) }
    var showMarketplaceRegister by remember { mutableStateOf(false) }
    var pendingMarketplace by remember { mutableStateOf<List<LocalServiceProvider>>(emptyList()) }
    val marketplaceApi = remember { MarketplaceApi() }
    val marketplaceScope = rememberCoroutineScope()
    var cloudProviders by remember { mutableStateOf<List<MarketplaceProvider>>(emptyList()) }
    var marketplaceLoading by remember { mutableStateOf(false) }
    var marketplaceMessage by remember { mutableStateOf<String?>(null) }
    val providerLoadResult = remember {
        runCatching {
            ReminderBackup.decodeProviders(preferences.getString("providers", "[]") ?: "[]")
        }
    }
    var providerLoadError by remember {
        mutableStateOf(providerLoadResult.exceptionOrNull()?.let {
            if (hindi) "स्थानीय सेवा सूची पढ़ने में समस्या। डेटा सुरक्षित रखा गया है; बैकअप रीस्टोर करें।"
            else "Unable to read the saved provider directory. Existing data is preserved; restore a backup to recover it."
        })
    }
    var providers by remember {
        mutableStateOf(providerLoadResult.getOrDefault(emptyList()))
    }
    val categories = remember { listOf(
        Triple("Electrician", "इलेक्ट्रीशियन", Icons.Default.ElectricalServices),
        Triple("Plumber", "प्लंबर", Icons.Default.Plumbing),
        Triple("Mason", "राजमिस्त्री", Icons.Default.Construction),
        Triple("Carpenter", "बढ़ई", Icons.Default.Build),
        Triple("Painter", "पेंटर", Icons.Default.FormatPaint),
        Triple("House cleaner / Helper", "घरेलू सहायक", Icons.Default.CleaningServices),
        Triple("AC repair", "AC रिपेयर", Icons.Default.AcUnit),
        Triple("Appliance repair", "उपकरण मरम्मत", Icons.Default.HomeRepairService),
        Triple("Driver", "ड्राइवर", Icons.Default.DirectionsCar)
    ) }
    val filteredProviders = remember(providers, favoritesOnly, providerCategory, providerQuery) {
        val query = providerQuery.trim()
        providers.filter { provider ->
        (!favoritesOnly || provider.isFavorite) &&
            (providerCategory == "All" || provider.service.equals(providerCategory, ignoreCase = true)) &&
            (query.isBlank() || listOf(provider.name, provider.service, provider.area, provider.details)
                .any { it.contains(query, ignoreCase = true) })
        }
    }
    fun saveProviders(items: List<LocalServiceProvider>) {
        if (providerLoadError != null) {
            directoryMessage = providerLoadError
            return
        }
        runCatching {
            val json = ReminderBackup.encodeProviders(items).toString()
            check(preferences.edit().putString("providers", json).commit()) {
                "Unable to save provider directory"
            }
        }.onSuccess {
            providers = items
        }.onFailure {
            directoryMessage = (if (hindi) "सेव नहीं हुआ: " else "Save failed: ") + (it.message ?: "Storage error")
        }
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
            FilterChip(selected = selectedMode == "MARKETPLACE", onClick = { selectedMode = "MARKETPLACE" },
                label = { Text(if (hindi) "मार्केटप्लेस" else "Marketplace") },
                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) })
        }
        if (providerLoadError != null && selectedMode == "DIRECTORY") {
            Surface(
                color = Color(0xFFFFF4E5),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Text(
                    providerLoadError ?: "",
                    color = Color(0xFF8A4B08),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
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
        } else if (selectedMode == "DIRECTORY") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (hindi) "आपकी सेवा सूची" else "Your provider directory",
                        color = Navy, fontWeight = FontWeight.SemiBold)
                    Text(if (hindi) "इस डिवाइस पर सेव प्रदाता" else "Providers saved on this device",
                        color = Slate.copy(alpha = .6f), fontSize = 11.sp)
                }
                Button(onClick = { providerBeingEdited = null; showAddProvider = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(if (hindi) "जोड़ें" else "Add")
                }
            }
            directoryMessage?.let {
                Text(it, color = Color(0xFF9A6700), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = providerQuery, onValueChange = { providerQuery = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (hindi) "नाम, सेवा या इलाका खोजें" else "Search name, service or area") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text(if (hindi) "पसंदीदा" else "Favorites") },
                    leadingIcon = { Icon(if (favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null) }
                )
                FilterChip(
                    selected = providerCategory == "All",
                    onClick = { providerCategory = "All" },
                    label = { Text(if (hindi) "सभी सेवाएँ" else "All services") }
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = providerCategory == category.first,
                        onClick = {
                            providerCategory = if (providerCategory == category.first) "All" else category.first
                        },
                        label = { Text(if (hindi) category.second else category.first) }
                    )
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
                        OutlinedButton(onClick = { providerBeingEdited = null; showAddProvider = true }) {
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
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Teal.copy(alpha = .10f)
                                    ) {
                                        Icon(
                                            categories.firstOrNull { it.first.equals(provider.service, true) }?.third
                                                ?: Icons.Default.Handyman,
                                            contentDescription = null,
                                            tint = Teal,
                                            modifier = Modifier.padding(10.dp).size(22.dp)
                                        )
                                    }
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(provider.name, color = Navy, fontWeight = FontWeight.Bold)
                                        Text(provider.service, color = Teal, fontSize = 12.sp)
                                if (provider.userRating > 0) {
                                    Text((if (hindi) "आपकी रेटिंग: " else "Your rating: ") + "★".repeat(provider.userRating),
                                        color = Color(0xFFB7791F), fontSize = 11.sp)
                                }
                                    }
                                    IconButton(onClick = {
                                        saveProviders(providers.map { if (it.id == provider.id) it.copy(isFavorite = !it.isFavorite) else it })
                                        selectedProvider = selectedProvider?.let { if (it.id == provider.id) it.copy(isFavorite = !it.isFavorite) else it }
                                    }) {
                                        Icon(if (provider.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (hindi) "पसंदीदा बदलें" else "Toggle favorite",
                                            tint = if (provider.isFavorite) Color(0xFFE0A000) else Slate.copy(alpha = .55f))
                                    }
                                    IconButton(onClick = {
                                        providerBeingEdited = provider
                                        showAddProvider = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = if (hindi) "बदलें" else "Edit",
                                            tint = DailyBlue)
                                    }
                                    IconButton(onClick = { providerPendingDelete = provider }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = if (hindi) "हटाएँ" else "Delete",
                                            tint = Color(0xFFB45309))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, contentDescription = null,
                                        tint = Slate.copy(alpha = .6f), modifier = Modifier.size(15.dp))
                                    Text(provider.phone, color = Slate.copy(alpha = .75f), fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 6.dp))
                                }
                                if (provider.area.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null,
                                            tint = Slate.copy(alpha = .6f), modifier = Modifier.size(15.dp))
                                        Text(provider.area, color = Slate.copy(alpha = .7f), fontSize = 12.sp)
                                    }
                                }
                                if (provider.details.isNotBlank()) Text(provider.details, color = Slate, fontSize = 12.sp)
                                TextButton(onClick = { selectedProvider = provider }, modifier = Modifier.align(Alignment.Start)) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(if (hindi) "पूरी जानकारी देखें" else "View details")
                                }
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
        } else {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(if (hindi) "DailySathi सेवा मार्केटप्लेस" else "DailySathi Service Marketplace",
                        color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (hindi) "ग्राहक बिना लॉगिन सेवाएँ खोज सकते हैं। सेवा प्रदाता निःशुल्क पंजीकरण कर सकते हैं।"
                        else "Customers can browse without login. Service providers can register for free.",
                        color = Slate.copy(alpha = .68f), fontSize = 12.sp)
                    Button(onClick = {
                        if (marketplaceApi.isConfigured) {
                            showMarketplaceRegister = true
                        } else {
                            selectedMode = "DIRECTORY"
                            marketplaceMessage = if (hindi)
                                "ऑनलाइन पंजीकरण बाद में उपलब्ध होगा। अभी प्रदाता को अपनी डिवाइस डायरेक्टरी में जोड़ें।"
                            else
                                "Online registration will be available later. For now, add the provider to your on-device directory."
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddBusiness, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (hindi) "सेवा प्रदाता जोड़ें" else "Add a service provider")
                    }
                    OutlinedButton(
                        onClick = {
                            if (!marketplaceApi.isConfigured) {
                                marketplaceMessage = if (hindi)
                                    "ऑनलाइन डायरेक्टरी अभी सेटअप नहीं है। आप डिवाइस की स्थानीय प्रदाता सूची का उपयोग कर सकते हैं।"
                                else
                                    "The online directory is not connected yet. You can use the on-device provider directory meanwhile."
                            } else {
                                marketplaceLoading = true
                                marketplaceScope.launch {
                                    runCatching {
                                        marketplaceApi.listApprovedProviders(
                                            null, providerCategory, area, "", providerQuery
                                        )
                                    }
                                        .onSuccess { cloudProviders = it; marketplaceMessage = if (it.isEmpty()) {
                                            if (hindi) "अभी कोई स्वीकृत प्रदाता नहीं मिला।" else "No approved providers found yet."
                                        } else null }
                                        .onFailure { marketplaceMessage = it.message ?: "Unable to load listings." }
                                    marketplaceLoading = false
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !marketplaceLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (marketplaceLoading) (if (hindi) "लोड हो रहा है..." else "Loading...")
                            else if (hindi) "स्वीकृत प्रदाता खोजें" else "Find approved providers")
                    }
                    marketplaceMessage?.let { Text(it, color = Color(0xFF9A6700), fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(if (hindi) "स्वीकृत सेवा प्रदाता" else "Approved service providers",
                color = Navy, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = area, onValueChange = { area = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (hindi) "शहर / इलाका" else "City / area") })
            OutlinedTextField(value = providerQuery, onValueChange = { providerQuery = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (hindi) "सेवा या नाम खोजें" else "Search service or name") })
            if (cloudProviders.isEmpty()) {
                Text(if (hindi) "स्वीकृत लिस्टिंग देखने के लिए ऊपर खोजें।" else "Tap Find approved providers to load public listings.",
                    color = Slate.copy(alpha = .65f), fontSize = 12.sp)
            } else {
                cloudProviders.forEach { item ->
                    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.businessName, color = Navy, fontWeight = FontWeight.SemiBold)
                            Text(item.category + " • " + item.area + ", " + item.city, color = Teal, fontSize = 12.sp)
                            if (item.description.isNotBlank()) Text(item.description, color = Slate, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.phone)))
                                }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Call, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (hindi) "कॉल" else "Call")
                                }
                                OutlinedButton(onClick = {
                                    val digits = item.phone.filter { it.isDigit() }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
                                }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Chat, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("WhatsApp")
                                }
                            }
                        }
                    }
                }
            }
            Text(if (hindi) "सिर्फ स्वीकृत प्रदाता सार्वजनिक रूप से दिखाई देंगे।" else "Only approved providers are shown publicly.",
                color = Slate.copy(alpha = .55f), fontSize = 11.sp)
        }
    }
    providerPendingDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { providerPendingDelete = null },
            title = { Text(if (hindi) "प्रदाता हटाएँ?" else "Delete provider?") },
            text = {
                Text(
                    if (hindi) "${provider.name} को आपकी स्थानीय डायरेक्टरी से हटाया जाएगा।"
                    else "${provider.name} will be removed from your on-device directory."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    saveProviders(providers.filterNot { it.id == provider.id })
                    providerPendingDelete = null
                    directoryMessage = if (hindi) "प्रदाता हटा दिया गया।" else "Provider deleted."
                }) {
                    Text(if (hindi) "हटाएँ" else "Delete", color = Color(0xFFB45309))
                }
            },
            dismissButton = {
                TextButton(onClick = { providerPendingDelete = null }) {
                    Text(if (hindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }
    selectedProvider?.let { provider ->
        var ratingDraft by remember(provider.id) { mutableIntStateOf(provider.userRating) }
        var feedbackDraft by remember(provider.id) { mutableStateOf(provider.feedback) }
        val shareText = buildString {
            append(provider.name).append("\n")
            append(if (hindi) "सेवा: " else "Service: ").append(provider.service).append("\n")
            append(if (hindi) "मोबाइल: " else "Phone: ").append(provider.phone)
            if (provider.area.isNotBlank()) append("\n").append(if (hindi) "इलाका: " else "Area: ").append(provider.area)
            if (provider.details.isNotBlank()) append("\n").append(if (hindi) "विवरण: " else "Details: ").append(provider.details)
            append("\n\n").append(if (hindi) "DailySathi से साझा किया गया" else "Shared via DailySathi")
        }
        AlertDialog(
            onDismissRequest = { selectedProvider = null },
            title = { Text(if (hindi) "सेवा प्रदाता की जानकारी" else "Provider details") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(provider.name, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(provider.service, color = Teal, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = DailyBlue)
                        Text(provider.phone, modifier = Modifier.padding(start = 8.dp))
                    }
                    if (provider.area.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = DailyBlue)
                            Text(provider.area, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (provider.details.isNotBlank()) {
                        Text(if (hindi) "सेवा का विवरण" else "Service description",
                            color = Navy, fontWeight = FontWeight.SemiBold)
                        Text(provider.details, color = Slate)
                    }
                    Divider(color = SoftBg)
                    Text(if (hindi) "आपकी रेटिंग" else "Your rating",
                        color = Navy, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { value ->
                            IconButton(onClick = { ratingDraft = value }) {
                                Icon(
                                    if (value <= ratingDraft) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "$value",
                                    tint = if (value <= ratingDraft) Color(0xFFE0A000) else Slate.copy(alpha = .4f)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = feedbackDraft,
                        onValueChange = { feedbackDraft = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (hindi) "सेवा का अनुभव लिखें (वैकल्पिक)" else "Share service feedback (optional)") },
                        minLines = 2,
                        supportingText = { Text("${feedbackDraft.length}/500") }
                    )
                    OutlinedButton(
                        onClick = {
                            val updated = provider.copy(userRating = ratingDraft, feedback = feedbackDraft.trim())
                            saveProviders(providers.map { if (it.id == provider.id) updated else it })
                            selectedProvider = updated
                            directoryMessage = if (hindi) "आपकी रेटिंग और प्रतिक्रिया सेव हो गई।" else "Your rating and feedback have been saved."
                        },
                        enabled = ratingDraft in 1..5 || feedbackDraft.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RateReview, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (hindi) "रेटिंग और प्रतिक्रिया सेव करें" else "Save rating & feedback")
                    }
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
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Chat, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("WhatsApp")
                        }
                    }
                    OutlinedButton(onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(
                            sendIntent, if (hindi) "प्रदाता की जानकारी साझा करें" else "Share provider"
                        ))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (hindi) "संपर्क साझा करें" else "Share contact")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedProvider = null
                    providerBeingEdited = provider
                    showAddProvider = true
                }) { Text(if (hindi) "संपादित करें" else "Edit") }
            },
            dismissButton = {
                TextButton(onClick = { selectedProvider = null }) {
                    Text(if (hindi) "बंद करें" else "Close")
                }
            }
        )
    }
    if (showMarketplaceRegister) {
        MarketplaceRegistrationDialog(
            hindi = hindi,
            categories = categories.map { it.first },
            onDismiss = { showMarketplaceRegister = false }
        ) { businessName, service, contactName, phone, city, providerArea, details ->
            showMarketplaceRegister = false
            if (!marketplaceApi.isConfigured) {
                marketplaceMessage = if (hindi)
                    "ऑनलाइन पंजीकरण अभी उपलब्ध नहीं है। स्थानीय डायरेक्टरी में प्रदाता जोड़ सकते हैं।"
                else
                    "Online registration is not connected yet. You can add this provider to your local directory."
            } else {
                marketplaceLoading = true
                marketplaceScope.launch {
                    runCatching {
                        marketplaceApi.submitProvider(
                            businessName = businessName,
                            category = service,
                            contactName = contactName,
                            phone = phone,
                            city = city,
                            area = providerArea,
                            description = details
                        )
                    }.onSuccess {
                        marketplaceMessage = if (hindi)
                            "आपका आवेदन समीक्षा के लिए भेज दिया गया है। स्वीकृति के बाद लिस्टिंग दिखाई देगी।"
                        else
                            "Your application was submitted for review. It will appear after approval."
                    }.onFailure {
                        marketplaceMessage = it.message ?: "Unable to submit provider application."
                    }
                    marketplaceLoading = false
                }
            }
        }
    }
    if (showAddProvider) {
        AddLocalProviderDialog(
            hindi = hindi,
            categories = categories.map { it.first },
            initialProvider = providerBeingEdited,
            onDismiss = { showAddProvider = false; providerBeingEdited = null }
        ) { name, service, phone, providerArea, details ->
            val duplicate = providers.any {
                it.id != providerBeingEdited?.id &&
                    it.phone.filter(Char::isDigit) == phone.filter(Char::isDigit) &&
                    it.name.trim().equals(name.trim(), ignoreCase = true)
            }
            if (duplicate) {
                directoryMessage = if (hindi) "यह प्रदाता पहले से सूची में है।" else "This provider already exists in your directory."
            } else {
                val updated = if (providerBeingEdited == null) {
                    providers + LocalServiceProvider(
                        id = System.currentTimeMillis(), name = name, service = service,
                        phone = phone, area = providerArea, details = details
                    )
                } else {
                    providers.map { current ->
                        if (current.id == providerBeingEdited!!.id) current.copy(
                            name = name, service = service, phone = phone,
                            area = providerArea, details = details
                        ) else current
                    }
                }
                saveProviders(updated)
                directoryMessage = if (hindi) "प्रदाता जानकारी सेव हो गई।" else "Provider details saved."
                showAddProvider = false
                providerBeingEdited = null
            }
        }
    }
}

@Composable
private fun MarketplaceRegistrationDialog(
    hindi: Boolean,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String, String, String) -> Unit
) {
    var businessName by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var service by remember { mutableStateOf(categories.first()) }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    val validPhone = phone.count { it.isDigit() } in 10..13

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hindi) "सेवा प्रदाता पंजीकरण" else "Service provider registration") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (hindi) "आपकी जानकारी समीक्षा के बाद ही सार्वजनिक होगी।"
                    else "Your listing will become public only after moderation.",
                    color = Slate.copy(alpha = .7f), fontSize = 12.sp
                )
                OutlinedTextField(
                    value = businessName, onValueChange = { businessName = it },
                    label = { Text(if (hindi) "व्यवसाय / दुकान का नाम" else "Business / shop name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contactName, onValueChange = { contactName = it },
                    label = { Text(if (hindi) "संपर्क व्यक्ति का नाम" else "Contact person name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
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
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text(if (hindi) "मोबाइल नंबर" else "Mobile number") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = phone.isNotBlank() && !validPhone
                )
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    label = { Text(if (hindi) "शहर" else "City") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = area, onValueChange = { area = it },
                    label = { Text(if (hindi) "इलाका / क्षेत्र" else "Area / locality") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = details, onValueChange = { details = it },
                    label = { Text(if (hindi) "सेवा का विवरण (वैकल्पिक)" else "Service details (optional)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        businessName.trim(), service, contactName.trim(), phone.trim(),
                        city.trim(), area.trim(), details.trim()
                    )
                },
                enabled = businessName.isNotBlank() && contactName.isNotBlank() &&
                    city.isNotBlank() && area.isNotBlank() && validPhone
            ) {
                Text(if (hindi) "आवेदन भेजें" else "Submit application")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (hindi) "रद्द करें" else "Cancel") }
        }
    )
}

@Composable
private fun AddLocalProviderDialog(
    hindi: Boolean, categories: List<String>,
    initialProvider: LocalServiceProvider? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember(initialProvider?.id) { mutableStateOf(initialProvider?.name.orEmpty()) }
    var service by remember(initialProvider?.id) { mutableStateOf(initialProvider?.service ?: categories.first()) }
    var phone by remember(initialProvider?.id) { mutableStateOf(initialProvider?.phone.orEmpty()) }
    var area by remember(initialProvider?.id) { mutableStateOf(initialProvider?.area.orEmpty()) }
    var details by remember(initialProvider?.id) { mutableStateOf(initialProvider?.details.orEmpty()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProvider != null) {
            if (hindi) "प्रदाता जानकारी बदलें" else "Edit provider"
        } else {
            if (hindi) "सेवा प्रदाता जोड़ें" else "Add a service provider"
        }) },
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
                Text(if (initialProvider != null) { if (hindi) "अपडेट करें" else "Update" } else { if (hindi) "सेव करें" else "Save" })
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
    val context = LocalContext.current
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackType by remember { mutableStateOf("Suggestion") }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackError by remember { mutableStateOf<String?>(null) }
    var showPremiumPlans by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
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
                    else "Save reminders, appointments and your local service directory—including favourites and ratings—to a JSON backup file.",
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
                Text(if (hindi) "रीस्टोर करने पर बैकअप के रिमाइंडर और (नए बैकअप में) स्थानीय सेवा सूची मौजूदा डेटा को बदल देंगे।"
                    else "Restore replaces current reminders and, for newer backups, the local service directory.",
                    color = Color(0xFFB45309), fontSize = 11.sp)
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (hindi) "DailySathi को सपोर्ट करें" else "Support DailySathi",
                    fontWeight = FontWeight.SemiBold, color = Navy)
                Text(if (hindi) "आपका सहयोग ऐप को बेहतर बनाने और नए फीचर विकसित करने में मदद करेगा।"
                    else "Your support helps us improve the app and build useful new features.",
                    color = Slate.copy(alpha = .7f), fontSize = 12.sp)
                OutlinedButton(onClick = { showPremiumPlans = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hindi) "प्रीमियम प्लान देखें" else "View Premium plans")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = DailyBlue)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (hindi) "प्रीमियम सदस्यता" else "Premium subscription", fontWeight = FontWeight.Medium)
                        Text(if (hindi) "जल्द उपलब्ध — Google Play Billing के माध्यम से"
                            else "Coming soon — subscriptions through Google Play Billing",
                            color = Slate.copy(alpha = .6f), fontSize = 11.sp)
                    }
                }
                Divider(color = SoftBg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFB45309))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (hindi) "स्वैच्छिक दान" else "Voluntary donation", fontWeight = FontWeight.Medium)
                        Text("UPI: 9460915879@ptsbi", color = Slate.copy(alpha = .7f), fontSize = 12.sp)
                        Text(if (hindi) "स्वैच्छिक सहयोग है; कोई प्रीमियम सुविधा नहीं मिलेगी।"
                            else "Optional support only; donations do not unlock premium features.",
                            color = Slate.copy(alpha = .6f), fontSize = 11.sp)
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("DailySathi donation UPI", "9460915879@ptsbi")
                            )
                            android.widget.Toast.makeText(
                                context,
                                if (hindi) "UPI ID कॉपी हो गई" else "UPI ID copied",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (hindi) "UPI ID कॉपी करें" else "Copy donation UPI ID")
                        }
                    }
                }
                Divider(color = SoftBg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = Teal)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (hindi) "विज्ञापन" else "Advertisements", fontWeight = FontWeight.Medium)
                        Text(if (hindi) "विज्ञापन अभी सक्रिय नहीं हैं।"
                            else "Ads are not enabled in this version. An ad-supported free tier is planned.",
                            color = Slate.copy(alpha = .6f), fontSize = 11.sp)
                    }
                }
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (hindi) "प्रतिक्रिया और सुझाव" else "Feedback & Suggestions",
                    fontWeight = FontWeight.SemiBold, color = Navy)
                Text(if (hindi) "बग रिपोर्ट करें, नया सुझाव दें या अपना अनुभव साझा करें।"
                    else "Report a bug, suggest a feature, or tell us about your experience. We would love to hear from you.",
                    color = Slate.copy(alpha = .65f), fontSize = 12.sp)
                Button(onClick = { showFeedback = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Feedback, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hindi) "प्रतिक्रिया भेजें" else "Send feedback")
                }
                Text("Support: tulsaparihar@gmail.com", color = Slate.copy(alpha = .6f), fontSize = 11.sp)
            }
        }
        Text("Parihar Digital Labs", fontWeight = FontWeight.Bold, color = Navy)
        Text("Technology that makes life easier.", color = Slate.copy(alpha = .65f))
        Text("DailySathi v0.1.0", color = Slate.copy(alpha = .5f), fontSize = 12.sp)
    }

    if (showPremiumPlans) {
        AlertDialog(
            onDismissRequest = { showPremiumPlans = false },
            title = { Text(if (hindi) "DailySathi Premium" else "DailySathi Premium") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (hindi) "कम कीमत में विज्ञापन-मुक्त और अतिरिक्त सुविधाएँ। सदस्यता Google Play के सुरक्षित भुगतान से होगी।"
                        else "Affordable access to extra features. Subscriptions will use secure Google Play Billing.",
                        color = Slate.copy(alpha = .72f), fontSize = 12.sp
                    )
                    listOf(
                        Triple("Monthly", "₹99", if (hindi) "1 महीना" else "1 month"),
                        Triple("Six months", "₹499", if (hindi) "6 महीने" else "6 months"),
                        Triple("Annual", "₹999", if (hindi) "12 महीने" else "12 months")
                    ).forEach { plan ->
                        Surface(
                            color = SoftBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        when (plan.first) {
                                            "Monthly" -> if (hindi) "मासिक" else "Monthly"
                                            "Six months" -> if (hindi) "छह महीने" else "Six months"
                                            else -> if (hindi) "वार्षिक" else "Annual"
                                        },
                                        fontWeight = FontWeight.SemiBold, color = Navy
                                    )
                                    Text(plan.third, color = Slate.copy(alpha = .6f), fontSize = 11.sp)
                                }
                                Text(plan.second, fontWeight = FontWeight.Bold, color = DailyBlue, fontSize = 18.sp)
                            }
                        }
                    }
                    Text(
                        if (hindi) "खरीद, खरीद बहाल करना और भुगतान इतिहास Google Play Billing तथा सुरक्षित खाता सेवा सक्रिय होने पर उपलब्ध होंगे।"
                        else "Purchases, restore purchases and payment history will become available after Google Play Billing and secure account services are configured.",
                        color = Slate.copy(alpha = .65f), fontSize = 11.sp
                    )
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hindi) "सदस्यता जल्द उपलब्ध होगी" else "Subscribe — coming soon")
                    }
                    TextButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(if (hindi) "खरीद बहाल करें" else "Restore purchases")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPremiumPlans = false }) {
                    Text(if (hindi) "बंद करें" else "Close")
                }
            }
        )
    }

    if (showFeedback) {
        AlertDialog(
            onDismissRequest = { showFeedback = false; feedbackError = null },
            title = { Text(if (hindi) "अपनी प्रतिक्रिया भेजें" else "Send us your feedback") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (hindi) "आप किस बारे में लिखना चाहते हैं?" else "What would you like to share?",
                        color = Slate.copy(alpha = .7f), fontSize = 12.sp)
                    listOf("Suggestion", "Bug report", "General feedback").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = feedbackType == option, onClick = { feedbackType = option })
                            Text(when (option) {
                                "Bug report" -> if (hindi) "बग रिपोर्ट" else "Bug report"
                                "General feedback" -> if (hindi) "सामान्य प्रतिक्रिया" else "General feedback"
                                else -> if (hindi) "सुझाव" else "Suggestion"
                            }, fontSize = 13.sp)
                        }
                    }
                    OutlinedTextField(
                        value = feedbackMessage,
                        onValueChange = { feedbackMessage = it; feedbackError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (hindi) "अपना संदेश लिखें" else "Your message") },
                        minLines = 4,
                        maxLines = 6
                    )
                    feedbackError?.let { Text(it, color = Color(0xFFB45309), fontSize = 12.sp) }
                    Text(if (hindi) "आपका ईमेल ऐप खुलेगा ताकि आप संदेश भेज सकें।"
                        else "Your email app will open so you can review and send the message.",
                        color = Slate.copy(alpha = .6f), fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val message = feedbackMessage.trim()
                    if (message.isBlank()) {
                        feedbackError = if (hindi) "कृपया अपना संदेश लिखें।" else "Please enter your message."
                    } else {
                        val subject = "DailySathi $feedbackType"
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:tulsaparihar@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        runCatching { context.startActivity(emailIntent) }
                            .onSuccess { showFeedback = false; feedbackMessage = ""; feedbackError = null }
                            .onFailure { feedbackError = if (hindi) "ईमेल ऐप उपलब्ध नहीं है। कृपया tulsaparihar@gmail.com पर सीधे ईमेल करें।" else "No email app found. Please email tulsaparihar@gmail.com directly." }
                    }
                }) { Text(if (hindi) "ईमेल से भेजें" else "Continue to email") }
            },
            dismissButton = {
                TextButton(onClick = { showFeedback = false; feedbackError = null }) {
                    Text(if (hindi) "रद्द करें" else "Cancel")
                }
            }
        )
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
        // When editing, reopen the currently saved date and time instead of
        // unexpectedly resetting the picker to one hour from now.
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dueAtMillis ?: System.currentTimeMillis()
            if (dueAtMillis == null) {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
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
    MainTab.TOOLS -> "टूल्स"
    MainTab.SETTINGS -> "सेटिंग्स"
}


internal fun startOfLocalDay(timeMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
