package com.example.tejastra.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tejastra.data.NotificationFilterRule
import com.example.tejastra.data.PrefsManager
import com.example.tejastra.ui.theme.*

@Composable
fun NotificationFilterScreen(
    modeId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var rules by remember { mutableStateOf(prefsManager.getNotificationFilterRules(modeId)) }
    
    // UI state
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        installedApps = getInstalledForSettings(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        item {
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
                    text = "Notification Filters",
                    style = MaterialTheme.typography.titleMedium,
                    color = Snow,
                    fontWeight = FontWeight.Light,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Check if notification access is granted
            if (!isNotificationAccessGranted(context)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Charcoal)
                        .clickable {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permission Required",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE57373),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap here to allow TejAstra to read notifications to filter them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Fog
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = "Smart Notifications",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Silence distracting messages but let urgent ones through based on keywords or who is messaging.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // List of currently configured apps
        if (rules.isEmpty() && !showAppPicker && selectedApp == null) {
            item {
                Text(
                    text = "No filters configured yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(rules) { rule ->
                RuleCard(
                    rule = rule,
                    onEdit = {
                        selectedApp = installedApps.find { it.packageName == rule.packageName } ?: AppInfo(rule.appName, rule.packageName)
                    },
                    onDelete = {
                        val updated = rules.filter { it.packageName != rule.packageName }
                        prefsManager.saveNotificationFilterRules(updated, modeId)
                        rules = prefsManager.getNotificationFilterRules(modeId)
                    }
                )
            }
        }

        if (selectedApp == null && !showAppPicker) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showAppPicker = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Charcoal,
                        contentColor = Snow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add App Filter")
                }
            }
        }

        // App Picker
        if (showAppPicker) {
            item {
                Text(
                    text = "Select App to Filter",
                    style = MaterialTheme.typography.titleMedium,
                    color = Snow,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            items(installedApps.filter { app -> rules.none { it.packageName == app.packageName } }) { app ->
                Text(
                    text = app.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedApp = app
                            showAppPicker = false
                        }
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fog
                )
            }
        }

        // Rule Editor for Selected App
        if (selectedApp != null) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Divider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(24.dp))
                
                RuleEditor(
                    appInfo = selectedApp!!,
                    initialRule = rules.find { it.packageName == selectedApp!!.packageName },
                    onSave = { rule ->
                        val updated = rules.filter { it.packageName != rule.packageName }.toMutableList()
                        updated.add(rule)
                        prefsManager.saveNotificationFilterRules(updated, modeId)
                        rules = prefsManager.getNotificationFilterRules(modeId)
                        selectedApp = null
                    },
                    onCancel = {
                        selectedApp = null
                    }
                )
            }
        }
    }
}

@Composable
fun RuleCard(rule: NotificationFilterRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal)
            .clickable { onEdit() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.appName,
                style = MaterialTheme.typography.titleSmall,
                color = Snow
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            if (rule.isAllowed) {
                Text(text = "All allowed", color = Color(0xFF81C784), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    text = "Blocked except: ${rule.allowedContacts.size} contacts, ${rule.urgencyKeywords.size} keywords",
                    color = Fog,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(
            text = "✕",
            color = TextTertiary,
            modifier = Modifier
                .clickable { onDelete() }
                .padding(8.dp)
        )
    }
}

@Composable
fun RuleEditor(
    appInfo: AppInfo,
    initialRule: NotificationFilterRule?,
    onSave: (NotificationFilterRule) -> Unit,
    onCancel: () -> Unit
) {
    var isAllowed by remember { mutableStateOf(initialRule?.isAllowed ?: false) }
    var contacts by remember { mutableStateOf(initialRule?.allowedContacts ?: emptyList()) }
    var keywords by remember { mutableStateOf(initialRule?.urgencyKeywords ?: emptyList()) }

    var newContact by remember { mutableStateOf("") }
    var newKeyword by remember { mutableStateOf("") }

    Text(
        text = "Filtering: ${appInfo.label}",
        style = MaterialTheme.typography.titleLarge,
        color = Snow,
        fontWeight = FontWeight.Light
    )
    Spacer(modifier = Modifier.height(16.dp))

    // Allow All Toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isAllowed = !isAllowed }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Allow all notifications", color = Snow, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = isAllowed, onCheckedChange = { isAllowed = it })
    }

    AnimatedVisibility(visible = !isAllowed) {
        Column {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Allowed Contacts", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text("Exact name matches (e.g. 'John Doe')", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            contacts.forEach { contact ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "• $contact", color = Fog)
                    Text("✕", color = TextTertiary, modifier = Modifier.clickable {
                        contacts = contacts.filter { it != contact }
                    })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = newContact,
                onValueChange = { newContact = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Snow),
                cursorBrush = SolidColor(Snow),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (newContact.isNotBlank() && !contacts.contains(newContact.trim())) {
                        contacts = contacts + newContact.trim()
                        newContact = ""
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Charcoal)
                    .padding(12.dp),
                decorationBox = { innerTextField ->
                    if (newContact.isEmpty()) Text("Add person (press enter to save)", color = TextDisabled)
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("Urgency Keywords", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text("If message contains these words, allow it.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            keywords.forEach { keyword ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "• $keyword", color = Fog)
                    Text("✕", color = TextTertiary, modifier = Modifier.clickable {
                        keywords = keywords.filter { it != keyword }
                    })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = newKeyword,
                onValueChange = { newKeyword = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Snow),
                cursorBrush = SolidColor(Snow),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (newKeyword.isNotBlank() && !keywords.contains(newKeyword.trim())) {
                        keywords = keywords + newKeyword.trim()
                        newKeyword = ""
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Charcoal)
                    .padding(12.dp),
                decorationBox = { innerTextField ->
                    if (newKeyword.isEmpty()) Text("Add keyword (e.g. 'urgent', 'bug')", color = TextDisabled)
                    innerTextField()
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Fog)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                onSave(
                    NotificationFilterRule(
                        packageName = appInfo.packageName,
                        appName = appInfo.label,
                        isAllowed = isAllowed,
                        allowedContacts = contacts,
                        urgencyKeywords = keywords
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Snow, contentColor = Void)
        ) {
            Text("Save")
        }
    }
}

fun isNotificationAccessGranted(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val packageName = context.packageName
    return enabledListeners != null && enabledListeners.contains(packageName)
}
