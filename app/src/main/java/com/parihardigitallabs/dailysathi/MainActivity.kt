package com.parihardigitallabs.dailysathi

import android.os.Bundle
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
    HOME("Home"), CALENDAR("Calendar"), TASKS("Tasks"), FAMILY("Family"), SETTINGS("Settings")
}

@Composable
private fun DailySathiApp(database: DailySathiDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reminders by database.reminderDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var showAdd by remember { mutableStateOf(false) }
    var hindi by remember { mutableStateOf(false) }

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
                        MainTab.FAMILY -> Icons.Default.People
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
                onToggle = { item ->
                    scope.launch {
                        database.reminderDao().update(item.copy(isCompleted = !item.isCompleted))
                        if (item.isCompleted) item.dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, item.id, it) }
                        else ReminderAlarmScheduler.cancel(context, item.id)
                    }
                }
            )
            MainTab.CALENDAR -> PlaceholderScreen(
                if (hindi) "कैलेंडर" else "Calendar",
                if (hindi) "महत्वपूर्ण तारीखें और रिमाइंडर यहाँ दिखाई देंगे।"
                else "Your important dates and upcoming reminders will appear here.",
                Icons.Default.CalendarMonth, Modifier.padding(padding)
            )
            MainTab.TASKS -> TasksScreen(
                reminders, hindi, Modifier.padding(padding),
                onToggle = { item ->
                    scope.launch {
                        database.reminderDao().update(item.copy(isCompleted = !item.isCompleted))
                    }
                },
                onDelete = { item -> scope.launch { database.reminderDao().delete(item); ReminderAlarmScheduler.cancel(context, item.id) } }
            )
            MainTab.FAMILY -> PlaceholderScreen(
                if (hindi) "परिवार" else "Family",
                if (hindi) "परिवार प्रोफाइल अगले चरण में आएँगे।"
                else "Family profiles and shared tasks are planned for the next milestone.",
                Icons.Default.People, Modifier.padding(padding)
            )
            MainTab.SETTINGS -> SettingsScreen(hindi, { hindi = it }, Modifier.padding(padding))
        }
    }

    if (showAdd) {
        AddReminderDialog(
            hindi = hindi,
            onDismiss = { showAdd = false },
            onSave = { title, note, dueAtMillis ->
                scope.launch {
                    val id = database.reminderDao().insert(Reminder(title = title, note = note, dueAtMillis = dueAtMillis))
                    dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, id, it) }
                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        (context as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 501)
                    }
                    showAdd = false
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
                    QuickCard(if (hindi) "बिल और EMI" else "Bills & EMI",
                        if (hindi) "भुगतान की तारीखें" else "Due dates",
                        Icons.Default.ReceiptLong, Teal, Modifier.weight(1f), onAdd)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickCard(if (hindi) "नवीनीकरण" else "Renewals",
                        if (hindi) "दस्तावेज़" else "Documents",
                        Icons.Default.Description, Color(0xFFB97820), Modifier.weight(1f), onAdd)
                    QuickCard(if (hindi) "परिवार के कार्य" else "Family tasks",
                        if (hindi) "मिलकर व्यवस्थित" else "Organize together",
                        Icons.Default.Groups, Color(0xFF7653A6), Modifier.weight(1f), onAdd)
                }
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
private fun TasksScreen(
    reminders: List<Reminder>, hindi: Boolean, modifier: Modifier,
    onToggle: (Reminder) -> Unit, onDelete: (Reminder) -> Unit
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
                            Text(reminder.title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
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
private fun SettingsScreen(hindi: Boolean, onLanguageChange: (Boolean) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(if (hindi) "सेटिंग्स" else "Settings",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (hindi) "ऐप की भाषा" else "App language", fontWeight = FontWeight.SemiBold)
                    Text(if (hindi) "हिंदी" else "English",
                        color = Slate.copy(alpha = .6f), fontSize = 12.sp)
                }
                Switch(checked = hindi, onCheckedChange = onLanguageChange)
            }
        }
        Text("Parihar Digital Labs", fontWeight = FontWeight.Bold, color = Navy)
        Text("Technology that makes life easier.", color = Slate.copy(alpha = .65f))
        Text("DailySathi v0.1.0", color = Slate.copy(alpha = .5f), fontSize = 12.sp)
    }
}

@Composable
private fun AddReminderDialog(
    hindi: Boolean, onDismiss: () -> Unit, onSave: (String, String, Long?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dueAtMillis by remember { mutableStateOf<Long?>(null) }
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
        title = { Text(if (hindi) "नया रिमाइंडर" else "New reminder") },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), note.trim(), dueAtMillis) },
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
    MainTab.FAMILY -> "परिवार"
    MainTab.SETTINGS -> "सेटिंग्स"
}
