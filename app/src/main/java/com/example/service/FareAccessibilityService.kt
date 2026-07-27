package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.LicenseManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FareAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FareAccessibility"
        private const val PREFS_NAME = "fare_filter_prefs"
        private const val NOTIFICATION_CHANNEL_ID = "fare_filter_bg_channel"
        private const val NOTIFICATION_ID = 8810

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        var minFareThreshold: Int = 100
        var exactOnlyMatch: Boolean = false
        var isServiceRuleActive: Boolean = true
        var targetAppName: String = "All Apps"
        var targetKeywordsList: List<String> = listOf("Accept", "Accept Ride", "Confirm", "Accept Order", "Accept Trip")
        
        var enableFallbackGesture: Boolean = true
        var targetXRatioPercent: Int = 50
        var targetYRatioPercent: Int = 85

        private var lastActionTimestamp: Long = 0L
        var actionCooldownMs: Long = 2000L

        val lastLogMessage = MutableStateFlow("Accessibility Service Initialized (Standby)")

        fun updatePrefs(
            context: Context,
            minFare: Int,
            exactOnly: Boolean,
            serviceActive: Boolean,
            targetApp: String,
            keywords: String,
            cooldownSeconds: Long,
            fallbackGesture: Boolean,
            xRatio: Int,
            yRatio: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("min_fare", minFare)
                .putBoolean("exact_only", exactOnly)
                .putBoolean("service_active", serviceActive)
                .putString("target_app", targetApp)
                .putString("keywords", keywords)
                .putLong("cooldown_seconds", cooldownSeconds)
                .putBoolean("fallback_gesture", fallbackGesture)
                .putInt("x_ratio", xRatio)
                .putInt("y_ratio", yRatio)
                .apply()

            // Update live static values
            minFareThreshold = minFare
            exactOnlyMatch = exactOnly
            isServiceRuleActive = serviceActive
            targetAppName = targetApp
            targetKeywordsList = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            actionCooldownMs = (cooldownSeconds * 1000L).coerceAtLeast(1000L)
            enableFallbackGesture = fallbackGesture
            targetXRatioPercent = xRatio
            targetYRatioPercent = yRatio
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        loadPrefs()
        createNotificationChannel()
        startForegroundNotification()
        Log.d(TAG, "FareAccessibilityService Connected & Loaded Prefs")
        lastLogMessage.value = "Service active in background & watching ride apps"
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        minFareThreshold = prefs.getInt("min_fare", 100)
        exactOnlyMatch = prefs.getBoolean("exact_only", false)
        isServiceRuleActive = prefs.getBoolean("service_active", true)
        targetAppName = prefs.getString("target_app", "All Apps") ?: "All Apps"
        val kwStr = prefs.getString("keywords", "Accept, Accept Ride, Confirm, Accept Order, Accept Trip") ?: "Accept, Accept Ride, Confirm"
        targetKeywordsList = kwStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        actionCooldownMs = (prefs.getLong("cooldown_seconds", 2L) * 1000L).coerceAtLeast(1000L)
        enableFallbackGesture = prefs.getBoolean("fallback_gesture", true)
        targetXRatioPercent = prefs.getInt("x_ratio", 50)
        targetYRatioPercent = prefs.getInt("y_ratio", 85)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Fare Filter Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Fare Filter active to auto-accept rides in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        try {
            val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Fare Filter Assistant Active")
                .setContentText("Monitoring ride offers (≥ ₹$minFareThreshold) in background")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground notification", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        loadPrefs()
        if (!LicenseManager.isAccessGranted(this)) {
            lastLogMessage.value = "SUBSCRIPTION EXPIRED / DISABLED. Please contact @DriveTechSoft on Telegram to renew."
            return
        }
        if (!isServiceRuleActive || event == null) return

        // Retrieve package name safely from event or root window
        val pkgName = (event.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString() ?: "").lowercase()

        // Ignore soft keyboards
        if (pkgName.contains("inputmethod") || 
            pkgName.contains("keyboard") || 
            pkgName.contains("gboard") || 
            pkgName.contains("latin")) {
            return
        }

        // Do not auto-click on our own application settings UI
        val selfPkg = packageName.lowercase()
        if (pkgName == selfPkg || pkgName.contains("com.example") || pkgName.contains("com.aistudio")) {
            return
        }

        // Filter target app if specified
        when (targetAppName) {
            "Rapido" -> if (!pkgName.contains("rapido")) return
            "Ola" -> if (!pkgName.contains("ola")) return
            "Uber" -> if (!pkgName.contains("uber")) return
        }

        // Rate limiting cooldown check
        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < actionCooldownMs) {
            return
        }

        // Collect root nodes from active window or interactive windows
        val nodesToScan = mutableListOf<AccessibilityNodeInfo>()
        
        rootInActiveWindow?.let { nodesToScan.add(it) }
        event.source?.let { nodesToScan.add(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    window.root?.let { nodesToScan.add(it) }
                }
            } catch (e: Exception) {
                // Ignore window query error
            }
        }

        var handled = false
        for (rootNode in nodesToScan) {
            if (handled) break
            try {
                handled = scanAndProcessNode(rootNode, pkgName.ifEmpty { "Ride App" })
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning node", e)
            }
        }
    }

    private fun scanAndProcessNode(rootNode: AccessibilityNodeInfo, appLabel: String): Boolean {
        val extractedFare = extractFareFromTree(rootNode)
        
        if (extractedFare != null) {
            val matches = if (exactOnlyMatch) extractedFare == minFareThreshold else extractedFare >= minFareThreshold
            if (matches) {
                lastLogMessage.value = "Ride fare ₹$extractedFare detected on $appLabel! Searching accept button..."
                val clicked = findAndClickAcceptButton(rootNode, appLabel)
                if (clicked) {
                    lastActionTimestamp = System.currentTimeMillis()
                    return true
                }
            } else {
                lastLogMessage.value = "Fare ₹$extractedFare on $appLabel is below target ₹$minFareThreshold. Skipped."
            }
        }
        return false
    }

    /**
     * Recursively parses fare amounts like "₹164", "₹164 + ₹20", "Rs 200" from accessibility nodes
     */
    private fun extractFareFromTree(node: AccessibilityNodeInfo): Int? {
        val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        
        val currencyRegex = Regex("(?:₹|Rs\\.?|INR)\\s*([0-9]{2,5})", RegexOption.IGNORE_CASE)
        val matches = currencyRegex.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()

        if (matches.isNotEmpty()) {
            return matches.sum()
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childFare = extractFareFromTree(child)
            if (childFare != null) {
                return childFare
            }
        }
        return null
    }

    private fun findAndClickAcceptButton(rootNode: AccessibilityNodeInfo, appLabel: String): Boolean {
        // 1. Search for nodes matching target keywords (e.g. "Accept")
        val targetNode = findNodeByKeywords(rootNode, targetKeywordsList)

        if (targetNode != null) {
            // Direct click
            if (targetNode.isClickable) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                lastLogMessage.value = "AUTO-ACCEPTED ride (₹ matches) on $appLabel via direct click!"
                return true
            }

            // Parent clickable container
            var parent = targetNode.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    lastLogMessage.value = "AUTO-ACCEPTED ride on $appLabel via container click!"
                    return true
                }
                parent = parent.parent
            }

            // Precise bounds center tap gesture
            val bounds = Rect()
            targetNode.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                tapAtCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                lastLogMessage.value = "AUTO-ACCEPTED ride on $appLabel via precise gesture tap at (${bounds.centerX()}, ${bounds.centerY()})"
                return true
            }
        }

        // 2. Fallback gesture tap at configured screen coordinates ratio
        if (enableFallbackGesture) {
            val displayMetrics = resources.displayMetrics
            val x = displayMetrics.widthPixels * (targetXRatioPercent / 100f)
            val y = displayMetrics.heightPixels * (targetYRatioPercent / 100f)
            tapAtCoordinates(x, y)
            lastLogMessage.value = "AUTO-ACCEPTED ride on $appLabel via fallback gesture tap at ${targetXRatioPercent}%, ${targetYRatioPercent}%"
            return true
        }

        lastLogMessage.value = "Fare match found on $appLabel, but no clickable accept button was detected."
        return false
    }

    private fun findNodeByKeywords(node: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        val lowerText = text.lowercase().trim()

        for (keyword in keywords) {
            val kLower = keyword.lowercase().trim()
            if (lowerText.contains(kLower) || lowerText == kLower) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByKeywords(child, keywords)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun tapAtCoordinates(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture completed at ($x, $y)")
            }
        }, null)
    }

    override fun onInterrupt() {
        _isRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
    }
}
