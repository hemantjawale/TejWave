package com.example.tejastra.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tejastra.data.BlockedApp
import com.example.tejastra.data.FocusMode
import com.example.tejastra.data.PrefsManager
import com.example.tejastra.ui.theme.*
import com.example.tejastra.utils.toTitleCase

/**
 * Screen to add a new app to the blocked list.
 * Shows installed apps, lets user configure time limit and blocking options.
 */
@Composable
fun AddAppScreen(
    initialModeId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    val focusModes = remember { prefsManager.getFocusModes() }
    var targetModeId by remember { mutableStateOf(initialModeId ?: prefsManager.activeFocusModeId) }
    val targetMode = remember(targetModeId) { focusModes.find { it.id == targetModeId } }

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var timeLimitMinutes by remember { mutableIntStateOf(5) }
    var dailyLimitMinutes by remember { mutableIntStateOf(30) }
    var blockReels by remember { mutableStateOf(false) }
    var blockScrolling by remember { mutableStateOf(false) }

    // Known social media packages for showing relevant options
    val socialMediaPackages = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.pinterest",
    )

    val supportsReelBlock = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
    )

    LaunchedEffect(Unit) {
        installedApps = getInstalled(context)
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            // Show social media first, then others
            installedApps.sortedByDescending { it.packageName in socialMediaPackages }
        } else {
            installedApps.filter {
                it.label.contains(searchQuery, ignoreCase = true)
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

            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "←",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Snow,
                )
                Text(
                    text = "Add blocked app",
                    style = MaterialTheme.typography.titleMedium,
                    color = Snow,
                    fontWeight = FontWeight.Light,
                )
                // Spacer for symmetry
                Spacer(modifier = Modifier.width(24.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Choose focus mode",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                focusModes.forEach { mode ->
                    val isSelected = mode.id == targetModeId
                    ModeChip(
                        mode = mode,
                        isSelected = isSelected,
                        onClick = { targetModeId = mode.id },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Configuration (when app selected) ──
        if (selectedApp != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Charcoal)
                        .padding(24.dp),
                ) {
                    Text(
                        text = selectedApp!!.label.toTitleCase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Snow,
                        fontWeight = FontWeight.Light,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = selectedApp!!.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Blocking in ${targetMode?.name ?: "mode"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Session Time Limit ──
                    Text(
                        text = "Session time limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 15, 30).forEach { mins ->
                            val isSelected = timeLimitMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Snow else Graphite)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { timeLimitMinutes = mins }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${mins}m",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) Void else TextTertiary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Daily Limit ──
                    Text(
                        text = "Daily limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15, 30, 60, 120).forEach { mins ->
                            val isSelected = dailyLimitMinutes == mins
                            val label = if (mins >= 60) "${mins / 60}h" else "${mins}m"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Snow else Graphite)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { dailyLimitMinutes = mins }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) Void else TextTertiary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Block Reels (if applicable) ──
                    if (selectedApp!!.packageName in supportsReelBlock) {
                        ToggleRow(
                            label = "Block reels / shorts",
                            description = "Auto-navigate away from reels tab",
                            isOn = blockReels,
                            onToggle = { blockReels = !blockReels },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ── Block Scrolling ──
                    ToggleRow(
                        label = "Block mindless scrolling",
                        description = "Interrupt after rapid-fire scrolling",
                        isOn = blockScrolling,
                        onToggle = { blockScrolling = !blockScrolling },
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Save Button ──
                    Button(
                        onClick = {
                            prefsManager.addBlockedApp(
                                BlockedApp(
                                    packageName = selectedApp!!.packageName,
                                    appName = selectedApp!!.label,
                                    timeLimitMinutes = timeLimitMinutes,
                                    blockReels = blockReels,
                                    blockScrolling = blockScrolling,
                                    isEnabled = true,
                                    dailyLimitMinutes = dailyLimitMinutes,
                                ),
                                targetModeId,
                            )
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Snow,
                            contentColor = Void,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Save to ${targetMode?.name ?: "mode"}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { selectedApp = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Choose a different app",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            // ── Search Bar ──
            item {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Charcoal)
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Snow,
                    ),
                    cursorBrush = SolidColor(Snow),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search apps",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextDisabled,
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── App List ──
            items(filteredApps, key = { it.packageName }) { app ->
                val isBlocked = prefsManager.isAppBlocked(app.packageName, targetModeId)
                val isSocial = app.packageName in socialMediaPackages

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (!isBlocked) selectedApp = app }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isBlocked) TextDisabled else Snow,
                            fontWeight = FontWeight.Light,
                        )
                        if (isSocial) {
                            Text(
                                text = "Social media",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDisabled,
                            )
                        }
                    }

                    if (isBlocked) {
                        Text(
                            text = "in ${targetMode?.name ?: "mode"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDisabled,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun ModeChip(
    mode: FocusMode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isSelected) Snow else Charcoal)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = mode.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Void else Snow,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun ToggleRow(
    label: String,
    description: String,
    isOn: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Snow,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isOn) Snow else Graphite)
                .padding(2.dp),
        ) {
            val offsetX by animateDpAsState(
                targetValue = if (isOn) 20.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.7f),
                label = "toggle"
            )
            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .size(20.dp)
                    .background(if (isOn) Void else Ash, CircleShape)
            )
        }
    }
}

data class AppInfo(
    val label: String,
    val packageName: String,
)

private fun getInstalled(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    @Suppress("DEPRECATION")
    val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

    return resolveInfos
        .filter { it.activityInfo.packageName != context.packageName }
        .map { resolveInfo ->
            AppInfo(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName,
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
