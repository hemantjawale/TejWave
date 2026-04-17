package com.example.tejastra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tejastra.data.ScreenTimeTracker
import com.example.tejastra.ui.theme.*
import com.example.tejastra.utils.toTitleCase

/**
 * Detailed screen time view showing per-app usage breakdown.
 */
@Composable
fun ScreenTimeScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var screenTimeText by remember { mutableStateOf("—") }
    var appUsages by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        try {
            if (ScreenTimeTracker.hasPermission(context)) {
                val tracker = ScreenTimeTracker(context)
                val summary = tracker.getTodaySummary()
                val hours = summary.totalMinutes / 60
                val mins = summary.totalMinutes % 60
                screenTimeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

                appUsages = summary.appUsages.map { usage ->
                    val h = usage.usageTimeMinutes / 60
                    val m = usage.usageTimeMinutes % 60
                    val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                    Triple(usage.appName, timeStr, usage.categoryName)
                }
            }
        } catch (_: Exception) { }
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
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Screen time",
                    style = MaterialTheme.typography.titleMedium,
                    color = Snow,
                    fontWeight = FontWeight.Light,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Total ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "⟳",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { refreshTrigger++ },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTertiary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = screenTimeText,
                style = MaterialTheme.typography.displayLarge,
                color = Snow,
                fontWeight = FontWeight.Thin,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Per-App Breakdown Header ──
            Text(
                text = "Breakdown",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── App Usage List ──
        if (appUsages.isEmpty()) {
            item {
                Text(
                    text = "No usage data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            }
        } else {
            items(appUsages) { (appName, timeStr, categoryName) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appName.toTitleCase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fog,
                            fontWeight = FontWeight.Light,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDisabled,
                        )
                    }
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                        fontWeight = FontWeight.Light,
                    )
                }

                Divider(color = BorderSubtle, thickness = 0.5.dp)
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
