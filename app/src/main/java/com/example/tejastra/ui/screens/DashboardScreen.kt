package com.example.tejastra.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import android.content.pm.PackageManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tejastra.data.*
import com.example.tejastra.service.TejAstraAccessibilityService
import com.example.tejastra.ui.theme.*
import com.example.tejastra.utils.toTitleCase

/**
 * Main dashboard showing:
 * - Today's screen time
 * - Active blocked apps
 * - Permission status
 * - Quick actions
 */
@Composable
fun DashboardScreen(
    onNavigateToAddApp: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToScreenTime: () -> Unit,
    onNavigateToFocusMode: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    var focusModes by remember { mutableStateOf(prefsManager.getFocusModes()) }
    var activeModeId by remember { mutableStateOf(prefsManager.activeFocusModeId) }
    var blockedApps by remember { mutableStateOf(prefsManager.getBlockedApps(activeModeId)) }
    var screenTimeText by remember { mutableStateOf("—") }
    var totalMinutes by remember { mutableLongStateOf(0L) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Permission states
    var hasUsagePermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var isDefaultLauncher by remember { mutableStateOf(false) }

    var attentionCredits by remember { mutableStateOf<AttentionCredits?>(null) }
    var appUsageSummary by remember { mutableStateOf<ScreenTimeSummary?>(null) }

    // Auto-refresh credits on broadcast from accessibility service
    DisposableEffect(context) {
        val creditReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == TejAstraAccessibilityService.ACTION_CREDITS_UPDATED) {
                    attentionCredits = ScreenTimeTracker(context).calculateAttentionCredits()
                }
            }
        }
        val filter = android.content.IntentFilter(TejAstraAccessibilityService.ACTION_CREDITS_UPDATED)
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(creditReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(creditReceiver, filter)
        }
        onDispose {
            context.unregisterReceiver(creditReceiver)
        }
    }

    LaunchedEffect(refreshTrigger) {
        LocationModeManager.syncModeFromCurrentLocation(context, prefsManager)?.let { newModeId ->
            activeModeId = newModeId
            blockedApps = prefsManager.getBlockedApps(newModeId)
            focusModes = prefsManager.getFocusModes()
        }

        hasUsagePermission = ScreenTimeTracker.hasPermission(context)
        hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
        isDefaultLauncher = isAppDefaultLauncher(context)
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(context) else true

        if (hasUsagePermission) {
            try {
                val tracker = ScreenTimeTracker(context)
                val summary = tracker.getTodaySummary()
                appUsageSummary = summary
                totalMinutes = summary.totalMinutes
                val hours = summary.totalMinutes / 60
                val mins = summary.totalMinutes % 60
                screenTimeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                attentionCredits = tracker.calculateAttentionCredits()
            } catch (_: Exception) {
                screenTimeText = "—"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(48.dp))
            
            // ── Header with Greeting and Settings ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val calendar = java.util.Calendar.getInstance()
                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when (hour) {
                    in 0..11 -> "Good Morning"
                    in 12..16 -> "Good Afternoon"
                    else -> "Good Evening"
                }
                Text(
                    text = "👋 $greeting, Aachal",
                    style = MaterialTheme.typography.titleMedium,
                    color = Snow,
                    fontWeight = FontWeight.Bold,
                )
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onNavigateToSettings() },
                    tint = TextTertiary,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Current Mode & Credits Box ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Charcoal)
                    .padding(24.dp),
            ) {
                Column {
                    val currentModeName = focusModes.find { it.id == activeModeId }?.name ?: "UNKNOWN"
                    Text(text = "🧠 Current Mode: ${currentModeName.uppercase()}", style = MaterialTheme.typography.bodyMedium, color = Snow, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val calendar = java.util.Calendar.getInstance()
                    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                    val activeScheduleBlock = prefsManager.getSchedule().find { block ->
                        val startMins = block.startHour * 60 + block.startMinute
                        val endMins = block.endHour * 60 + block.endMinute
                        val currentMins = hour * 60 + calendar.get(java.util.Calendar.MINUTE)
                        currentMins in startMins until endMins && block.daysOfWeek.contains(calendar.get(java.util.Calendar.DAY_OF_WEEK))
                    }
                    val timeLeftStr = if (activeScheduleBlock != null) {
                        val endMins = activeScheduleBlock.endHour * 60 + activeScheduleBlock.endMinute
                        val currentMins = hour * 60 + calendar.get(java.util.Calendar.MINUTE)
                        val remaining = endMins - currentMins
                        "${remaining / 60}h ${remaining % 60}m"
                    } else "Active"
                    
                    Text(text = "⏱ Time Left: $timeLeftStr", style = MaterialTheme.typography.bodyMedium, color = Snow)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val currentCredits = attentionCredits
                    if (currentCredits != null) {
                        Text(text = "💰 Attention Credits: ${currentCredits.remainingCredits} / ${currentCredits.totalCredits}", style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { onNavigateToFocusMode(activeModeId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Snow, contentColor = Void),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Configure Focus", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                prefsManager.activeFocusModeId = "break"
                                activeModeId = "break"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDim, contentColor = Snow),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Take Break", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Today Summary ──
            Text(text = "📊 Today Summary", style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            val focusTime = prefsManager.productiveTimeMinutes
            val focusHours = focusTime / 60
            val focusMins = focusTime % 60
            val focusTimeStr = if (focusHours > 0) "${focusHours}h ${focusMins}m" else "${focusMins}m"
            val distractions = prefsManager.timesDistractionBlocked
            val focusScore = if (totalMinutes > 0) ((focusTime.toFloat() / (totalMinutes + focusTime).toFloat()) * 100).toInt().coerceAtMost(100) else 100

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Charcoal)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "🔥 Focus Time: $focusTimeStr", style = MaterialTheme.typography.bodyMedium, color = Snow)
                Text(text = "⚠️ Distractions: $distractions", style = MaterialTheme.typography.bodyMedium, color = Snow)
                Text(text = "🏆 Focus Score: $focusScore%", style = MaterialTheme.typography.bodyMedium, color = Snow)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Top Apps Used ──
            Text(text = "📱 Top Apps Used", style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            val tracker = remember { ScreenTimeTracker(context) }
            val appsWithCredit = appUsageSummary?.appUsages?.mapNotNull { usage ->
                val credits = (usage.usageTimeMinutes * tracker.getMultiplierForApp(usage.packageName)).toInt()
                if (credits > 0) usage to credits else null
            }?.sortedByDescending { it.second }?.take(5)

            if (appsWithCredit.isNullOrEmpty()) {
                Text(text = "No distracting apps used today. Great job!", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Charcoal)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    appsWithCredit.forEachIndexed { index, (usage, credits) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${usage.appName.toTitleCase()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Snow
                            )
                            Text(
                                text = "$credits credits",
                                style = MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.ui.graphics.Color(0xFFE57373)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Permissions Status ──
            val allPermissionsGranted = hasUsagePermission && hasAccessibilityPermission && hasOverlayPermission && isDefaultLauncher
            if (!allPermissionsGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Charcoal)
                        .padding(20.dp),
                ) {
                    Text(
                        text = "Permissions needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                        letterSpacing = 2.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    PermissionRow(
                        label = "Usage access",
                        granted = hasUsagePermission,
                        onClick = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    PermissionRow(
                        label = "Accessibility service",
                        granted = hasAccessibilityPermission,
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    PermissionRow(
                        label = "Overlay permission",
                        granted = hasOverlayPermission,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    )
                    PermissionRow(
                        label = "Default launcher",
                        granted = isDefaultLauncher,
                        onClick = {
                            var success = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
                                    if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                                        val roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(roleIntent)
                                        success = true
                                    }
                                } catch (e: Exception) {
                                    // Fall through
                                }
                            }
                            
                            if (!success) {
                                try {
                                    val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val fallback = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(fallback)
                                    } catch (e2: Exception) {
                                        val finalFallback = Intent(Settings.ACTION_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(finalFallback)
                                    }
                                }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Blocked Apps Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${focusModes.find { it.id == activeModeId }?.name ?: "Active"} blocked apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "+",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onNavigateToAddApp(activeModeId) },
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextTertiary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Blocked Apps List ──
        if (blockedApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Charcoal)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onNavigateToAddApp(activeModeId) }
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No apps blocked yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap to add your first app",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDisabled,
                        )
                    }
                }
            }
        } else {
            items(blockedApps, key = { it.packageName }) { app ->
                BlockedAppCard(
                    app = app,
                    onToggle = {
                        val updated = app.copy(isEnabled = !app.isEnabled)
                        prefsManager.addBlockedApp(updated, activeModeId)
                        blockedApps = prefsManager.getBlockedApps(activeModeId)
                    },
                    onRemove = {
                        prefsManager.removeBlockedApp(app.packageName, activeModeId)
                        blockedApps = prefsManager.getBlockedApps(activeModeId)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun BlockedAppCard(
    app: BlockedApp,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (app.isEnabled) Charcoal else SurfaceDim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = !expanded }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = app.appName.toTitleCase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (app.isEnabled) Snow else TextDisabled,
                    fontWeight = FontWeight.Normal,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = "${app.timeLimitMinutes}m session",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                    if (app.blockReels) {
                        Text(
                            text = " · no reels",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                    if (app.blockScrolling) {
                        Text(
                            text = " · anti-scroll",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                }
            }

            // Toggle dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (app.isEnabled) Snow else TextDisabled,
                        CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onToggle() }
            )
        }

        // Expanded details
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Daily limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                    Text(
                        text = "${app.dailyLimitMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = Snow,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "remove",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onRemove() },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled,
                )
            }
        }
    }
}

@Composable
fun PermissionRow(
    label: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (!granted) onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) TextTertiary else Snow,
        )
        Text(
            text = if (granted) "✓" else "→",
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) TextTertiary else Snow,
        )
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

fun isAppDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}
