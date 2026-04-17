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

        val now = System.currentTimeMillis()
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

    override fun onInterrupt() {
        Log.d(TAG, "TejAstra Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        sessionTimer?.cancel()
    }
}
