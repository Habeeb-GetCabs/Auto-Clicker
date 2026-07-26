package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FareAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FareAccessibility"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        var minFareThreshold: Int = 300
        var exactOnlyMatch: Boolean = false
        var isServiceRuleActive: Boolean = true
        var targetAppName: String = "All Apps"
        var targetKeywordsList: List<String> = listOf("Accept", "Accept Ride", "Confirm", "Accept Order")
        
        // SAFE BY DEFAULT: Fallback coordinate gestures disabled unless explicitly enabled by user
        var enableFallbackGesture: Boolean = false
        var targetXRatioPercent: Int = 50
        var targetYRatioPercent: Int = 85

        private var lastActionTimestamp: Long = 0L
        var actionCooldownMs: Long = 4000L // Default 4-second safety cooldown between auto-clicks

        val lastLogMessage = MutableStateFlow("Accessibility Service Initialized (Standby)")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        Log.d(TAG, "FareAccessibilityService Connected")
        lastLogMessage.value = "Service connected & watching ride apps"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isServiceRuleActive || event == null) return

        val pkgName = event.packageName?.toString()?.lowercase() ?: return

        // 1. SAFETY RULE: Never process events from our own app or typing in our app
        val selfPackage = packageName.lowercase()
        if (pkgName == selfPackage || pkgName.contains("com.example") || pkgName.contains("com.aistudio")) {
            return
        }

        // 2. SAFETY RULE: Ignore system keyboards, system UI, launchers, or settings
        if (pkgName.contains("inputmethod") || 
            pkgName.contains("keyboard") || 
            pkgName.contains("systemui") || 
            pkgName.contains("launcher") || 
            pkgName.contains("settings")) {
            return
        }

        // 3. TARGET APP FILTERING: If user chose a specific app (e.g., Rapido / Ola / Uber), ignore others
        when (targetAppName) {
            "Rapido" -> if (!pkgName.contains("rapido")) return
            "Ola" -> if (!pkgName.contains("ola")) return
            "Uber" -> if (!pkgName.contains("uber")) return
        }

        // 4. RATE LIMIT COOLDOWN: Safety delay between auto-clicks to prevent endless loops
        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < actionCooldownMs) {
            return
        }

        val rootNode = rootInActiveWindow ?: return
        try {
            scanAndProcessNode(rootNode, pkgName)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun scanAndProcessNode(node: AccessibilityNodeInfo, pkgName: String) {
        val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""

        // Extract numbers following currency symbol or standalone numbers if relevant
        if (nodeText.contains("₹") || nodeText.contains("Rs") || nodeText.lowercase().contains("fare")) {
            val digits = nodeText.replace(Regex("[^0-9]"), "")
            val fare = digits.toIntOrNull()
            if (fare != null) {
                val matches = if (exactOnlyMatch) fare == minFareThreshold else fare >= minFareThreshold
                if (matches) {
                    lastLogMessage.value = "Detected fare ₹$fare on $pkgName. Searching accept button..."
                    val clicked = findAndClickAcceptButton(rootInActiveWindow, pkgName)
                    if (clicked) {
                        lastActionTimestamp = System.currentTimeMillis()
                        return
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanAndProcessNode(child, pkgName)
            child.recycle()
        }
    }

    private fun findAndClickAcceptButton(rootNode: AccessibilityNodeInfo?, pkgName: String): Boolean {
        if (rootNode == null) return false

        for (keyword in targetKeywordsList) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        lastLogMessage.value = "AUTO-ACCEPTED ride (₹ matches) on $pkgName via button '$keyword'"
                        return true
                    } else {
                        var parent = node.parent
                        while (parent != null) {
                            if (parent.isClickable) {
                                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                lastLogMessage.value = "AUTO-ACCEPTED ride on $pkgName via parent container for '$keyword'"
                                return true
                            }
                            parent = parent.parent
                        }
                    }
                }
            }
        }

        // Fallback gesture click ONLY if explicitly enabled in settings by the user
        if (enableFallbackGesture) {
            performFallbackGestureTap(pkgName)
            return true
        } else {
            lastLogMessage.value = "Detected fare match on $pkgName but no clickable '$targetKeywordsList' found."
            return false
        }
    }

    private fun performFallbackGestureTap(pkgName: String) {
        val displayMetrics = resources.displayMetrics
        val x = (displayMetrics.widthPixels * (targetXRatioPercent / 100f))
        val y = (displayMetrics.heightPixels * (targetYRatioPercent / 100f))

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                lastLogMessage.value = "Fallback coordinate gesture tap dispatched on $pkgName at (${targetXRatioPercent}%, ${targetYRatioPercent}%)"
            }
        }, null)
    }

    override fun onInterrupt() {
        _isRunning.value = false
        Log.d(TAG, "FareAccessibilityService Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
    }
}

