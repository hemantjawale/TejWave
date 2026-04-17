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
    private val prefsManager = PrefsManager(context)

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
     * Calculates the total attention credits consumed today based on live tracking.
     */
    fun calculateAttentionCredits(): AttentionCredits {
        // Daily reset (only resets consumed, not purchased)
        val midnight = todayMidnight()
        if (prefsManager.lastCreditResetTime < midnight) {
            prefsManager.attentionCreditsConsumed = 0
            prefsManager.lastCreditResetTime = System.currentTimeMillis()
        }

        val totalConsumed = prefsManager.attentionCreditsConsumed
        val baseCredits = 100
        val purchased = prefsManager.purchasedCredits
        val totalCredits = baseCredits + purchased
        val remaining = (totalCredits - totalConsumed).coerceAtMost(totalCredits).coerceAtLeast(0)

        return AttentionCredits(
            remainingCredits = remaining,
            consumedCredits = totalConsumed,
            totalCredits = totalCredits
        )
    }

    /**
     * Increments the consumed credits based on current app usage (distracting apps).
     */
    fun recordUsage(packageName: String) {
        val multiplier = getMultiplierForApp(packageName)
        if (multiplier > 0) {
            prefsManager.attentionCreditsConsumed += multiplier.toInt()
        }
    }

    /**
     * Decreases consumed credits (earns back) for productive app usage.
     * Credits earned = 5 per minute. Cannot go below 0 consumed (i.e. max 100 remaining).
     */
    fun recordProductiveUsage(packageName: String) {
        if (isProductiveApp(packageName)) {
            val earned = 5
            val newConsumed = (prefsManager.attentionCreditsConsumed - earned).coerceAtLeast(0)
            prefsManager.attentionCreditsConsumed = newConsumed
        }
    }

    fun getMultiplierForApp(packageName: String): Double {
        val app = try {
            val pi = context.packageManager.getPackageInfo(packageName, 0)
            // For simplicity, we check known distracting apps or category
            // This is used by the live recorder
            null
        } catch (e: Exception) { null }

        return when {
            packageName == "com.instagram.android" -> 10.0
            packageName == "com.snapchat.android" -> 8.0
            packageName == "com.facebook.katana" -> 8.0
            packageName == "com.google.android.youtube" -> 3.0
            packageName == "com.whatsapp" -> 1.0
            else -> 0.0
        }
    }

    /**
     * Returns true if the app is considered productive and earns credits back.
     * Uses both exact package matches and prefix matching for entire app families.
     */
    fun isProductiveApp(packageName: String): Boolean {
        // Exact match
        if (packageName in productiveAppPackages) return true
        // Prefix match for app families
        if (productiveAppPrefixes.any { packageName.startsWith(it) }) return true
        return false
    }

    companion object {
        /** Prefixes that cover entire productive app families */
        private val productiveAppPrefixes = listOf(
            "com.microsoft.office.",       // All MS Office apps
            "com.microsoft.teams",         // MS Teams
            "com.microsoft.skydrive",      // OneDrive
            "com.microsoft.todos",         // MS To Do
            "com.google.android.apps.docs",// Google Drive/Docs/Sheets/Slides
            "com.google.android.apps.classroom",
            "com.google.android.apps.meetings",
            "com.google.android.apps.tasks",
            "com.adobe.",                  // All Adobe apps (Reader, Scan, etc.)
        )

        /** Comprehensive list of productive app package names */
        private val productiveAppPackages = setOf(
            // Google Workspace
            "com.google.android.apps.docs",              // Google Drive
            "com.google.android.apps.docs.editors.docs",  // Google Docs
            "com.google.android.apps.docs.editors.sheets",// Google Sheets
            "com.google.android.apps.docs.editors.slides",// Google Slides
            "com.google.android.apps.classroom",          // Google Classroom
            "com.google.android.calendar",                // Google Calendar
            "com.google.android.keep",                    // Google Keep
            "com.google.android.apps.meetings",           // Google Meet
            "com.google.android.apps.pdfviewer",          // Google PDF Viewer

            // Microsoft Office & Productivity
            "com.microsoft.office.word",                  // MS Word
            "com.microsoft.office.excel",                 // MS Excel
            "com.microsoft.office.powerpoint",            // MS PowerPoint
            "com.microsoft.office.onenote",               // OneNote
            "com.microsoft.office.outlook",               // Outlook
            "com.microsoft.teams",                        // MS Teams
            "com.microsoft.office.officehubrow",          // MS Office Hub
            "com.microsoft.office.officehubhl",           // MS Office Hub (alt)
            "com.microsoft.skydrive",                     // OneDrive
            "com.microsoft.todos",                        // Microsoft To Do

            // AI Assistants
            "com.openai.chatgpt",                         // ChatGPT
            "com.anthropic.claude",                        // Claude AI
            "com.google.android.apps.bard",               // Google Gemini
            "com.google.android.apps.googleassistant",    // Google Assistant

            // PDF & Document Readers
            "com.adobe.reader",                            // Adobe Acrobat Reader
            "com.adobe.scan",                              // Adobe Scan
            "com.xodo.pdf.reader",                         // Xodo PDF Reader
            "com.foxit.mobile.pdf.lite",                   // Foxit PDF Reader
            "cn.wps.moffice_eng",                          // WPS Office
            "com.artifex.mupdf.viewer.app",                // MuPDF
            "com.kdanmobile.android.pdfreader.google.complimentary", // PDF Reader
            "com.google.android.apps.pdfviewer",           // Google PDF Viewer
            "com.onyx.dox",                                // OnyxBoox PDF
            "org.readera",                                 // ReadEra
            "com.librera.reader",                          // Librera Reader

            // Book & Reading
            "com.amazon.kindle",                           // Kindle
            "com.google.android.apps.books",               // Google Play Books

            // Video Conferencing
            "us.zoom.videomeetings",                      // Zoom
            "com.cisco.webex.meetings",                   // Webex

            // Coding & Dev
            "com.github.android",                         // GitHub

            // Note-taking & Learning
            "com.notion.id",                              // Notion
            "com.evernote",                                // Evernote
            "com.duolingo",                                // Duolingo
            "com.quizlet.quizletandroid",                  // Quizlet
            "com.linkedin.android",                        // LinkedIn
            "com.udemy.android",                           // Udemy
            "com.coursera.app",                            // Coursera
            "com.khanacademy.android",                     // Khan Academy
            "com.medium.reader",                           // Medium

            // Productivity
            "com.todoist",                                 // Todoist
            "com.ticktick.task",                            // TickTick
            "com.slack",                                   // Slack
            "com.Slack",                                   // Slack (alt)
            "com.trello",                                  // Trello
            "com.google.android.apps.tasks",               // Google Tasks
            "com.google.android.gm",                       // Gmail
        )

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