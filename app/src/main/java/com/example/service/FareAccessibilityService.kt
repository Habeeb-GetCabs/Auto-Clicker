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

        var minFareThreshold: Int = 300
        var isServiceRuleActive: Boolean = true
        var targetKeywordsList: List<String> = listOf("Accept", "Accept Ride", "Confirm", "Accept Order")
        var targetXRatioPercent: Int = 50
        var targetYRatioPercent: Int = 85

        val lastLogMessage = MutableStateFlow("Accessibility Service Initialized")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        Log.d(TAG, "FareAccessibilityService Connected")
        lastLogMessage.value = "Service connected & listening to screen popups"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isServiceRuleActive || event == null) return

        val rootNode = rootInActiveWindow ?: return
        try {
            scanAndProcessNode(rootNode)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun scanAndProcessNode(node: AccessibilityNodeInfo) {
        val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        
        // Extract numbers following currency symbol or standalone numbers if relevant
        if (nodeText.contains("₹") || nodeText.contains("Rs") || nodeText.lowercase().contains("fare")) {
            val digits = nodeText.replace(Regex("[^0-9]"), "")
            val fare = digits.toIntOrNull()
            if (fare != null && fare >= minFareThreshold) {
                lastLogMessage.value = "Detected fare ₹$fare >= threshold ₹$minFareThreshold. Searching accept button..."
                findAndClickAcceptButton(rootInActiveWindow)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanAndProcessNode(child)
            child.recycle()
        }
    }

    private fun findAndClickAcceptButton(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) return

        for (keyword in targetKeywordsList) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        lastLogMessage.value = "Clicked button containing '$keyword'"
                        return
                    } else {
                        var parent = node.parent
                        while (parent != null) {
                            if (parent.isClickable) {
                                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                lastLogMessage.value = "Clicked parent node for '$keyword'"
                                return
                            }
                            parent = parent.parent
                        }
                    }
                }
            }
        }

        // Fallback gesture click if exact text node was not clickable directly
        performFallbackGestureTap()
    }

    private fun performFallbackGestureTap() {
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
                lastLogMessage.value = "Fallback gesture tap dispatched at (${targetXRatioPercent}%, ${targetYRatioPercent}%)"
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
