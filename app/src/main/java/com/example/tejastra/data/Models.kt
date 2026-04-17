package com.example.tejastra.data

/**
 * Represents a blocked app configuration.
 */
data class BlockedApp(
    val packageName: String,
    val appName: String,
    val timeLimitMinutes: Int = 5,        // How many minutes allowed per session
    val blockReels: Boolean = false,       // Block Reels/Shorts content
    val blockScrolling: Boolean = false,   // Block mindless scrolling
    val isEnabled: Boolean = true,         // Whether this rule is active
    val dailyLimitMinutes: Int = 30,       // Total daily usage limit
)

data class FocusMode(
    val id: String,
    val name: String,
    val description: String,
)

data class ModeLocationConfig(
    val modeId: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val label: String = "",
    val isEnabled: Boolean = true,
)

object FocusModes {
    val defaults = listOf(
        FocusMode(
            id = "work",
            name = "Work",
            description = "Protect your attention while working.",
        ),
        FocusMode(
            id = "gym",
            name = "Gym",
            description = "Keep only workout-friendly apps around.",
        ),
        FocusMode(
            id = "college",
            name = "College",
            description = "Cut distractions during lectures and study sessions.",
        ),
    )

    fun defaultMode(): FocusMode = defaults.first()

    fun sanitizeId(name: String): String {
        return name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "mode" }
    }
}

/**
 * Represents a task for today's to-do list shown on the launcher wallpaper.
 */
data class Task(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Represents screen time data for an app.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMinutes: Long,
    val categoryName: String = "Other",
)

/**
 * Represents overall screen time summary.
 */
data class ScreenTimeSummary(
    val totalMinutes: Long,
    val appUsages: List<AppUsageInfo>,
    val unlockCount: Int = 0,
)

/**
 * Notification filter rule for an app.
 * Controls which notifications are allowed through during focus.
 */
data class NotificationFilterRule(
    val packageName: String,
    val appName: String,
    val isAllowed: Boolean = false,          // Allow ALL notifications from this app
    val allowedContacts: List<String> = emptyList(), // Only show notifications from these people
    val urgencyKeywords: List<String> = emptyList(), // Show if message contains these keywords
)
