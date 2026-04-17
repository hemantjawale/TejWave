package com.example.tejastra.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tejastra.data.FocusMode
import com.example.tejastra.data.PrefsManager
import com.example.tejastra.ui.theme.*
import com.example.tejastra.utils.toTitleCase

/**
 * Settings screen for launcher favorites and app configuration.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFocusMode: (String) -> Unit,
    onNavigateToSchedule: () -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    var favoriteApps by remember { mutableStateOf(prefsManager.getFavoriteApps()) }
    var focusModes by remember { mutableStateOf(prefsManager.getFocusModes()) }
    var activeModeId by remember { mutableStateOf(prefsManager.activeFocusModeId) }
    var use24HourClock by remember { mutableStateOf(prefsManager.use24HourClock) }
    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        installedApps = getInstalledForSettings(context)
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

            Text(
                text = "Home clock",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose 24-hour or 12-hour time on the launcher home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClockFormatChip(
                    label = "24-hour",
                    isSelected = use24HourClock,
                    onClick = {
                        prefsManager.use24HourClock = true
                        use24HourClock = true
                    },
                    modifier = Modifier.weight(1f),
                )
                ClockFormatChip(
                    label = "12-hour",
                    isSelected = !use24HourClock,
                    onClick = {
                        prefsManager.use24HourClock = false
                        use24HourClock = false
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Divider(color = BorderSubtle, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() },
                    tint = Snow,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = Snow,
                    fontWeight = FontWeight.Light,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Focus modes",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Open a mode to manage blocked apps, auto-location, and behavior.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            FocusModesSection(
                modes = focusModes,
                activeModeId = activeModeId,
                onSelectMode = { mode ->
                    prefsManager.activeFocusModeId = mode.id
                    activeModeId = mode.id
                    onNavigateToFocusMode(mode.id)
                },
                onAddMode = { name, description ->
                    prefsManager.addFocusMode(name, description)
                    focusModes = prefsManager.getFocusModes()
                },
                onDeleteMode = { mode ->
                    prefsManager.removeFocusMode(mode.id)
                    focusModes = prefsManager.getFocusModes()
                    activeModeId = prefsManager.activeFocusModeId
                },
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Automation",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Charcoal)
                    .clickable { onNavigateToSchedule() }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Schedule",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                    Text(
                        text = "Set time blocks for auto-focus",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                Text("→", color = TextTertiary, style = MaterialTheme.typography.titleMedium)
            }


            Spacer(modifier = Modifier.height(32.dp))

            Divider(color = BorderSubtle, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() },
                    tint = Snow,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = Snow,
                    fontWeight = FontWeight.Light,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Launcher Favorite Apps ──
            Text(
                text = "Launcher favorites",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "4 apps shown on your home screen (text only)",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show current favorites
        if (favoriteApps.isEmpty()) {
            item {
                Text(
                    text = "No favorites set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(favoriteApps) { pkg ->
                val appName = installedApps.find { it.packageName == pkg }?.label ?: pkg
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appName.toTitleCase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fog,
                    )
                    Text(
                        text = "×",
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val updated = favoriteApps.toMutableList()
                            updated.remove(pkg)
                            prefsManager.saveFavoriteApps(updated)
                            favoriteApps = prefsManager.getFavoriteApps()
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextTertiary,
                    )
                }
            }
        }

        item {
            if (favoriteApps.size < 4) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "+ Add favorite",
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showAppPicker = !showAppPicker }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // App picker for favorites
        if (showAppPicker) {
            items(
                installedApps.filter { it.packageName !in favoriteApps },
                key = { it.packageName }
            ) { app ->
                Text(
                    text = app.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val updated = favoriteApps.toMutableList()
                            updated.add(app.packageName)
                            prefsManager.saveFavoriteApps(updated.take(4))
                            favoriteApps = prefsManager.getFavoriteApps()
                            showAppPicker = false
                        }
                        .padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fog,
                    fontWeight = FontWeight.Light,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))

            Divider(color = BorderSubtle, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Bottom Shortcuts ──
            var bottomLeftApp by remember { mutableStateOf(prefsManager.bottomLeftApp) }
            var bottomRightApp by remember { mutableStateOf(prefsManager.bottomRightApp) }
            var pickingSide by remember { mutableStateOf<String?>(null) } // "Left" or "Right"

            Text(
                text = "Bottom shortcuts",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Apps at bottom corners of your home screen",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Left
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { pickingSide = if (pickingSide == "Left") null else "Left" },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Left corner",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fog,
                )
                Text(
                    text = if (bottomLeftApp.isNotEmpty()) (installedApps.find { it.packageName == bottomLeftApp }?.label?.toTitleCase() ?: "Unknown") else "None",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Snow,
                )
            }

            if (pickingSide == "Left") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Charcoal)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "None",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { bottomLeftApp = ""; prefsManager.bottomLeftApp = ""; pickingSide = null }
                            .padding(vertical = 12.dp, horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                    installedApps.forEach { app ->
                        Text(
                            text = app.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { bottomLeftApp = app.packageName; prefsManager.bottomLeftApp = app.packageName; pickingSide = null }
                                .padding(vertical = 12.dp, horizontal = 20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fog,
                            fontWeight = FontWeight.Light,
                        )
                    }
                }
            }

            // Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { pickingSide = if (pickingSide == "Right") null else "Right" },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Right corner",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fog,
                )
                Text(
                    text = if (bottomRightApp.isNotEmpty()) (installedApps.find { it.packageName == bottomRightApp }?.label?.toTitleCase() ?: "Unknown") else "None",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Snow,
                )
            }

            if (pickingSide == "Right") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Charcoal)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "None",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { bottomRightApp = ""; prefsManager.bottomRightApp = ""; pickingSide = null }
                            .padding(vertical = 12.dp, horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                    installedApps.forEach { app ->
                        Text(
                            text = app.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { bottomRightApp = app.packageName; prefsManager.bottomRightApp = app.packageName; pickingSide = null }
                                .padding(vertical = 12.dp, horizontal = 20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fog,
                            fontWeight = FontWeight.Light,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Divider(color = BorderSubtle, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Motivation Text ──
            Text(
                text = "Motivation text",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Subtle text displayed in your launcher. Leave blank to hide.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            var motivationTexts by remember { mutableStateOf(prefsManager.getMotivationTexts()) }
            var editingIndex by remember { mutableStateOf(-1) }
            var editingText by remember { mutableStateOf("") }
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                motivationTexts.forEachIndexed { index, quote ->
                    if (editingIndex == index) {
                        // Inline edit mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Snow, RoundedCornerShape(8.dp))
                                .background(Charcoal)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = editingText,
                                onValueChange = { editingText = it },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Snow),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Snow),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save",
                                color = Snow,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    if (editingText.isNotBlank()) {
                                        val updated = motivationTexts.toMutableList()
                                        updated[index] = editingText
                                        prefsManager.saveMotivationTexts(updated)
                                        motivationTexts = updated
                                    }
                                    editingIndex = -1
                                }
                            )
                        }
                    } else {
                        // Display mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Charcoal)
                                .clickable {
                                    editingIndex = index
                                    editingText = quote
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = quote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Snow,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✏",
                                color = TextTertiary,
                                modifier = Modifier
                                    .clickable {
                                        editingIndex = index
                                        editingText = quote
                                    }
                                    .padding(end = 12.dp)
                            )
                            Text(
                                text = "✕",
                                color = TextDisabled,
                                modifier = Modifier.clickable {
                                    val updated = motivationTexts.toMutableList()
                                    updated.removeAt(index)
                                    prefsManager.saveMotivationTexts(updated)
                                    motivationTexts = updated
                                    if (editingIndex == index) editingIndex = -1
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            var newQuote by remember { mutableStateOf("") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = newQuote,
                    onValueChange = { newQuote = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .background(Charcoal)
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Snow),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Snow),
                    decorationBox = { innerTextField ->
                        if (newQuote.isEmpty()) {
                            Text(
                                text = "Add a quote or reminder...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextDisabled,
                            )
                        }
                        innerTextField()
                    }
                )
                
                if (newQuote.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = motivationTexts.toMutableList()
                            updated.add(newQuote)
                            prefsManager.saveMotivationTexts(updated)
                            motivationTexts = updated
                            newQuote = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Snow, contentColor = Void)
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Divider(color = BorderSubtle, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Dumb Phone Mode ──
            Text(
                text = "Dumb-phone mode",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            var isDumbPhoneMode by remember { mutableStateOf(prefsManager.isDumbPhoneMode) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDumbPhoneMode) Charcoal else SurfaceDim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        prefsManager.isDumbPhoneMode = !isDumbPhoneMode
                        isDumbPhoneMode = prefsManager.isDumbPhoneMode
                    }
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Disable app drawer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Locks your phone to ONLY the 4 favorite apps below the clock. Disables the swipe-up gesture completely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        lineHeight = 18.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (isDumbPhoneMode) Snow else TextDisabled,
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            var shouldShowKeyboardOnDrawerOpen by remember {
                mutableStateOf(prefsManager.shouldShowKeyboardOnDrawerOpen)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (shouldShowKeyboardOnDrawerOpen) Charcoal else SurfaceDim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        prefsManager.shouldShowKeyboardOnDrawerOpen = !shouldShowKeyboardOnDrawerOpen
                        shouldShowKeyboardOnDrawerOpen = prefsManager.shouldShowKeyboardOnDrawerOpen
                    }
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open keyboard on swipe-up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When you directly swipe up to open the app drawer, the search keyboard pops up automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        lineHeight = 18.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (shouldShowKeyboardOnDrawerOpen) Snow else TextDisabled,
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            var isDoubleTapLockEnabled by remember { mutableStateOf(prefsManager.isDoubleTapLockEnabled) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDoubleTapLockEnabled) Charcoal else SurfaceDim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        prefsManager.isDoubleTapLockEnabled = !isDoubleTapLockEnabled
                        isDoubleTapLockEnabled = prefsManager.isDoubleTapLockEnabled
                    }
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Double tap to lock screen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Locks the screen when you randomly double-tap empty space on the home launcher menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        lineHeight = 18.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (isDoubleTapLockEnabled) Snow else TextDisabled,
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Divider(color = BorderSubtle, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(32.dp))

            // ── DNS Blocking ──
            Text(
                text = "Network & security",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Charcoal)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("DNS", "family.cloudflare-dns.com")
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "DNS copied! Paste into 'Private DNS'", android.widget.Toast.LENGTH_LONG).show()
                        
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch(e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Enable Cloudflare Family DNS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Blocks malware naturally. Tap to copy address and open Android network settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Divider(color = BorderSubtle, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(32.dp))

            // ── About ──
            Text(
                text = "About",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TejAstra",
                style = MaterialTheme.typography.headlineMedium,
                color = Snow,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "A distraction blocker & mindful launcher\nDesigned to help you reclaim your attention",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Version 1.0",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
            )

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun FocusModesSection(
    modes: List<FocusMode>,
    activeModeId: String,
    onSelectMode: (FocusMode) -> Unit,
    onAddMode: (String, String) -> Unit,
    onDeleteMode: (FocusMode) -> Unit,
) {
    var newModeName by remember { mutableStateOf("") }
    var newModeDescription by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        modes.forEach { mode ->
            val isSelected = mode.id == activeModeId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Charcoal)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectMode(mode) }
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = mode.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Snow,
                    )
                    Text(
                        text = if (isSelected) "Active" else "Open",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) Snow else TextTertiary,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tap to open this focus mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                    )
                    if (modes.size > 1) {
                        Text(
                            text = "Remove",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDisabled,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onDeleteMode(mode) }
                        )
                    }
                }
            }
        }

        Divider(color = BorderSubtle, thickness = 0.5.dp)

        Text(
            text = "Create a new focus mode",
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled,
            letterSpacing = 2.sp,
        )

        BasicTextField(
            value = newModeName,
            onValueChange = { newModeName = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Charcoal)
                .padding(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Snow),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Snow),
            decorationBox = { innerTextField ->
                if (newModeName.isEmpty()) {
                    Text(
                        text = "New mode name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDisabled,
                    )
                }
                innerTextField()
            }
        )

        BasicTextField(
            value = newModeDescription,
            onValueChange = { newModeDescription = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Charcoal)
                .padding(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Snow),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Snow),
            decorationBox = { innerTextField ->
                if (newModeDescription.isEmpty()) {
                    Text(
                        text = "What should this mode protect?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDisabled,
                    )
                }
                innerTextField()
            }
        )

        Button(
            onClick = {
                onAddMode(
                    newModeName.trim(),
                    newModeDescription.trim().ifBlank { "A custom focus mode." },
                )
                newModeName = ""
                newModeDescription = ""
            },
            enabled = newModeName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Snow,
                contentColor = Void,
            ),
        ) {
            Text(
                text = "Add focus mode",
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ClockFormatChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Snow else Charcoal)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Void else Snow,
            fontWeight = FontWeight.Medium,
        )
    }
}

fun getInstalledForSettings(context: Context): List<AppInfo> {
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

private fun Double.formatCoordinate(): String = String.format("%.5f", this)
