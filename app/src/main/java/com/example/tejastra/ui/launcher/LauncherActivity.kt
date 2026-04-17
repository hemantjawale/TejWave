package com.example.tejastra.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tejastra.data.LocationModeManager
import com.example.tejastra.data.PrefsManager
import com.example.tejastra.data.ScreenTimeTracker
import com.example.tejastra.data.Task
import com.example.tejastra.ui.theme.*
import com.example.tejastra.utils.toTitleCase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Minimal launcher — pure black, shows time, screen time, tasks, 4 favorite apps (text only).
 * Swipe up to reveal app drawer with text-only app names A–Z sorted.
 */
class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Hide Status Bar for Extreme Full Screen
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())

        setContent {
            TejAstraTheme {
                LauncherScreen()
            }
        }
    }

    // Prevent back from exiting the launcher
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing — this IS the home screen
    }
}

data class AppItem(
    val label: String,
    val packageName: String,
)

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }

    var showDrawer by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var screenTimeText by remember { mutableStateOf("—") }
    var tasks by remember { mutableStateOf(prefsManager.getTasks()) }
    var favoriteApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddTask by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var isDumbPhoneMode by remember { mutableStateOf(prefsManager.isDumbPhoneMode) }
    var shouldShowKeyboardOnDrawerOpen by remember {
        mutableStateOf(prefsManager.shouldShowKeyboardOnDrawerOpen)
    }
    var use24HourClock by remember { mutableStateOf(prefsManager.use24HourClock) }
    var isDeepWorkActive by remember { mutableStateOf(false) }
    var pomodoroTimeLeft by remember { mutableStateOf("") }
    var customDeepWorkMinutes by remember { mutableStateOf("25") }
    
    var isTryingToStopDeepWork by remember { mutableStateOf(false) }
    var stopDeepWorkCountdown by remember { mutableIntStateOf(5) }
    val breathingLevel = remember { Animatable(1f) }
    var motivationText by remember { mutableStateOf(prefsManager.motivationText) }
    var activeModeName by remember {
        mutableStateOf(
            prefsManager.getFocusModes().find { it.id == prefsManager.activeFocusModeId }?.name ?: "Work"
        )
    }
    
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    if (level != -1 && scale != -1) {
                        batteryLevel = (level * 100) / scale
                    }
                    isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == android.os.BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        val filter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Load apps
    LaunchedEffect(Unit) {
        allApps = getInstalledApps(context)
        val favPkgs = prefsManager.getFavoriteApps()
        favoriteApps = favPkgs.mapNotNull { pkg ->
            allApps.find { it.packageName == pkg }
        }
    }

    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTime = SimpleDateFormat(
                if (use24HourClock) "HH:mm" else "hh:mm a",
                Locale.getDefault()
            ).format(now)
            currentDate = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
            
            val msNow = System.currentTimeMillis()
            val endMillis = prefsManager.deepWorkEndTime
            if (endMillis > msNow) {
                isDeepWorkActive = true
                val totalSecs = (endMillis - msNow) / 1000
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                pomodoroTimeLeft = String.format("%02d:%02d", mins, secs)
            } else {
                isDeepWorkActive = false
            }
            isDumbPhoneMode = prefsManager.isDumbPhoneMode
            shouldShowKeyboardOnDrawerOpen = prefsManager.shouldShowKeyboardOnDrawerOpen
            use24HourClock = prefsManager.use24HourClock
            motivationText = prefsManager.motivationText
            activeModeName = prefsManager.getFocusModes()
                .find { it.id == prefsManager.activeFocusModeId }
                ?.name
                ?: activeModeName
            
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            LocationModeManager.syncModeFromCurrentLocation(context, prefsManager)?.let { modeId ->
                activeModeName = prefsManager.getFocusModes().find { it.id == modeId }?.name ?: activeModeName
            }
            kotlinx.coroutines.delay(60_000)
        }
    }

    // Update screen time
    LaunchedEffect(Unit) {
        try {
            if (ScreenTimeTracker.hasPermission(context)) {
                val tracker = ScreenTimeTracker(context)
                while (true) {
                    val summary = tracker.getTodaySummary()
                    val hours = summary.totalMinutes / 60
                    val mins = summary.totalMinutes % 60
                    screenTimeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    kotlinx.coroutines.delay(60_000)
                }
            }
        } catch (_: Exception) {
            screenTimeText = "—"
        }
    }

    // Dopamine delay countdown
    LaunchedEffect(isTryingToStopDeepWork) {
        if (isTryingToStopDeepWork) {
            stopDeepWorkCountdown = 5
            launch {
                breathingLevel.snapTo(1f)
                breathingLevel.animateTo(0f, animationSpec = tween(5000, easing = LinearEasing))
            }
            while (stopDeepWorkCountdown > 0) {
                kotlinx.coroutines.delay(1000)
                stopDeepWorkCountdown--
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
    ) {
        // ── Home Screen ──
        AnimatedVisibility(
            visible = !showDrawer,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -40 && !isDumbPhoneMode && !isDeepWorkActive) {
                                showDrawer = true
                            }
                        }
                    }
                    .pointerInput(prefsManager.isDoubleTapLockEnabled) {
                        if (prefsManager.isDoubleTapLockEnabled) {
                            detectTapGestures(
                                onDoubleTap = {
                                    com.example.tejastra.service.TejAstraAccessibilityService.instance?.performGlobalAction(8) // GLOBAL_ACTION_LOCK_SCREEN (API 28)
                                }
                            )
                        }
                    }
                    .systemBarsPadding()
                    .padding(horizontal = 32.dp)
            ) {
                // Settings icon top right
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val intent = Intent(context, com.example.tejastra.MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                    tint = TextTertiary,
                )
                
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(80.dp))

                    // ── Time ──
                    Column(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            try {
                                val pm = context.packageManager
                                val clockPackages = listOf(
                                    "com.sec.android.app.clockpackage", // Samsung
                                    "com.google.android.deskclock", // Google 
                                    "com.android.deskclock", // AOSP
                                    "com.oneplus.deskclock" // OnePlus
                                )
                                
                                var launched = false
                                for (pkg in clockPackages) {
                                    val intent = pm.getLaunchIntentForPackage(pkg)
                                    if (intent != null) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        launched = true
                                        break
                                    }
                                }
                                
                                if (!launched) {
                                    val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "No clock app found", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(
                            text = currentTime,
                            style = MaterialTheme.typography.displayLarge,
                            color = Snow,
                            fontWeight = FontWeight.Thin,
                            letterSpacing = (-3).sp,
                            fontSize = 80.sp,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentDate.toTitleCase(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary,
                                letterSpacing = 1.sp,
                            )
                            
                            if (batteryLevel >= 0) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDisabled,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "$batteryLevel%${if (isCharging) " (Charging)" else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (batteryLevel <= 15 && !isCharging) Color(0xFFE57373) else TextTertiary,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "$activeModeName mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDisabled,
                            letterSpacing = 2.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // ── Screen Time ──
                    Column(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val intent = Intent(context, com.example.tejastra.MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra("open_route", "screen_time")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text(
                            text = "Screen time",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDisabled,
                            letterSpacing = 2.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = screenTimeText,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Snow,
                            fontWeight = FontWeight.Light,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Deep Work (Pomodoro) ──
                    if (isDeepWorkActive) {
                        Column {
                            Text(
                                text = "Deep work session",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDisabled,
                                letterSpacing = 2.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pomodoroTimeLeft,
                                style = MaterialTheme.typography.headlineLarge,
                                color = Snow,
                                fontWeight = FontWeight.Light,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "App drawer disabled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Fog,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Stop",
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        isTryingToStopDeepWork = true
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Deep work",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDisabled,
                                letterSpacing = 2.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = customDeepWorkMinutes,
                                    onValueChange = { if (it.length <= 3) customDeepWorkMinutes = it.filter { char -> char.isDigit() } },
                                    textStyle = MaterialTheme.typography.headlineLarge.copy(color = Snow),
                                    modifier = Modifier.width(60.dp),
                                    cursorBrush = SolidColor(Snow),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                                Text(
                                    text = " min ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Fog,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "Start",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Charcoal)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            val min = customDeepWorkMinutes.toLongOrNull() ?: 25L
                                            prefsManager.deepWorkEndTime = System.currentTimeMillis() + (min * 60 * 1000L)
                                        }
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextTertiary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Today's Tasks ──
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDisabled,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                text = "+",
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { showAddTask = !showAddTask },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextTertiary,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Add task field
                        AnimatedVisibility(visible = showAddTask) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicTextField(
                                    value = newTaskTitle,
                                    onValueChange = { newTaskTitle = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = Snow,
                                    ),
                                    cursorBrush = SolidColor(Snow),
                                    decorationBox = { innerTextField ->
                                        if (newTaskTitle.isEmpty()) {
                                            Text(
                                                "What needs to be done",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextDisabled,
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (newTaskTitle.isNotBlank()) {
                                    Text(
                                        text = "↵",
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                prefsManager.addTask(
                                                    Task(
                                                        id = UUID.randomUUID().toString(),
                                                        title = newTaskTitle.trim(),
                                                    )
                                                )
                                                tasks = prefsManager.getTasks()
                                                newTaskTitle = ""
                                                showAddTask = false
                                            },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Snow,
                                    )
                                }
                            }
                        }

                        // Task list
                        tasks.filter { !it.isCompleted }.take(5).forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        prefsManager.toggleTask(task.id)
                                        tasks = prefsManager.getTasks()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "○",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (task.isCompleted) TextDisabled else Fog,
                                )
                            }
                        }

                        // Show completed count
                        val completedCount = tasks.count { it.isCompleted }
                        if (completedCount > 0) {
                            Text(
                                text = "$completedCount done",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDisabled,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Favorite Apps (text only, max 4) ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        favoriteApps.take(4).forEach { app ->
                            Text(
                                text = app.label.toTitleCase(),
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        launchApp(context, app.packageName)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }

                    // ── Motivation Line ──
                    if (motivationText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = motivationText,
                                modifier = Modifier.padding(horizontal = 48.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextDisabled,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }



                    // Swipe hint
                    val hintText = if (isDeepWorkActive) {
                        "Focus."
                    } else if (isDumbPhoneMode) {
                        "Dumb-phone active."
                    } else {
                        "↑"
                    }
                    
                    Text(
                        text = hintText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .alpha(0.2f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                        textAlign = TextAlign.Center,
                    )

                    // ── Bottom Shortcut Icons ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp) // Changed from negative to safe positive padding
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (prefsManager.bottomLeftApp.isNotEmpty()) {
                            val leftIcon = remember(prefsManager.bottomLeftApp) {
                                try {
                                    val drawable = context.packageManager.getApplicationIcon(prefsManager.bottomLeftApp)
                                    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
                                    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
                                    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                                    drawable.draw(canvas)
                                    bitmap.asImageBitmap()
                                } catch (e: Exception) {
                                    android.util.Log.e("TejAstra", "Error loading left icon", e)
                                    null 
                                }
                            }
                            if (leftIcon != null) {
                                Image(
                                    bitmap = leftIcon,
                                    contentDescription = "Left shortcut",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { launchApp(context, prefsManager.bottomLeftApp) },
                                )
                            } else {
                                Spacer(modifier = Modifier.size(44.dp))
                            }
                        } else {
                            Spacer(modifier = Modifier.size(44.dp))
                        }

                        if (prefsManager.bottomRightApp.isNotEmpty()) {
                            val rightIcon = remember(prefsManager.bottomRightApp) {
                                try {
                                    val drawable = context.packageManager.getApplicationIcon(prefsManager.bottomRightApp)
                                    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
                                    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
                                    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                                    drawable.draw(canvas)
                                    bitmap.asImageBitmap()
                                } catch (e: Exception) {
                                    android.util.Log.e("TejAstra", "Error loading right icon", e)
                                    null 
                                }
                            }
                            if (rightIcon != null) {
                                Image(
                                    bitmap = rightIcon,
                                    contentDescription = "Right shortcut",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { launchApp(context, prefsManager.bottomRightApp) },
                                )
                            } else {
                                Spacer(modifier = Modifier.size(44.dp))
                            }
                        } else {
                            Spacer(modifier = Modifier.size(44.dp))
                        }
                    }
                }
            }
        }

        // ── App Drawer ──
        AnimatedVisibility(
            visible = showDrawer,
            enter = slideInVertically(tween(350, easing = EaseOutCubic)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(200))
        ) {
            AppDrawer(
                apps = allApps,
                searchQuery = searchQuery,
                autoFocusSearch = shouldShowKeyboardOnDrawerOpen,
                onSearchChange = { searchQuery = it },
                onAppClick = { pkg ->
                    launchApp(context, pkg)
                    showDrawer = false
                    searchQuery = ""
                },
                onClose = {
                    showDrawer = false
                    searchQuery = ""
                }
            )
        }

        // ── Stop Deep Work Overlay (Dopamine Delay) ──
        AnimatedVisibility(
            visible = isTryingToStopDeepWork,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Void.copy(alpha = 0.98f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}, // Absorb clicks completely
                contentAlignment = Alignment.Center
            ) {
                // Full Screen Emptying Animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(breathingLevel.value)
                        .align(Alignment.BottomCenter)
                        .background(Snow.copy(alpha = 0.15f))
                )
                
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Do you really want to exit?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Snow,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    if (stopDeepWorkCountdown > 0) {
                        Text(
                            text = stopDeepWorkCountdown.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = Snow,
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Thin
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Breathe and let the urge pass...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                text = "No, stay focused",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Snow)
                                    .clickable { isTryingToStopDeepWork = false }
                                    .padding(vertical = 16.dp, horizontal = 32.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Void,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Yes, exit",
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        prefsManager.deepWorkEndTime = 0
                                        isDeepWorkActive = false
                                        isTryingToStopDeepWork = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppDrawer(
    apps: List<AppItem>,
    searchQuery: String,
    autoFocusSearch: Boolean,
    onSearchChange: (String) -> Unit,
    onAppClick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(searchQuery, filteredApps.size) {
        if (searchQuery.isNotBlank() && filteredApps.size == 1) {
            onAppClick(filteredApps.first().packageName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
            .systemBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 60) {
                        onClose()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Search ──
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(vertical = 16.dp),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = Snow,
                    fontWeight = FontWeight.Light,
                ),
                cursorBrush = SolidColor(Snow),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Search",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextDisabled,
                            fontWeight = FontWeight.Light,
                        )
                    }
                    innerTextField()
                }
            )

            Divider(
                color = BorderSubtle,
                thickness = 0.5.dp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── App List (text only) ──
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    Text(
                        text = app.label.toTitleCase(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onAppClick(app.packageName) }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Fog,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}

private fun getInstalledApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    @Suppress("DEPRECATION")
    val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

    return resolveInfos
        .map { resolveInfo ->
            AppItem(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName,
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun launchApp(context: Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "app not available", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
