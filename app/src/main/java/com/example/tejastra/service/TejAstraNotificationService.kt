package com.example.tejastra.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.tejastra.data.PrefsManager

class TejAstraNotificationService : NotificationListenerService() {

    private lateinit var prefsManager: PrefsManager

    companion object {
        private const val TAG = "TejAstraNotifService"
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        Log.d(TAG, "Notification Service Created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName
        
        // Don't filter system apps or our own app
        if (packageName == "android" || packageName == "com.android.systemui" || packageName == "com.example.tejastra") {
            return
        }

        val rule = prefsManager.getNotificationRule(packageName)
        
        // If there's no rule for this app, we can just allow it, or block it based on default policy.
        // Usually, in a strict focus mode, if an app has a rule and isAllowed is false, we filter.
        // If there's no rule, we might leave it alone. But wait, if they set up a rule, they want to filter.
        if (rule == null) {
            // No specific rule for this app, so allow it by default
            return
        }

        if (rule.isAllowed) {
            // Fully allowed
            return
        }

        // It's not fully allowed. Let's check contact names and urgency keywords.
        val notification = sbn.notification
        val extras = notification.extras
        
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullText = "$title $text $bigText".lowercase()
        val titleLower = title.lowercase()

        // 1. Check if it's from an allowed contact
        // Usually, the contact name is the title (e.g., in WhatsApp, Instagram DMs)
        val isFromAllowedContact = rule.allowedContacts.any { contact ->
            titleLower.contains(contact.lowercase())
        }

        if (isFromAllowedContact) {
            Log.d(TAG, "Allowed notification from contact: $title")
            return // Allow
        }

        // 2. Check for urgency keywords
        val containsUrgentKeyword = rule.urgencyKeywords.any { keyword ->
            fullText.contains(keyword.lowercase())
        }

        if (containsUrgentKeyword) {
            Log.d(TAG, "Allowed urgent notification containing keyword. Title: $title")
            return // Allow
        }

        // If it's not from an allowed contact and doesn't have urgent keywords, block it!
        Log.d(TAG, "Blocked notification from $packageName. Title: $title")
        cancelNotification(sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Do nothing
    }
}
