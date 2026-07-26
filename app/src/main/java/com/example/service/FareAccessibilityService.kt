package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
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

        var minFareThreshold: Int = 100
        var exactOnlyMatch: Boolean = false
        var isServiceRuleActive: Boolean = true
        var targetAppName: String = "All Apps"
        var targetKeywordsList: List<String> = listOf("Accept", "Accept Ride", "Confirm", "Accept Order", "Accept Trip")
        
        // Fallback gesture option
        var enableFallbackGesture: Boolean = true
        var targetXRatioPercent: Int = 50
        var targetYRatioPercent: Int = 85

        private var lastActionTimestamp: Long = 0L
        var actionCooldownMs: Long = 2000L // 2-second safety cooldown

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

        // Ignore soft keyboards & system menus
        if (pkgName.contains("inputmethod") || 
            pkgName.contains("keyboard") || 
            pkgName.contains("systemui") || 
            pkgName.contains("launcher") || 
            pkgName.contains("settings")) {
            return
        }

        // Target App Filter
        when (targetAppName) {
            "Rapido" -> if (!pkgName.contains("rapido")) return
            "Ola" -> if (!pkgName.contains("ola")) return
            "Uber" -> if (!pkgName.contains("uber")) return
        }

        // Rate limit cooldown
        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < actionCooldownMs) {
            return
        }

        val rootNode = rootInActiveWindow ?: event.source ?: return
        try {
            scanAndProcessWindow(rootNode, pkgName)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    private fun scanAndProcessWindow(rootNode: AccessibilityNodeInfo, pkgName: String) {
        val extractedFare = extractFareFromTree(rootNode)
        
        if (extractedFare != null) {
            val matches = if (exactOnlyMatch) extractedFare == minFareThreshold else extractedFare >= minFareThreshold
            if (matches) {
                lastLogMessage.value = "Match found: ₹$extractedFare >= target ₹$minFareThreshold on $pkgName. Triggering accept..."
                val clicked = findAndClickAcceptButton(rootNode, pkgName)
                if (clicked) {
                    lastActionTimestamp = System.currentTimeMillis()
                }
            } else {
                lastLogMessage.value = "Detected fare ₹$extractedFare (< target ₹$minFareThreshold). Skipping."
            }
        }
    }

    /**
     * Intelligently parses currency values like "₹164 + ₹20", "₹164", "Rs 200" from node tree
     */
    private fun extractFareFromTree(node: AccessibilityNodeInfo): Int? {
        val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        
        // Find all currency matches in this node's text
        val currencyRegex = Regex("(?:₹|Rs\\.?|INR)\\s*([0-9]{2,5})", RegexOption.IGNORE_CASE)
        val matches = currencyRegex.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()

        if (matches.isNotEmpty()) {
            // Sum up values if multiple fares appear in same block (e.g. ₹164 + ₹20 = ₹184)
            return matches.sum()
        }

        // Search children recursively
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childFare = extractFareFromTree(child)
            if (childFare != null) {
                return childFare
            }
        }
        return null
    }

    private fun findAndClickAcceptButton(rootNode: AccessibilityNodeInfo, pkgName: String): Boolean {
        // 1. Full tree search for nodes matching target keywords (e.g., "Accept")
        val targetNode = findNodeByKeywords(rootNode, targetKeywordsList)

        if (targetNode != null) {
            // Try direct click
            if (targetNode.isClickable) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                lastLogMessage.value = "AUTO-ACCEPTED ride on $pkgName via direct click!"
                return true
            }

            // Try parent click
            var parent = targetNode.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    lastLogMessage.value = "AUTO-ACCEPTED ride on $pkgName via parent container click!"
                    return true
                }
                parent = parent.parent
            }

            // Perform precise coordinate tap on the center of the 'Accept' button bounds
            val bounds = Rect()
            targetNode.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                tapAtCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat(), "Target Button Center Bounds")
                lastLogMessage.value = "AUTO-ACCEPTED ride on $pkgName via precise button tap at (${bounds.centerX()}, ${bounds.centerY()})"
                return true
            }
        }

        // 2. Fallback gesture tap if enabled
        if (enableFallbackGesture) {
            val displayMetrics = resources.displayMetrics
            val x = displayMetrics.widthPixels * (targetXRatioPercent / 100f)
            val y = displayMetrics.heightPixels * (targetYRatioPercent / 100f)
            tapAtCoordinates(x, y, "Fallback Ratio ($targetXRatioPercent%, $targetYRatioPercent%)")
            lastLogMessage.value = "Dispatched fallback auto-tap gesture at $targetXRatioPercent%, $targetYRatioPercent%"
            return true
        }

        lastLogMessage.value = "Fare matched ₹$minFareThreshold, but 'Accept' button was not clickable on screen."
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

    private fun tapAtCoordinates(x: Float, y: Float, label: String) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture completed successfully: $label")
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
