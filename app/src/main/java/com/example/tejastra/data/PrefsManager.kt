package com.example.tejastra.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages persistence of blocked apps, favorite apps, and tasks using SharedPreferences.
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tejastra_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val KEY_MODE_BLOCKED_APPS = "mode_blocked_apps"
        private const val KEY_FOCUS_MODES = "focus_modes"
        private const val KEY_ACTIVE_FOCUS_MODE = "active_focus_mode"
        private const val KEY_MODE_LOCATIONS = "mode_locations"
        private const val KEY_LOCATION_AUTOMATION_ENABLED = "location_automation_enabled"
        private const val KEY_FAVORITE_APPS = "favorite_apps"
        private const val KEY_TASKS = "tasks"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_DUMB_PHONE_MODE = "dumb_phone_mode"
        private const val KEY_DEEP_WORK_END_TIME = "deep_work_end_time"
        private const val KEY_MOTIVATION_TEXT = "motivation_text"
        private const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        private const val KEY_BOTTOM_LEFT_APP = "bottom_left_app"
        private const val KEY_BOTTOM_RIGHT_APP = "bottom_right_app"
        private const val KEY_DRAWER_KEYBOARD_ON_OPEN = "drawer_keyboard_on_open"
        private const val KEY_CLOCK_24_HOUR = "clock_24_hour"
        private const val KEY_MODE_NOTIFICATION_RULES = "mode_notification_rules"
    }

    // ── Blocked Apps ──────────────────────────────────────────────────

    fun getBlockedApps(modeId: String = activeFocusModeId): List<BlockedApp> {
        val modeJson = prefs.getString(KEY_MODE_BLOCKED_APPS, null)
        if (!modeJson.isNullOrBlank()) {
            val root = JSONObject(modeJson)
            if (root.has(modeId)) {
                return parseBlockedApps(root.optJSONArray(modeId)?.toString() ?: "[]")
            }
        }

        val legacyJson = prefs.getString(KEY_BLOCKED_APPS, null)
        if (!legacyJson.isNullOrBlank()) {
            val migratedApps = parseBlockedApps(legacyJson)
            if (migratedApps.isNotEmpty()) {
                saveBlockedApps(migratedApps, modeId)
                prefs.edit().remove(KEY_BLOCKED_APPS).apply()
                return migratedApps
            }
        }

        return emptyList()
    }

    private fun parseBlockedApps(json: String): List<BlockedApp> {
        val arr = JSONArray(json)
        val list = mutableListOf<BlockedApp>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                BlockedApp(
                    packageName = obj.getString("packageName"),
                    appName = obj.getString("appName"),
                    timeLimitMinutes = obj.optInt("timeLimitMinutes", 5),
                    blockReels = obj.optBoolean("blockReels", false),
                    blockScrolling = obj.optBoolean("blockScrolling", false),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 30),
                )
            )
        }
        return list
    }

    fun saveBlockedApps(apps: List<BlockedApp>, modeId: String = activeFocusModeId) {
        val arr = JSONArray()
        apps.forEach { app ->
            val obj = JSONObject().apply {
                put("packageName", app.packageName)
                put("appName", app.appName)
                put("timeLimitMinutes", app.timeLimitMinutes)
                put("blockReels", app.blockReels)
                put("blockScrolling", app.blockScrolling)
                put("isEnabled", app.isEnabled)
                put("dailyLimitMinutes", app.dailyLimitMinutes)
            }
            arr.put(obj)
        }

        val root = JSONObject(prefs.getString(KEY_MODE_BLOCKED_APPS, "{}") ?: "{}")
        root.put(modeId, arr)
        prefs.edit().putString(KEY_MODE_BLOCKED_APPS, root.toString()).apply()
    }

    fun addBlockedApp(app: BlockedApp, modeId: String = activeFocusModeId) {
        val current = getBlockedApps(modeId).toMutableList()
        current.removeAll { it.packageName == app.packageName }
        current.add(app)
        saveBlockedApps(current, modeId)
    }

    fun removeBlockedApp(packageName: String, modeId: String = activeFocusModeId) {
        val current = getBlockedApps(modeId).toMutableList()
        current.removeAll { it.packageName == packageName }
        saveBlockedApps(current, modeId)
    }

    fun isAppBlocked(packageName: String, modeId: String = activeFocusModeId): Boolean {
        return getBlockedApps(modeId).any { it.packageName == packageName && it.isEnabled }
    }

    fun getBlockedAppConfig(packageName: String, modeId: String = activeFocusModeId): BlockedApp? {
        return getBlockedApps(modeId).find { it.packageName == packageName && it.isEnabled }
    }

    // ── Notification Rules ────────────────────────────────────────────

    fun getNotificationFilterRules(modeId: String = activeFocusModeId): List<NotificationFilterRule> {
        val modeJson = prefs.getString(KEY_MODE_NOTIFICATION_RULES, null)
        if (!modeJson.isNullOrBlank()) {
            val root = JSONObject(modeJson)
            if (root.has(modeId)) {
                return parseNotificationRules(root.optJSONArray(modeId)?.toString() ?: "[]")
            }
        }
        return emptyList()
    }

    private fun parseNotificationRules(json: String): List<NotificationFilterRule> {
        val arr = JSONArray(json)
        val list = mutableListOf<NotificationFilterRule>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            
            val contactsArr = obj.optJSONArray("allowedContacts")
            val contacts = mutableListOf<String>()
            if (contactsArr != null) {
                for (j in 0 until contactsArr.length()) {
                    contacts.add(contactsArr.getString(j))
                }
            }
            
            val keywordsArr = obj.optJSONArray("urgencyKeywords")
            val keywords = mutableListOf<String>()
            if (keywordsArr != null) {
                for (j in 0 until keywordsArr.length()) {
                    keywords.add(keywordsArr.getString(j))
                }
            }

            list.add(
                NotificationFilterRule(
                    packageName = obj.getString("packageName"),
                    appName = obj.getString("appName"),
                    isAllowed = obj.optBoolean("isAllowed", false),
                    allowedContacts = contacts,
                    urgencyKeywords = keywords,
                )
            )
        }
        return list
    }

    fun saveNotificationFilterRules(rules: List<NotificationFilterRule>, modeId: String = activeFocusModeId) {
        val arr = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject().apply {
                put("packageName", rule.packageName)
                put("appName", rule.appName)
                put("isAllowed", rule.isAllowed)
                
                val contactsArr = JSONArray()
                rule.allowedContacts.forEach { contactsArr.put(it) }
                put("allowedContacts", contactsArr)
                
                val keywordsArr = JSONArray()
                rule.urgencyKeywords.forEach { keywordsArr.put(it) }
                put("urgencyKeywords", keywordsArr)
            }
            arr.put(obj)
        }

        val root = JSONObject(prefs.getString(KEY_MODE_NOTIFICATION_RULES, "{}") ?: "{}")
        root.put(modeId, arr)
        prefs.edit().putString(KEY_MODE_NOTIFICATION_RULES, root.toString()).apply()
    }

    fun getNotificationRule(packageName: String, modeId: String = activeFocusModeId): NotificationFilterRule? {
        return getNotificationFilterRules(modeId).find { it.packageName == packageName }
    }

    fun isNotificationFeatureEnabled(): Boolean {
        // Feature is enabled if there are any rules in the active mode
        return getNotificationFilterRules(activeFocusModeId).isNotEmpty()
    }

    fun getFocusModes(): List<FocusMode> {
        val json = prefs.getString(KEY_FOCUS_MODES, null)
        if (json.isNullOrBlank()) {
            saveFocusModes(FocusModes.defaults)
            return FocusModes.defaults
        }

        val arr = JSONArray(json)
        val list = mutableListOf<FocusMode>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                FocusMode(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.optString("description"),
                )
            )
        }
        return if (list.isEmpty()) FocusModes.defaults.also(::saveFocusModes) else list
    }

    fun saveFocusModes(modes: List<FocusMode>) {
        val arr = JSONArray()
        modes.forEach { mode ->
            arr.put(
                JSONObject().apply {
                    put("id", mode.id)
                    put("name", mode.name)
                    put("description", mode.description)
                }
            )
        }
        prefs.edit().putString(KEY_FOCUS_MODES, arr.toString()).apply()
    }

    fun addFocusMode(name: String, description: String): FocusMode {
        val current = getFocusModes().toMutableList()
        val baseId = FocusModes.sanitizeId(name)
        var modeId = baseId
        var suffix = 2
        while (current.any { it.id == modeId }) {
            modeId = "${baseId}_$suffix"
            suffix++
        }

        val mode = FocusMode(
            id = modeId,
            name = name.trim(),
            description = description.trim(),
        )
        current.add(mode)
        saveFocusModes(current)
        return mode
    }

    fun removeFocusMode(modeId: String) {
        val current = getFocusModes().toMutableList()
        if (current.size <= 1) return

        current.removeAll { it.id == modeId }
        saveFocusModes(current)

        val locations = getModeLocationConfigs().toMutableMap()
        locations.remove(modeId)
        saveModeLocationConfigs(locations.values.toList())

        if (activeFocusModeId == modeId) {
            activeFocusModeId = current.firstOrNull()?.id ?: FocusModes.defaultMode().id
        }
    }

    fun getModeLocationConfigs(): Map<String, ModeLocationConfig> {
        val json = prefs.getString(KEY_MODE_LOCATIONS, null)
        if (json.isNullOrBlank()) return emptyMap()

        val arr = JSONArray(json)
        val map = mutableMapOf<String, ModeLocationConfig>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val config = ModeLocationConfig(
                modeId = obj.getString("modeId"),
                latitude = obj.getDouble("latitude"),
                longitude = obj.getDouble("longitude"),
                radiusMeters = obj.optDouble("radiusMeters", 150.0).toFloat(),
                label = obj.optString("label"),
                isEnabled = obj.optBoolean("isEnabled", true),
            )
            map[config.modeId] = config
        }
        return map
    }

    fun saveModeLocationConfigs(configs: List<ModeLocationConfig>) {
        val arr = JSONArray()
        configs.forEach { config ->
            arr.put(
                JSONObject().apply {
                    put("modeId", config.modeId)
                    put("latitude", config.latitude)
                    put("longitude", config.longitude)
                    put("radiusMeters", config.radiusMeters.toDouble())
                    put("label", config.label)
                    put("isEnabled", config.isEnabled)
                }
            )
        }
        prefs.edit().putString(KEY_MODE_LOCATIONS, arr.toString()).apply()
    }

    fun upsertModeLocationConfig(config: ModeLocationConfig) {
        val current = getModeLocationConfigs().toMutableMap()
        current[config.modeId] = config
        saveModeLocationConfigs(current.values.toList())
    }

    fun removeModeLocationConfig(modeId: String) {
        val current = getModeLocationConfigs().toMutableMap()
        current.remove(modeId)
        saveModeLocationConfigs(current.values.toList())
    }

    // ── Favorite Apps (Launcher Home) ─────────────────────────────────

    fun getFavoriteApps(): List<String> {
        val json = prefs.getString(KEY_FAVORITE_APPS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }

    fun saveFavoriteApps(packageNames: List<String>) {
        val arr = JSONArray()
        packageNames.take(4).forEach { arr.put(it) }
        prefs.edit().putString(KEY_FAVORITE_APPS, arr.toString()).apply()
    }

    // ── Tasks ─────────────────────────────────────────────────────────

    fun getTasks(): List<Task> {
        val json = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<Task>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                Task(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                )
            )
        }
        return list
    }

    fun saveTasks(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("isCompleted", task.isCompleted)
                put("createdAt", task.createdAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    fun addTask(task: Task) {
        val current = getTasks().toMutableList()
        current.add(task)
        saveTasks(current)
    }

    fun toggleTask(id: String) {
        val current = getTasks().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(isCompleted = !current[idx].isCompleted)
            saveTasks(current)
        }
    }

    fun removeTask(id: String) {
        val current = getTasks().toMutableList()
        current.removeAll { it.id == id }
        saveTasks(current)
    }

    // ── Onboarding ────────────────────────────────────────────────────

    var isOnboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var activeFocusModeId: String
        get() {
            val modes = getFocusModes()
            val saved = prefs.getString(KEY_ACTIVE_FOCUS_MODE, null)
            val fallback = modes.firstOrNull()?.id ?: FocusModes.defaultMode().id
            return if (!saved.isNullOrBlank() && modes.any { it.id == saved }) {
                saved
            } else {
                prefs.edit().putString(KEY_ACTIVE_FOCUS_MODE, fallback).apply()
                fallback
            }
        }
        set(value) = prefs.edit().putString(KEY_ACTIVE_FOCUS_MODE, value).apply()

    var isLocationAutomationEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCATION_AUTOMATION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCATION_AUTOMATION_ENABLED, value).apply()

    // ── Hardcore / Deep Work ──────────────────────────────────────────

    var isDumbPhoneMode: Boolean
        get() = prefs.getBoolean(KEY_DUMB_PHONE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DUMB_PHONE_MODE, value).apply()

    var isDoubleTapLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_TAP_LOCK, value).apply()

    var shouldShowKeyboardOnDrawerOpen: Boolean
        get() = prefs.getBoolean(KEY_DRAWER_KEYBOARD_ON_OPEN, true)
        set(value) = prefs.edit().putBoolean(KEY_DRAWER_KEYBOARD_ON_OPEN, value).apply()

    var use24HourClock: Boolean
        get() = prefs.getBoolean(KEY_CLOCK_24_HOUR, true)
        set(value) = prefs.edit().putBoolean(KEY_CLOCK_24_HOUR, value).apply()

    var deepWorkEndTime: Long
        get() = prefs.getLong(KEY_DEEP_WORK_END_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_DEEP_WORK_END_TIME, value).apply()

    fun getMotivationTexts(): List<String> {
        val json = prefs.getString(KEY_MOTIVATION_TEXT, "[\"Dont take your mind where the life hasn't gone yet\"]") ?: "[]"
        if (json.startsWith("[")) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // If it starts with [ but is not a JSON array, treat as regular string below
            }
        }
        
        // Return as a single item list if it's not a JSON array or empty
        return if (json.isNotBlank()) listOf(json) else emptyList()
    }

    fun saveMotivationTexts(texts: List<String>) {
        val arr = JSONArray()
        texts.forEach { arr.put(it) }
        prefs.edit().putString(KEY_MOTIVATION_TEXT, arr.toString()).apply()
    }

    var motivationText: String
        get() {
            val texts = getMotivationTexts()
            if (texts.isEmpty()) return ""
            // Swap by 1 quote every day
            val dayIndex = (System.currentTimeMillis() / (1000 * 60 * 60 * 24)).toInt()
            return texts[dayIndex % texts.size]
        }
        set(value) {
            val texts = getMotivationTexts().toMutableList()
            if (texts.isEmpty()) {
                texts.add(value)
            } else {
                texts[0] = value
            }
            saveMotivationTexts(texts)
        }

    // ── Shortcuts ─────────────────────────────────────────────────────

    var bottomLeftApp: String
        get() = prefs.getString(KEY_BOTTOM_LEFT_APP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BOTTOM_LEFT_APP, value).apply()

    var bottomRightApp: String
        get() = prefs.getString(KEY_BOTTOM_RIGHT_APP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BOTTOM_RIGHT_APP, value).apply()
}
