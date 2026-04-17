package com.example.tejastra.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tejastra.data.LocationModeManager
import com.example.tejastra.data.ModeLocationConfig
import com.example.tejastra.data.PrefsManager
import com.example.tejastra.ui.theme.BorderSubtle
import com.example.tejastra.ui.theme.Charcoal
import com.example.tejastra.ui.theme.Graphite
import com.example.tejastra.ui.theme.Snow
import com.example.tejastra.ui.theme.SurfaceDim
import com.example.tejastra.ui.theme.TextDisabled
import com.example.tejastra.ui.theme.TextTertiary
import com.example.tejastra.ui.theme.Void
import kotlinx.coroutines.launch

@Composable
fun FocusModeDetailScreen(
    modeId: String,
    onBack: () -> Unit,
    onNavigateToAddApp: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    val scope = rememberCoroutineScope()

    var modes by remember { mutableStateOf(prefsManager.getFocusModes()) }
    var activeModeId by remember { mutableStateOf(prefsManager.activeFocusModeId) }
    var blockedApps by remember { mutableStateOf(prefsManager.getBlockedApps(modeId)) }
    var locationConfigs by remember { mutableStateOf(prefsManager.getModeLocationConfigs()) }
    var isLocationAutomationEnabled by remember { mutableStateOf(prefsManager.isLocationAutomationEnabled) }
    var showManualLocationDialog by remember { mutableStateOf(false) }
    var manualLocationInput by remember { mutableStateOf("") }

    val mode = modes.find { it.id == modeId }
    val locationConfig = locationConfigs[modeId]

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) {
            Toast.makeText(context, "Location permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Location permission is required for auto-switching", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(modeId) {
        LocationModeManager.syncModeFromCurrentLocation(context, prefsManager)
        modes = prefsManager.getFocusModes()
        activeModeId = prefsManager.activeFocusModeId
        blockedApps = prefsManager.getBlockedApps(modeId)
        locationConfigs = prefsManager.getModeLocationConfigs()
        isLocationAutomationEnabled = prefsManager.isLocationAutomationEnabled
    }

    if (mode == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Void),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Mode not found", color = Snow)
        }
        return
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "<",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Snow,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (activeModeId == modeId) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        letterSpacing = 2.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = mode.name,
                style = MaterialTheme.typography.headlineLarge,
                color = Snow,
                fontWeight = FontWeight.Light,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (activeModeId != modeId) {
                Button(
                    onClick = {
                        prefsManager.activeFocusModeId = modeId
                        activeModeId = modeId
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Snow,
                        contentColor = Void,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Set as active")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = "Location automation",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isLocationAutomationEnabled) Charcoal else SurfaceDim, RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        prefsManager.isLocationAutomationEnabled = !isLocationAutomationEnabled
                        isLocationAutomationEnabled = prefsManager.isLocationAutomationEnabled
                    }
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-switch this mode by location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When you enter this saved place, TejAstra can jump into ${mode.name}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isLocationAutomationEnabled) Snow else TextDisabled, RoundedCornerShape(99.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!LocationModeManager.hasLocationPermission(context)) {
                Text(
                    text = "Grant location permission to save a place for this mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grant location",
                    style = MaterialTheme.typography.labelLarge,
                    color = Snow,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Charcoal, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                Text(
                    text = locationConfig?.let { "Saved place: ${it.label}" } ?: "No location saved yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Snow,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (locationConfig == null) {
                        "Save your current location and link it to ${mode.name}."
                    } else {
                        "Choose how tightly this mode should trigger around the saved place."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(100f, 150f, 250f).forEach { radius ->
                        val selected = locationConfig?.radiusMeters == radius
                        Box(
                            modifier = Modifier
                                .background(if (selected) Snow else Graphite, RoundedCornerShape(999.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    locationConfig?.let {
                                        prefsManager.upsertModeLocationConfig(it.copy(radiusMeters = radius))
                                        locationConfigs = prefsManager.getModeLocationConfigs()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = "${radius.toInt()}m",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Void else Snow,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (locationConfig == null) "Use current location" else "Update saved place",
                        style = MaterialTheme.typography.labelLarge,
                        color = Snow,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!LocationModeManager.hasLocationPermission(context)) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            } else {
                                scope.launch {
                                    val location = LocationModeManager.getCurrentLocation(context)
                                    if (location == null) {
                                        Toast.makeText(context, "Could not get current location", Toast.LENGTH_LONG).show()
                                        return@launch
                                    }
                                    prefsManager.upsertModeLocationConfig(
                                        ModeLocationConfig(
                                            modeId = modeId,
                                            latitude = location.latitude,
                                            longitude = location.longitude,
                                            radiusMeters = locationConfig?.radiusMeters ?: 150f,
                                            label = "${location.latitude.formatCoordinate()}, ${location.longitude.formatCoordinate()}",
                                            isEnabled = true,
                                        )
                                    )
                                    locationConfigs = prefsManager.getModeLocationConfigs()
                                    Toast.makeText(context, "${mode.name} location saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    if (locationConfig != null) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                prefsManager.removeModeLocationConfig(modeId)
                                locationConfigs = prefsManager.getModeLocationConfigs()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Enter coordinates manually",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        showManualLocationDialog = true
                    }
                )
            }

            if (showManualLocationDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showManualLocationDialog = false },
                    title = {
                        Text("Enter Coordinates", color = Snow)
                    },
                    text = {
                        Column {
                            Text("Find a place in Google Maps, drop a pin, copy the coordinates (e.g. 37.77, -122.41) and paste here.", color = TextTertiary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = manualLocationInput,
                                onValueChange = { manualLocationInput = it },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Snow),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Snow),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceDim, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                decorationBox = { innerTextField ->
                                    if (manualLocationInput.isEmpty()) {
                                        Text("Lat, Lng", color = TextDisabled)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val parts = manualLocationInput.split(",")
                                if (parts.size >= 2) {
                                    val lat = parts[0].trim().toDoubleOrNull()
                                    val lng = parts[1].trim().toDoubleOrNull()
                                    if (lat != null && lng != null) {
                                        prefsManager.upsertModeLocationConfig(
                                            ModeLocationConfig(
                                                modeId = modeId,
                                                latitude = lat,
                                                longitude = lng,
                                                radiusMeters = locationConfig?.radiusMeters ?: 150f,
                                                label = "${lat.formatCoordinate()}, ${lng.formatCoordinate()}",
                                                isEnabled = true,
                                            )
                                        )
                                        locationConfigs = prefsManager.getModeLocationConfigs()
                                        showManualLocationDialog = false
                                        Toast.makeText(context, "${mode.name} location saved", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Invalid coordinates", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter in format: lat, lng", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Snow, contentColor = Void)
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showManualLocationDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Charcoal, contentColor = Snow)
                        ) {
                            Text("Cancel")
                        }
                    },
                    containerColor = Charcoal
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Blocked apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "+ Add app",
                    style = MaterialTheme.typography.labelLarge,
                    color = Snow,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onNavigateToAddApp(modeId) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (blockedApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Charcoal, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onNavigateToAddApp(modeId) }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No blocked apps in ${mode.name} yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
            }
        } else {
            items(blockedApps, key = { it.packageName }) { app ->
                BlockedAppCard(
                    app = app,
                    onToggle = {
                        prefsManager.addBlockedApp(app.copy(isEnabled = !app.isEnabled), modeId)
                        blockedApps = prefsManager.getBlockedApps(modeId)
                    },
                    onRemove = {
                        prefsManager.removeBlockedApp(app.packageName, modeId)
                        blockedApps = prefsManager.getBlockedApps(modeId)
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private fun Double.formatCoordinate(): String = String.format("%.5f", this)
