package com.example.tejastra.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tejastra.data.PrefsManager
import com.example.tejastra.data.TimeBlock
import com.example.tejastra.data.TimeMode
import com.example.tejastra.data.UserSchedulePreferences
import com.example.tejastra.ui.theme.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    var schedule by remember { mutableStateOf(prefsManager.getSchedule()) }
    var prefs by remember { mutableStateOf(prefsManager.getSchedulePreferences()) }
    
    var activeTab by remember { mutableStateOf(0) } // 0 = Builder, 1 = View
    
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Smart Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        color = Snow,
                        fontWeight = FontWeight.Light
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", color = Snow, fontSize = 24.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Void)
            )
        },
        containerColor = Void
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Mode Switcher ──
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(Charcoal, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                TabItem(
                    text = "Builder",
                    isSelected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                TabItem(
                    text = "Timeline",
                    isSelected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            if (activeTab == 0) {
                ScheduleBuilderView(
                    prefs = prefs,
                    onPrefsChange = { 
                        prefs = it
                        prefsManager.saveSchedulePreferences(it)
                    },
                    onGenerate = {
                        val generated = generateSmartSchedule(prefs)
                        schedule = generated
                        prefsManager.saveSchedule(generated)
                        
                        // Mark as generated today
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        prefsManager.lastScheduleGenDate = sdf.format(java.util.Date())
                        
                        activeTab = 1
                    }
                )
            } else {
                ScheduleTimelineView(
                    schedule = schedule,
                    onUpdateBlock = { updated ->
                        val newList = schedule.map { if (it.id == updated.id) updated else it }
                        schedule = newList
                        prefsManager.saveSchedule(newList)
                    },
                    onDeleteBlock = { blockId ->
                        val newList = schedule.filter { it.id != blockId }
                        schedule = newList
                        prefsManager.saveSchedule(newList)
                    }
                )
            }
        }
    }
}

@Composable
fun TabItem(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Snow else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Void else TextTertiary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun ScheduleBuilderView(
    prefs: UserSchedulePreferences,
    onPrefsChange: (UserSchedulePreferences) -> Unit,
    onGenerate: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val today = remember { sdf.format(java.util.Date()) }
    val isAlreadyGenerated = prefsManager.lastScheduleGenDate == today

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        SectionTitle("Daily Routine")
        
        PreferenceTimeItem("Wake-up time", prefs.wakeUpHour, prefs.wakeUpMinute) { h, m ->
            onPrefsChange(prefs.copy(wakeUpHour = h, wakeUpMinute = m))
        }
        PreferenceTimeItem("Sleep time", prefs.sleepHour, prefs.sleepMinute) { h, m ->
            onPrefsChange(prefs.copy(sleepHour = h, sleepMinute = m))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle("Work Hours")
        
        PreferenceTimeItem("Start Work", prefs.workStartHour, prefs.workStartMinute) { h, m ->
            onPrefsChange(prefs.copy(workStartHour = h, workStartMinute = m))
        }
        PreferenceTimeItem("End Work", prefs.workEndHour, prefs.workEndMinute) { h, m ->
            onPrefsChange(prefs.copy(workEndHour = h, workEndMinute = m))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle("Focus Rhythm")
        
        PreferenceSliderItem(
            label = "Deep Work session",
            value = prefs.focusDuration,
            range = 25..120,
            unit = "min"
        ) { onPrefsChange(prefs.copy(focusDuration = it)) }
        
        PreferenceSliderItem(
            label = "Short Break duration",
            value = prefs.breakDuration,
            range = 5..30,
            unit = "min"
        ) { onPrefsChange(prefs.copy(breakDuration = it)) }

        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onGenerate,
            enabled = !isAlreadyGenerated,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Snow, 
                contentColor = Void,
                disabledContainerColor = Charcoal,
                disabledContentColor = TextDisabled
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                if (isAlreadyGenerated) "Schedule Generated for Today" else "Generate Smart Schedule",
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (isAlreadyGenerated) "Come back tomorrow to generate a new schedule." 
            else "AI will optimize blocks based on peak focus windows.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDisabled,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ScheduleTimelineView(
    schedule: List<TimeBlock>,
    onUpdateBlock: (TimeBlock) -> Unit,
    onDeleteBlock: (String) -> Unit
) {
    val sortedSchedule = schedule.sortedBy { it.startHour * 60 + it.startMinute }
    val now = Calendar.getInstance()
    val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    var editingBlock by remember { mutableStateOf<TimeBlock?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Summary Section ──
        ScheduleSummarySection(schedule)
        
        // ── Suggestions ──
        SmartSuggestionsPanel()

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Timeline",
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled,
            modifier = Modifier.padding(horizontal = 24.dp),
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(sortedSchedule) { block ->
                val startMins = block.startHour * 60 + block.startMinute
                val endMins = block.endHour * 60 + block.endMinute
                val isActive = currentMins in startMins until endMins
                
                TimelineItem(
                    block = block,
                    isActive = isActive,
                    onDelete = { onDeleteBlock(block.id) },
                    onClick = { editingBlock = block }
                )
            }
        }
    }

    if (editingBlock != null) {
        EditBlockDialog(
            block = editingBlock!!,
            onDismiss = { editingBlock = null },
            onConfirm = { updated ->
                onUpdateBlock(updated)
                editingBlock = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBlockDialog(
    block: TimeBlock,
    onDismiss: () -> Unit,
    onConfirm: (TimeBlock) -> Unit
) {
    var selectedMode by remember { mutableStateOf(block.mode) }
    var startHour by remember { mutableIntStateOf(block.startHour) }
    var startMinute by remember { mutableIntStateOf(block.startMinute) }
    var endHour by remember { mutableIntStateOf(block.endHour) }
    var endMinute by remember { mutableIntStateOf(block.endMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Charcoal,
        title = { Text("Edit Block", color = Snow) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeMode.values().forEach { mode ->
                        val isSelected = selectedMode == mode
                        val color = when(mode) {
                            TimeMode.DEEP_WORK -> Color(0xFF81C784)
                            TimeMode.WORK -> Color(0xFF64B5F6)
                            TimeMode.BREAK -> Color(0xFFFFD54F)
                            TimeMode.FREE_TIME -> Color(0xFFE57373)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) color else Color.Transparent)
                                .border(1.dp, if (isSelected) color else BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { selectedMode = mode }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mode.name.first().toString(),
                                color = if (isSelected) Void else Snow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                        TimeText(h = startHour, m = startMinute) { h, m ->
                            startHour = h
                            startMinute = m
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("End", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                        TimeText(h = endHour, m = endMinute) { h, m ->
                            endHour = h
                            endMinute = m
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                onConfirm(block.copy(
                    mode = selectedMode,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute
                ))
            }) {
                Text("SAVE", color = Snow)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextDisabled)
            }
        }
    )
}

@Composable
fun ScheduleSummarySection(schedule: List<TimeBlock>) {
    var deepWorkMins = 0
    var breakMins = 0
    
    schedule.forEach { 
        val mins = (it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute)
        if (it.mode == TimeMode.DEEP_WORK) deepWorkMins += mins
        if (it.mode == TimeMode.BREAK) breakMins += mins
    }
    
    val prodScore = (deepWorkMins / 360f * 100).toInt().coerceAtMost(100)

    Row(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .background(Charcoal, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryStat("Deep Work", "${deepWorkMins / 60}h ${deepWorkMins % 60}m", Color(0xFF81C784))
        SummaryStat("Breaks", "${breakMins}m", Color(0xFFFFD54F))
        SummaryStat("Score", "$prodScore", Snow)
    }
}

@Composable
fun SummaryStat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Light)
    }
}

@Composable
fun SmartSuggestionsPanel() {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✨", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Smart Suggestions", style = MaterialTheme.typography.labelSmall, color = Snow)
        }
        Spacer(modifier = Modifier.height(12.dp))
        SuggestionItem("Your peak focus window is 8:00 AM – 10:30 AM.")
        SuggestionItem("Consider adding a 15m break after your long work block.")
    }
}

@Composable
fun SuggestionItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("•", color = TextDisabled)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
    }
}

@Composable
fun TimelineItem(
    block: TimeBlock,
    isActive: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val modeColor = when (block.mode) {
        TimeMode.DEEP_WORK -> Color(0xFF81C784) // Green
        TimeMode.WORK -> Color(0xFF64B5F6) // Blue
        TimeMode.BREAK -> Color(0xFFFFD54F) // Yellow
        TimeMode.FREE_TIME -> Color(0xFFE57373) // Red (requested)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time Indicator
        Column(
            modifier = Modifier.width(60.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                String.format("%02d:%02d", block.startHour, block.startMinute),
                style = MaterialTheme.typography.labelSmall,
                color = Snow
            )
            Box(modifier = Modifier.height(40.dp).width(1.dp).background(BorderSubtle).align(Alignment.End).padding(end = 4.dp))
            Text(
                String.format("%02d:%02d", block.endHour, block.endMinute),
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Block Card
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isActive) modeColor.copy(alpha = 0.2f) else Charcoal)
                .border(
                    width = if (isActive) 2.dp else 0.dp,
                    color = if (isActive) modeColor else Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable(onClick = onClick)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(modeColor, CircleShape))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        block.mode.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Snow,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${(block.endHour * 60 + block.endMinute) - (block.startHour * 60 + block.startMinute)} minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                
                if (isActive) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = modeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Snow,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun PreferenceTimeItem(label: String, h: Int, m: Int, onTimeChange: (Int, Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
        TimeText(h = h, m = m, onTimeChange = onTimeChange)
    }
}

@Composable
fun TimeText(h: Int, m: Int, onTimeChange: (Int, Int) -> Unit) {
    val context = LocalContext.current
    Text(
        text = String.format("%02d:%02d", h, m),
        color = Snow,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Charcoal)
            .clickable {
                android.app.TimePickerDialog(context, { _, hour, minute ->
                    onTimeChange(hour, minute)
                }, h, m, true).show()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun PreferenceSliderItem(label: String, value: Int, range: IntRange, unit: String, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
            Text("$value $unit", color = Snow, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Snow,
                activeTrackColor = Snow,
                inactiveTrackColor = Charcoal
            )
        )
    }
}

/**
 * AI Logic: Generates a smart schedule based on user preferences.
 */
fun generateSmartSchedule(prefs: UserSchedulePreferences): List<TimeBlock> {
    val blocks = mutableListOf<TimeBlock>()
    
    // 1. Morning Routine (Wake-up to Work Start)
    if (prefs.workStartHour > prefs.wakeUpHour || (prefs.workStartHour == prefs.wakeUpHour && prefs.workStartMinute > prefs.wakeUpMinute)) {
        blocks.add(createBlock(TimeMode.FREE_TIME, prefs.wakeUpHour, prefs.wakeUpMinute, prefs.workStartHour, prefs.workStartMinute))
    }
    
    // 2. Work Blocks (Work Start to Work End)
    var currentH = prefs.workStartHour
    var currentM = prefs.workStartMinute
    val workEndTotalMins = prefs.workEndHour * 60 + prefs.workEndMinute
    
    var isDeep = true // Alternate between Deep and Normal
    
    while ((currentH * 60 + currentM) < workEndTotalMins) {
        val blockDuration = prefs.focusDuration
        val nextTotalMins = currentH * 60 + currentM + blockDuration
        
        val actualEndTotalMins = nextTotalMins.coerceAtMost(workEndTotalMins)
        
        blocks.add(createBlock(
            if (isDeep) TimeMode.DEEP_WORK else TimeMode.WORK,
            currentH, currentM,
            actualEndTotalMins / 60, actualEndTotalMins % 60
        ))
        
        currentH = actualEndTotalMins / 60
        currentM = actualEndTotalMins % 60
        
        // Add a break if we haven't reached the end
        if ((currentH * 60 + currentM) < workEndTotalMins) {
            val breakEndMins = (currentH * 60 + currentM + prefs.breakDuration).coerceAtMost(workEndTotalMins)
            blocks.add(createBlock(TimeMode.BREAK, currentH, currentM, breakEndMins / 60, breakEndMins % 60))
            currentH = breakEndMins / 60
            currentM = breakEndMins % 60
        }
        
        isDeep = !isDeep
    }
    
    // 3. Evening Routine (Work End to Sleep)
    if (prefs.sleepHour > prefs.workEndHour || (prefs.sleepHour == prefs.workEndHour && prefs.sleepMinute > prefs.workEndMinute)) {
        blocks.add(createBlock(TimeMode.FREE_TIME, prefs.workEndHour, prefs.workEndMinute, prefs.sleepHour, prefs.sleepMinute))
    }
    
    return blocks
}

fun createBlock(mode: TimeMode, startH: Int, startM: Int, endH: Int, endM: Int): TimeBlock {
    return TimeBlock(
        id = UUID.randomUUID().toString(),
        mode = mode,
        startHour = startH,
        startMinute = startM,
        endHour = endH,
        endMinute = endM
    )
}
