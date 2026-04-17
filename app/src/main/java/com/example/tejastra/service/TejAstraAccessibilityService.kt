package com.example.tejastra.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.CountDownTimer
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.tejastra.data.PrefsManager
import com.example.tejastra.data.ScreenTimeTracker

/**
 * Accessibility service that:
 * 1. Detects when a blocked app is launched → shows breathe overlay
 * 2. Blocks Reels/Shorts tabs in Instagram/YouTube
 * 3. Blocks mindless scrolling by detecting rapid scroll events
 */
class TejAstraAccessibilityService : AccessibilityService() {

    private lateinit var prefsManager: PrefsManager
    private var currentForegroundPackage: String? = null
    private var sessionTimer: CountDownTimer? = null
    private var isSessionActive = false
    private var breatheShownForPackage: String? = null
    private var scrollEventCount = 0
    private var lastScrollResetTime = 0L
    private var lastReelCheckTime = 0L
    private var lastScheduleCheckTime = 0L
    private var currentMode: com.example.tejastra.data.TimeMode = com.example.tejastra.data.TimeMode.FREE_TIME
    private var breakStartTime: Long = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var usageRecordingTask: Runnable? = null

    // Track which section of the app the user is in
    private var currentAppSection = AppSection.UNKNOWN

    /**
     * Sections of social media apps. Only FEED and REELS are considered
     * "doom-scrollable". DM, STORIES, PROFILE, SEARCH etc. are safe.
     */
    private enum class AppSection {
        FEED, REELS, DM, STORIES, PROFILE, SEARCH, UNKNOWN
    }

    // ── Instagram section identifiers ──
    // View-IDs or class-names that tell us which section the user is in
    private val instagramDmIdentifiers = listOf(
        "direct_inbox", "direct_thread", "message_list",
        "inbox_tab", "direct_tab", "thread_message",
        "direct_text_input", "message_content"
    )
    private val instagramStoryIdentifiers = listOf(
        "reel_viewer_subtitle", "story_viewer", "stories_tray",
        "story_media", "story_progress", "story_ring"
    )
    private val instagramProfileIdentifiers = listOf(
        "profile_tab", "profile_header", "row_profile_header",
        "action_bar_title", "profile_picture"
    )
    private val instagramSearchIdentifiers = listOf(
        "search_tab", "explore_grid", "action_bar_search",
        "search_edit_text"
    )

    // Only the actual full-screen vertical video player for Reels
    private val instagramReelsIdentifiers = listOf(
        "clips_video_container", "clips_viewer_view_pager",
        "reel_viewer_image_view"
    )
    private val youtubeShortIdentifiers = listOf(
        "reel_player_overlay", "shorts_player", "reel_recycler"
    )

    // View-IDs that are part of the main feed's scroll container
    private val instagramFeedIdentifiers = listOf(
        "main_feed_list", "feed_timeline", "timeline_feed",
        "recycler_view", "coordinator_root_layout"
    )

    companion object {
        private const val TAG = "TejAstraA11y"
        private const val SCROLL_THRESHOLD = 5 // scrolls per 30s = mindless
        private const val SCROLL_WINDOW_MS = 30_000L

        /** Broadcast action sent after credits are consumed so the launcher can refresh. */
        const val ACTION_CREDITS_UPDATED = "com.example.tejastra.CREDITS_UPDATED"

        // Social media packages
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
        const val TWITTER = "com.twitter.android"
        const val TWITTER_X = "com.twitter.android"
        const val FACEBOOK = "com.facebook.katana"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val SNAPCHAT = "com.snapchat.android"
        const val REDDIT = "com.reddit.frontpage"

        var instance: TejAstraAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefsManager = PrefsManager(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "TejAstra Accessibility Service connected")
        startUsageRecording()
    }

    private fun startUsageRecording() {
        if (usageRecordingTask != null) return
        
        usageRecordingTask = object : Runnable {
            override fun run() {
                // Deduct credits for credit-consuming apps in ALL modes.
                // Deep Work already hard-blocks distracting apps, so they won't be in foreground.
                // Break / Free Time still deduct — this is the user's chosen discipline rule.
                currentForegroundPackage?.let { pkg ->
                    if (isCreditConsumingApp(pkg)) {
                        val tracker = ScreenTimeTracker(this@TejAstraAccessibilityService)
                        tracker.recordUsage(pkg)
                        Log.d(TAG, "Deducted credits for $pkg (mode=$currentMode)")
                        // Notify the launcher to refresh credits display
                        sendBroadcast(Intent(ACTION_CREDITS_UPDATED))
                    }
                }
                handler.postDelayed(this, 60000) // Every minute
            }
        }
        handler.post(usageRecordingTask!!)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChange(event)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleScroll(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleContentChange(event)
            }
        }

        // ── Schedule Check (Every 1 minute) ──
        val now = System.currentTimeMillis()
        if (now - lastScheduleCheckTime > 60_000L) {
            lastScheduleCheckTime = now
            checkSchedule()
        }
    }

    private fun handleWindowChange(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Ignore system UI and our own package
        if (packageName == "com.android.systemui" ||
            packageName == "com.example.tejastra" ||
            packageName == "com.android.launcher" ||
            packageName.contains("launcher")
        ) return

        // Update the current section whenever a window changes
        updateCurrentSection(event)

        // Detect new app launch
        if (packageName != currentForegroundPackage) {
            currentForegroundPackage = packageName
            scrollEventCount = 0
            lastScrollResetTime = System.currentTimeMillis()
            currentAppSection = AppSection.UNKNOWN

            // ── Mode-Based Blocking ──
            if (isDistractingApp(packageName) || (packageName == YOUTUBE && isViewingShorts(event))) {
                when (currentMode) {
                    com.example.tejastra.data.TimeMode.DEEP_WORK -> {
                        val reason = if (packageName == YOUTUBE) "YouTube Shorts are blocked in Deep Work." else "You are in Deep Work Mode. Distractions are not allowed."
                        showDeepWorkBlockOverlay(packageName, reason)
                        return
                    }
                    com.example.tejastra.data.TimeMode.WORK -> {
                        val tracker = ScreenTimeTracker(this)
                        val credits = tracker.calculateAttentionCredits()
                        if (credits.remainingCredits <= 0) {
                            showCreditLimitOverlay(packageName)
                            return
                        }
                    }
                    com.example.tejastra.data.TimeMode.BREAK -> {
                        if (breakStartTime == 0L) breakStartTime = System.currentTimeMillis()
                        val elapsed = System.currentTimeMillis() - breakStartTime
                        if (elapsed > 15 * 60 * 1000L) {
                            // Break time over
                        }
                    }
                    com.example.tejastra.data.TimeMode.FREE_TIME -> {
                        // All good
                    }
                }
            }

            // Fallback to legacy block rules if not a distracting app or in normal modes
            val blockedApp = prefsManager.getBlockedAppConfig(packageName)
            if (blockedApp != null && breatheShownForPackage != packageName) {
                if (ScreenTimeTracker.hasPermission(this)) {
                    val usageToday = ScreenTimeTracker(this).getAppUsageToday(packageName)
                    if (usageToday >= blockedApp.dailyLimitMinutes) {
                        showDailyLimitOverlay(packageName, blockedApp.appName)
                        breatheShownForPackage = packageName
                        return
                    }
                }

                // Show breathe overlay for this blocked app
                showBreatheOverlay(packageName, blockedApp.appName, blockedApp.timeLimitMinutes)
                breatheShownForPackage = packageName
            }
        }
    }

    /**
     * Detect which section the user navigated to based on window/activity info.
     */
    private fun updateCurrentSection(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName != INSTAGRAM && packageName != YOUTUBE) return

        val className = event.className?.toString() ?: ""

        // Activity-class-name hints
        val classLower = className.lowercase()
        when {
            classLower.contains("direct") || classLower.contains("message") || classLower.contains("inbox") -> {
                currentAppSection = AppSection.DM
                Log.d(TAG, "Section → DM")
                return
            }
            classLower.contains("story") || classLower.contains("reel_viewer_subtitle") -> {
                currentAppSection = AppSection.STORIES
                Log.d(TAG, "Section → STORIES")
                return
            }
            classLower.contains("profile") -> {
                currentAppSection = AppSection.PROFILE
                Log.d(TAG, "Section → PROFILE")
                return
            }
            classLower.contains("explore") || classLower.contains("search") -> {
                currentAppSection = AppSection.SEARCH
                Log.d(TAG, "Section → SEARCH")
                return
            }
            classLower.contains("clips") || classLower.contains("reels") -> {
                currentAppSection = AppSection.REELS
                Log.d(TAG, "Section → REELS")
                return
            }
        }

        // If it's a main activity / tab host, try to figure out the tab from view tree
        try {
            val rootNode = rootInActiveWindow ?: return
            val section = detectSectionFromViewTree(rootNode, packageName)
            if (section != AppSection.UNKNOWN) {
                currentAppSection = section
                Log.d(TAG, "Section (from tree) → $section")
            }
            rootNode.recycle()
        } catch (_: Exception) { }
    }

    /**
     * Walk the view tree (max 2 levels deep) to figure out the current section.
     */
    private fun detectSectionFromViewTree(node: AccessibilityNodeInfo, packageName: String, depth: Int = 0): AppSection {
        if (depth > 2) return AppSection.UNKNOWN

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        // Check for DM indicators
        if (instagramDmIdentifiers.any { viewId.contains(it) || contentDesc.contains(it) }) {
            return AppSection.DM
        }
        // Check for Story indicators
        if (instagramStoryIdentifiers.any { viewId.contains(it) || contentDesc.contains(it) }) {
            return AppSection.STORIES
        }
        // Check for Profile indicators
        if (instagramProfileIdentifiers.any { viewId.contains(it) || contentDesc.contains(it) }) {
            return AppSection.PROFILE
        }
        // Check for Search/Explore indicators
        if (instagramSearchIdentifiers.any { viewId.contains(it) || contentDesc.contains(it) }) {
            return AppSection.SEARCH
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = detectSectionFromViewTree(child, packageName, depth + 1)
            child.recycle()
            if (result != AppSection.UNKNOWN) return result
        }
        return AppSection.UNKNOWN
    }

    private fun handleScroll(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val blockedApp = prefsManager.getBlockedAppConfig(packageName) ?: return

        if (!blockedApp.blockScrolling) return

        // ── Only block scrolling on the main FEED ──
        // DMs, stories, profiles, search — all safe
        if (currentAppSection == AppSection.DM ||
            currentAppSection == AppSection.STORIES ||
            currentAppSection == AppSection.PROFILE ||
            currentAppSection == AppSection.SEARCH
        ) {
            return
        }

        // Extra safety: check the scrolled view itself to skip non-feed scrolling
        try {
            val source = event.source
            if (source != null) {
                val sourceViewId = source.viewIdResourceName?.lowercase() ?: ""
                source.recycle()
                // If the scroll is from a known non-feed context, skip
                if (sourceViewId.contains("message") || sourceViewId.contains("direct") ||
                    sourceViewId.contains("inbox") || sourceViewId.contains("thread") ||
                    sourceViewId.contains("story") || sourceViewId.contains("comment")
                ) {
                    return
                }
            }
        } catch (_: Exception) { }

        val now = System.currentTimeMillis()
        if (now - lastScrollResetTime > SCROLL_WINDOW_MS) {
            scrollEventCount = 0
            lastScrollResetTime = now
        }

        scrollEventCount++
        Log.d(TAG, "Scroll count: $scrollEventCount in $packageName (section=$currentAppSection)")

        if (scrollEventCount >= SCROLL_THRESHOLD) {
            Log.d(TAG, "Mindless feed scrolling detected in $packageName")
            showScrollBlockOverlay(packageName)
            scrollEventCount = 0
        }
    }

    private fun handleContentChange(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == "com.android.systemui" || packageName == "com.example.tejastra") return

        // ── Attention Credit Check (Periodic) ──
        val now = System.currentTimeMillis()
        if (now - lastReelCheckTime > 5000) {
            val tracker = ScreenTimeTracker(this)
            val credits = tracker.calculateAttentionCredits()
            if (credits.remainingCredits <= 0 && isCreditConsumingApp(packageName)) {
                showCreditLimitOverlay(packageName)
                return
            }
            // Note: we don't update lastReelCheckTime here because we want the Reels check to run at its own pace
        }

        val blockedApp = prefsManager.getBlockedAppConfig(packageName) ?: return

        if (!blockedApp.blockReels) return

        // ── Never block in DMs or Stories ──
        if (currentAppSection == AppSection.DM ||
            currentAppSection == AppSection.STORIES ||
            currentAppSection == AppSection.PROFILE ||
            currentAppSection == AppSection.SEARCH
        ) {
            return
        }

        if (now - lastReelCheckTime < 2000) return // Debounce 2 seconds (increased)
        lastReelCheckTime = now

        // Try to detect Reels/Shorts content — only full-screen video player
        val rootNode = rootInActiveWindow ?: return
        if (isReelsContent(rootNode, packageName)) {
            Log.d(TAG, "Reels/Shorts detected in $packageName — blocking")
            performGlobalAction(GLOBAL_ACTION_BACK)
            showReelsBlockedNotification(packageName)
        }
        rootNode.recycle()
    }

    /**
     * Check if the current view tree contains actual Reels video player views.
     * Only looks for the full-screen video player, not tabs or navigation.
     * Max depth of 3 to avoid scanning deeply into unrelated UI.
     */
    private fun isReelsContent(node: AccessibilityNodeInfo, packageName: String, depth: Int = 0): Boolean {
        if (depth > 3) return false

        val viewId = node.viewIdResourceName ?: ""
        val identifiers = when (packageName) {
            INSTAGRAM -> instagramReelsIdentifiers
            YOUTUBE -> youtubeShortIdentifiers
            else -> emptyList()
        }
        if (identifiers.any { viewId.contains(it, ignoreCase = true) }) return true

        // Recursively check children (limited depth)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (isReelsContent(child, packageName, depth + 1)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    private fun showBreatheOverlay(packageName: String, appName: String, timeLimitMinutes: Int) {
        val intent = Intent(this, com.example.tejastra.ui.overlay.BreatheActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("package_name", packageName)
            putExtra("app_name", appName)
            putExtra("time_limit", timeLimitMinutes)
        }
        startActivity(intent)
    }

    private fun showScrollBlockOverlay(packageName: String) {
        val intent = Intent(this, com.example.tejastra.ui.overlay.BreatheActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("package_name", packageName)
            putExtra("app_name", "your phone")
            putExtra("time_limit", 0) // Just breathe, no timer
            putExtra("is_scroll_block", true)
        }
        startActivity(intent)
    }

    private fun showDailyLimitOverlay(packageName: String, appName: String) {
        val intent = Intent(this, com.example.tejastra.ui.overlay.BreatheActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("package_name", packageName)
            putExtra("app_name", appName)
            putExtra("time_limit", 0)
            putExtra("is_daily_limit", true)
        }
        startActivity(intent)
    }

    private fun showReelsBlockedNotification(packageName: String) {
        Log.d(TAG, "Reels blocked for $packageName")
        val appName = prefsManager.getBlockedAppConfig(packageName)?.appName ?: "this app"
        val intent = Intent(this, com.example.tejastra.ui.overlay.BreatheActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("package_name", packageName)
            putExtra("app_name", appName)
            putExtra("time_limit", 0)
            putExtra("is_reels_block", true)
        }
        startActivity(intent)
    }

    private fun showCreditLimitOverlay(packageName: String) {
        val intent = Intent(this, com.example.tejastra.ui.overlay.BreatheActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("package_name", packageName)
            putExtra("app_name", "this app")
            putExtra("time_limit", 0)
            putExtra("is_credit_limit", true)
        }
        startActivity(intent)
    }

    private fun isCreditConsumingApp(packageName: String): Boolean {
        // Based on user requirements
        return when {
            packageName == INSTAGRAM -> true
            packageName == YOUTUBE -> true
            packageName == "com.whatsapp" -> true
            // Gallery/Docs categories check
            packageName.contains("gallery") || packageName.contains("photos") || 
            packageName.contains("docs") || packageName.contains("reader") -> true
            else -> false
        }
    }

    /**
     * Call this when the breathe overlay is dismissed (user chose to proceed).
     */
    fun onBreatheComplete(packageName: String, timeLimitMinutes: Int) {
        if (timeLimitMinutes <= 0) return

        sessionTimer?.cancel()
        isSessionActive = true

        sessionTimer = object : CountDownTimer(timeLimitMinutes * 60_000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Could broadcast remaining time
            }

            override fun onFinish() {
                isSessionActive = false
                val appName = prefsManager.getBlockedAppConfig(packageName)?.appName ?: "this app"
                val intent = Intent(this@TejAstraAccessibilityService, com.example.tejastra.ui.overlay.BreatheActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("package_name", packageName)
                    putExtra("app_name", appName)
                    putExtra("time_limit", 0)
                    putExtra("is_session_expired", true)
                }
                startActivity(intent)
                
                breatheShownForPackage = null // Allow breathe to show again
            }
        }.start()
    }

    /**
     * Reset breathe tracking when user leaves app normally.
     */
    fun resetBreatheTracking() {
        breatheShownForPackage = null
        sessionTimer?.cancel()
        isSessionActive = false
    }

    private fun checkSchedule() {
        val schedule = prefsManager.getSchedule()
        if (schedule.isEmpty()) return

        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val currentTimeInMins = currentHour * 60 + currentMinute
        val currentDay = now.get(java.util.Calendar.DAY_OF_WEEK)

        val activeBlock = schedule.find { block ->
            val startMins = block.startHour * 60 + block.startMinute
            val endMins = block.endHour * 60 + block.endMinute
            
            currentTimeInMins in startMins until endMins && block.daysOfWeek.contains(currentDay)
        }

        if (activeBlock != null) {
            currentMode = activeBlock.mode
            val targetModeId = when (activeBlock.mode) {
                com.example.tejastra.data.TimeMode.WORK -> "work"
                com.example.tejastra.data.TimeMode.DEEP_WORK -> "work"
                else -> null
            }
            
            if (targetModeId != null && prefsManager.activeFocusModeId != targetModeId) {
                prefsManager.activeFocusModeId = targetModeId
                Log.d(TAG, "Schedule: Auto-switched to mode $targetModeId (Logic Mode: $currentMode)")
            }
        } else {
            currentMode = com.example.tejastra.data.TimeMode.FREE_TIME
        }
    }

    private fun isDistractingApp(packageName: String): Boolean {
        return packageName == INSTAGRAM || 
               packageName == SNAPCHAT || 
               packageName == FACEBOOK || 
               packageName == TIKTOK ||
               packageName == REDDIT
    }

    private fun showDeepWorkBlockOverlay(packageName: String, reason: String) {
        val intent = Intent(this, com.example.tejastra.ui.overlay.BreatheActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("package_name", packageName)
            putExtra("app_name", "distractions")
            putExtra("time_limit", 0)
            putExtra("is_deep_work_block", true)
            putExtra("deep_work_reason", reason)
        }
        startActivity(intent)
    }

    private fun isViewingShorts(event: AccessibilityEvent): Boolean {
        if (event.packageName != YOUTUBE) return false
        val rootNode = rootInActiveWindow ?: return false
        return youtubeShortIdentifiers.any { id ->
            rootNode.findAccessibilityNodeInfosByViewId("$YOUTUBE:id/$id").isNotEmpty()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "TejAstra Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        usageRecordingTask?.let { handler.removeCallbacks(it) }
        instance = null
    }
}
