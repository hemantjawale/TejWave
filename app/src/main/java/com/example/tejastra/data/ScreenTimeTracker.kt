package com.example.tejastra.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.Calendar

class ScreenTimeTracker(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val tag = "ScreenTimeTracker"

    private fun todayMidnight(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getTodaySummary(): ScreenTimeSummary {
        val startTime = todayMidnight()
        val endTime = System.currentTimeMillis()

        val usageMap = calculateForegroundTime(startTime, endTime)

        val pm = context.packageManager
        var totalMinutes = 0L
        val appUsages = mutableListOf<AppUsageInfo>()

        usageMap.toList()
            .sortedByDescending { it.second }
            .forEach { (packageName, timeInMs) ->

                val minutes = timeInMs / 60000
                if (minutes <= 0) return@forEach

                totalMinutes += minutes

                val appInfo = try {
                    pm.getApplicationInfo(packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }

                val appName =
                    appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName

                val categoryName =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && appInfo != null) {
                        when (appInfo.category) {
                            android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Gaming"
                            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
                            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Image"
                            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "News"
                            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Maps"
                            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                            else -> "Other"
                        }
                    } else {
                        "Other"
                    }

                appUsages.add(
                    AppUsageInfo(
                        packageName = packageName,
                        appName = appName,
                        usageTimeMinutes = minutes,
                        categoryName = categoryName,
                    )
                )
            }

        Log.d(tag, "Today total: ${totalMinutes}m from ${appUsages.size} apps")

        return ScreenTimeSummary(
            totalMinutes = totalMinutes,
            appUsages = appUsages.take(20),
        )
    }

    private fun calculateForegroundTime(startTime: Long, endTime: Long): Map<String, Long> {

        val usageMap = mutableMapOf<String, Long>()

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var currentApp: String? = null
        var sessionStart = 0L
        var isScreenOn = true

        val FG_LEGACY = 1
        val BG_LEGACY = 2
        val FG_MODERN = 7
        val BG_MODERN = 8

        val SCREEN_INTERACTIVE = 15
        val SCREEN_NON_INTERACTIVE = 16

        val ignoredPackages = setOf(
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.inputmethod.latin"
        )

        try {
            while (events.hasNextEvent()) {
                events.getNextEvent(event)

                val pkg = event.packageName ?: continue
                val ts = event.timeStamp
                val type = event.eventType

                // 📱 Screen ON/OFF handling (CRITICAL FIX)
                when (type) {
                    SCREEN_INTERACTIVE -> {
                        isScreenOn = true
                    }

                    SCREEN_NON_INTERACTIVE -> {
                        isScreenOn = false

                        // End current session immediately
                        if (currentApp != null && sessionStart > 0 && ts > sessionStart) {
                            val duration = ts - sessionStart
                            if (duration > 1000) {
                                usageMap[currentApp!!] =
                                    (usageMap[currentApp!!] ?: 0L) + duration
                            }
                        }

                        currentApp = null
                        sessionStart = 0L
                    }
                }

                // Ignore if screen OFF
                if (!isScreenOn) continue

                // Ignore noise apps
                if (pkg in ignoredPackages || pkg.contains("launcher")) continue

                // ▶️ Foreground
                if (type == FG_LEGACY || type == FG_MODERN) {

                    // close previous app
                    if (currentApp != null && sessionStart > 0 && ts > sessionStart) {
                        val duration = ts - sessionStart
                        if (duration > 1000) {
                            usageMap[currentApp!!] =
                                (usageMap[currentApp!!] ?: 0L) + duration
                        }
                    }

                    currentApp = pkg
                    sessionStart = ts
                }

                // ⏹ Background
                else if (type == BG_LEGACY || type == BG_MODERN) {
                    if (pkg == currentApp && sessionStart > 0 && ts > sessionStart) {
                        val duration = ts - sessionStart
                        if (duration > 1000) {
                            usageMap[pkg] =
                                (usageMap[pkg] ?: 0L) + duration
                        }

                        currentApp = null
                        sessionStart = 0L
                    }
                }
            }

            // ⏳ Handle ongoing session
            if (currentApp != null && sessionStart > 0 && endTime > sessionStart) {
                val duration = endTime - sessionStart
                if (duration > 1000) {
                    usageMap[currentApp!!] =
                        (usageMap[currentApp!!] ?: 0L) + duration
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "Error reading usage events", e)
        }

        return usageMap
    }

    fun getAppUsageToday(packageName: String): Long {
        val startTime = todayMidnight()
        val endTime = System.currentTimeMillis()
        val usageMap = calculateForegroundTime(startTime, endTime)
        return (usageMap[packageName] ?: 0L) / 60000
    }

    /**
     * Calculates the total attention credits consumed today based on usage rules.
     */
    fun calculateAttentionCredits(): AttentionCredits {
        val summary = getTodaySummary()
        var consumed = 0.0

        summary.appUsages.forEach { app ->
            val minutes = app.usageTimeMinutes.toDouble()
            
            val multiplier = when {
                app.packageName == "com.instagram.android" -> 10.0
                app.packageName == "com.snapchat.android" -> 8.0
                app.packageName == "com.facebook.katana" -> 8.0
                app.packageName == "com.google.android.youtube" -> 3.0
                app.packageName == "com.whatsapp" -> 1.0
                app.categoryName in listOf("Image", "Productivity", "News") -> 2.0
                app.categoryName == "Social" -> 5.0 // Generic social apps (FB, etc) get a middle penalty
                app.categoryName == "Gaming" -> 5.0
                app.categoryName == "Video" -> 3.0
                else -> 0.0 // Default apps (Launcher, Phone, Settings, etc) don't consume credits
            }
            
            consumed += (minutes * multiplier)
        }

        val totalConsumed = consumed.toInt()
        val totalCredits = 100
        val remaining = (totalCredits - totalConsumed).coerceAtLeast(0)

        return AttentionCredits(
            remainingCredits = remaining,
            consumedCredits = totalConsumed,
            totalCredits = totalCredits
        )
    }

    companion object {
        fun hasPermission(context: Context): Boolean {
            val usm =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            return stats != null && stats.isNotEmpty()
        }
    }
}