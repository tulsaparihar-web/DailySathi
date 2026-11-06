package com.parihardigitallabs.dailysathi

import android.os.Bundle
import android.content.Intent
import android.net.Uri
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
    HOME("Home"), CALENDAR("Calendar"), TASKS("Tasks"), SERVICES("Services"), FAMILY("Family"), SETTINGS("Settings")
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
            onSave = { title, note, dueAtMillis, recurrence, category ->
                scope.launch {
                    val id = database.reminderDao().insert(Reminder(title = title, note = note, dueAtMillis = dueAtMillis, recurrence = recurrence, category = category))
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
            onSave = { title, note, dueAtMillis, recurrence, category ->
                scope.launch {
                    val updated = item.copy(title = title, note = note, dueAtMillis = dueAtMillis, recurrence = recurrence, category = category)
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
                                Text(reminder.title, fontWeight = FontWeight.Medium)
                                reminder.dueAtMillis?.let {
                                    Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it)),
                                        fontSize = 11.sp, color = Slate.copy(alpha = .6f))
                                }
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

@Composable
private fun LocalServicesScreen(hindi: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var area by remember { mutableStateOf("") }
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
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Text(if (hindi) "स्थानीय सेवाएँ" else "Local Services",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(if (hindi) "अपने आसपास काम के लिए सही व्यक्ति खोजें।" else "Find nearby professionals for everyday jobs.",
            color = Slate.copy(alpha = .68f), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
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
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 20.dp)) {
            items(categories) { category ->
                Surface(
                    onClick = {
                        val searchText = buildString {
                            append(category.first)
                            append(" near ")
                            append(area.ifBlank { "me" })
                        }
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
        }
        Text(if (hindi) "खोज Google Maps में खुलेगी। उपलब्धता, रेटिंग और संपर्क विवरण Maps पर निर्भर हैं।"
            else "Search opens in Google Maps. Listings, ratings and contact details depend on Maps results.",
            color = Slate.copy(alpha = .55f), fontSize = 11.sp)
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
    hindi: Boolean, initialReminder: Reminder? = null,
    onDismiss: () -> Unit, onSave: (String, String, Long?, String, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialReminder?.title.orEmpty()) }
    var note by remember { mutableStateOf(initialReminder?.note.orEmpty()) }
    var dueAtMillis by remember { mutableStateOf(initialReminder?.dueAtMillis) }
    var recurrence by remember { mutableStateOf(initialReminder?.recurrence ?: "NONE") }
    var category by remember { mutableStateOf(initialReminder?.category ?: "Personal") }
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
                onClick = { if (title.isNotBlank()) onSave(title.trim(), note.trim(), dueAtMillis, if (dueAtMillis == null) "NONE" else recurrence, category) },
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
    MainTab.FAMILY -> "परिवार"
    MainTab.SETTINGS -> "सेटिंग्स"
}
