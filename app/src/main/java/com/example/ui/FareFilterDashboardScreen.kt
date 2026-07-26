package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.model.FareRule
import com.example.model.TripNotificationLog
import com.example.service.FareAccessibilityService

fun checkOverlayPermissionEnabled(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, FareAccessibilityService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)

    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FareFilterDashboardScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityServiceEnabled(context)) }
    var isOverlayEnabled by remember { mutableStateOf(checkOverlayPermissionEnabled(context)) }

    // Re-check permissions whenever app resumes from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityServiceEnabled(context)
                isOverlayEnabled = checkOverlayPermissionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var minFareInput by remember { mutableStateOf("300") }
    var exactOnly by remember { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf("All Apps") }
    
    // Auto-click target settings
    var targetKeywords by remember { mutableStateOf("Accept, Accept Ride, Confirm, Accept Order") }
    var cooldownSecondsInput by remember { mutableStateOf("4") } // Minimum interval between auto-clicks
    var enableFallbackGesture by remember { mutableStateOf(false) } // Safe default false
    var targetXRatio by remember { mutableStateOf("50") } // % of screen width
    var targetYRatio by remember { mutableStateOf("85") } // % of screen height

    // Keep FareAccessibilityService configuration parameters strictly in sync
    LaunchedEffect(
        minFareInput,
        exactOnly,
        serviceEnabled,
        selectedApp,
        targetKeywords,
        cooldownSecondsInput,
        enableFallbackGesture,
        targetXRatio,
        targetYRatio,
        isAccessibilityEnabled,
        isOverlayEnabled
    ) {
        val parsedFare = minFareInput.toIntOrNull() ?: 300
        FareAccessibilityService.minFareThreshold = parsedFare
        FareAccessibilityService.exactOnlyMatch = exactOnly
        FareAccessibilityService.isServiceRuleActive = serviceEnabled && isAccessibilityEnabled && isOverlayEnabled
        FareAccessibilityService.targetAppName = selectedApp
        FareAccessibilityService.targetKeywordsList = targetKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        FareAccessibilityService.actionCooldownMs = ((cooldownSecondsInput.toLongOrNull() ?: 4L) * 1000L).coerceAtLeast(1000L)
        FareAccessibilityService.enableFallbackGesture = enableFallbackGesture
        FareAccessibilityService.targetXRatioPercent = targetXRatio.toIntOrNull() ?: 50
        FareAccessibilityService.targetYRatioPercent = targetYRatio.toIntOrNull() ?: 85
    }

    var simFareInput by remember { mutableStateOf("300") }
    var simAppName by remember { mutableStateOf("Rapido") }
    var simResult by remember { mutableStateOf<String?>(null) }

    val logs = remember {
        mutableStateListOf(
            TripNotificationLog("1", "Rapido", 320, true, "Clicked node 'Accept Ride' at bounds (X: 540, Y: 1820)", "10:42 AM"),
            TripNotificationLog("2", "Ola", 250, false, "Ignored (Below threshold ₹300)", "10:38 AM"),
            TripNotificationLog("3", "Uber", 300, true, "Clicked node 'ACCEPT' at bounds (X: 540, Y: 1750)", "10:15 AM")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Fare Filter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Fare Filter Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val allPermissionsReady = isAccessibilityEnabled && isOverlayEnabled
                // Service status banner & toggle
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (serviceEnabled && allPermissionsReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (serviceEnabled && allPermissionsReady) Color(0xFF10B981) else Color.Red)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (!allPermissionsReady) "Permissions Required" else if (serviceEnabled) "Filter Active" else "Filter Paused",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (!isAccessibilityEnabled && !isOverlayEnabled) "Enable Accessibility & Overlay permissions below to begin"
                                       else if (!isAccessibilityEnabled) "Requires Accessibility Service permission to read screen & auto-accept"
                                       else if (!isOverlayEnabled) "Requires Display Over Other Apps permission to operate over ride popups"
                                       else if (serviceEnabled) "Monitoring ride alerts against target price"
                                       else "Tap toggle to resume fare calculation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = serviceEnabled,
                            onCheckedChange = { serviceEnabled = it },
                            modifier = Modifier.testTag("service_switch")
                        )
                    }
                }
            }

            item {
                // Accessibility Permission Request & Activation Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("accessibility_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAccessibilityEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isAccessibilityEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAccessibilityEnabled) "Accessibility Service: ENABLED" else "Accessibility Permission Required",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isAccessibilityEnabled) {
                            Text(
                                text = "Fare Filter Assistant is active and listening for trip popups to auto-accept matches.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Accessibility Settings")
                            }
                        } else {
                            Text(
                                text = "To automatically detect fare amounts and tap 'Accept Ride' when ride requests appear, you MUST turn on Accessibility Service in Android Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("enable_accessibility_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.SettingsAccessibility, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ENABLE ACCESSIBILITY SERVICE", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("App Info (If Restricted Setting)")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "How to enable on your device:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "1. Tap 'ENABLE ACCESSIBILITY SERVICE' above.\n" +
                                               "2. Tap 'Downloaded apps' or 'Accessibility Services'.\n" +
                                               "3. Find 'Fare Filter Assistant' and toggle it ON.\n" +
                                               "4. If grayed out ('Restricted setting'): Tap 'App Info' button above → tap 3-dots (⋮) in top right → select 'Allow restricted settings'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Display Over Other Apps (Overlay) Permission Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("overlay_permission_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverlayEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOverlayEnabled) Icons.Default.CheckCircle else Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isOverlayEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isOverlayEnabled) "Display Over Other Apps: ENABLED" else "Display Over Other Apps Permission",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isOverlayEnabled) {
                            Text(
                                text = "Overlay permission granted. The app can run auto-touch and status indicators over ride-sharing popups.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Manage Overlay Settings")
                            }
                        } else {
                            Text(
                                text = "Required to allow auto-accept actions and overlay floating status badges when ride popups appear over other apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("enable_overlay_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ALLOW DISPLAY OVER OTHER APPS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                // Main Configuration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Fare Matching Rules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Configure price threshold and target app preference.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = minFareInput,
                            onValueChange = { minFareInput = it },
                            label = { Text("Minimum Fare Target (₹)") },
                            leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fare_input"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { exactOnly = !exactOnly }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = exactOnly,
                                onCheckedChange = { exactOnly = it },
                                modifier = Modifier.testTag("exact_match_checkbox")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Strict Exact Match Only",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (exactOnly) "Matches ONLY trips at exactly ₹${minFareInput.ifEmpty { "300" }}" else "Matches trips ₹${minFareInput.ifEmpty { "300" }} and above",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Target Application",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("All Apps", "Rapido", "Ola", "Uber").forEach { app ->
                                FilterChip(
                                    selected = selectedApp == app,
                                    onClick = { selectedApp = app },
                                    label = { Text(app) },
                                    modifier = Modifier.testTag("chip_$app")
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Auto-Touch Target & Click Strategy Configuration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto-Touch Target Strategy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "How the app locates and clicks the 'Accept Ride' button on popups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Keyword Text Node Matching Section
                        Text(
                            text = "1. Target Button Keyword (Recommended)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = targetKeywords,
                            onValueChange = { targetKeywords = it },
                            label = { Text("Button Text Keywords (comma separated)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("keywords_input"),
                            singleLine = true
                        )
                        Text(
                            text = "Searches screen UI nodes for words like 'Accept' or 'Accept Ride' and triggers click directly on the element.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = cooldownSecondsInput,
                            onValueChange = { cooldownSecondsInput = it.filter { char -> char.isDigit() } },
                            label = { Text("Auto-Click Rate Limit Cooldown (Seconds)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cooldown_input"),
                            singleLine = true
                        )
                        Text(
                            text = "Prevents repeated clicks by waiting at least ${cooldownSecondsInput.ifEmpty { "4" }} second(s) between auto-accept actions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        Divider()

                        Spacer(modifier = Modifier.height(12.dp))

                        // Touch Gesture Fallback Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "2. Coordinate Touch Gesture (Fallback)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (enableFallbackGesture) "ENABLED (May tap fixed screen position)" else "DISABLED (Recommended for Screen Safety)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enableFallbackGesture) MaterialTheme.colorScheme.tertiary else Color(0xFF10B981)
                                )
                            }
                            Switch(
                                checked = enableFallbackGesture,
                                onCheckedChange = { enableFallbackGesture = it },
                                modifier = Modifier.testTag("fallback_gesture_switch")
                            )
                        }

                        if (enableFallbackGesture) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = targetXRatio,
                                    onValueChange = { targetXRatio = it },
                                    label = { Text("Screen X %") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = targetYRatio,
                                    onValueChange = { targetYRatio = it },
                                    label = { Text("Screen Y %") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Text(
                                text = "Dispatches screen gesture tap at (${targetXRatio}% width, ${targetYRatio}% height) if button text is not found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            Text(
                                text = "Smart Text Node Match is active. Coordinate taps are disabled to prevent accidental screen presses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                // Interactive Test Simulator Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Trip Popup Simulator",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = "Test your rule with a simulated ride offer popup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = simFareInput,
                                onValueChange = { simFareInput = it },
                                label = { Text("Trip Fare (₹)") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sim_fare_input"),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    val currentTarget = minFareInput.toIntOrNull() ?: 300
                                    val simFare = simFareInput.toIntOrNull() ?: 0
                                    val isMatch = if (exactOnly) simFare == currentTarget else simFare >= currentTarget
                                    val firstKeyword = targetKeywords.split(",").firstOrNull()?.trim() ?: "Accept Ride"
                                    
                                    if (isMatch && serviceEnabled) {
                                        simResult = "MATCH! Clicked '$firstKeyword' button on $simAppName trip ₹$simFare"
                                        logs.add(0, TripNotificationLog(
                                            id = System.currentTimeMillis().toString(),
                                            appName = simAppName,
                                            fareAmount = simFare,
                                            matched = true,
                                            actionTaken = "Auto-clicked node '$firstKeyword' at bounds (X:${targetXRatio}%, Y:${targetYRatio}%)",
                                            timestamp = "Just now"
                                        ))
                                    } else {
                                        simResult = "IGNORED: Fare ₹$simFare does not meet target rule (Min ₹$currentTarget)"
                                        logs.add(0, TripNotificationLog(
                                            id = System.currentTimeMillis().toString(),
                                            appName = simAppName,
                                            fareAmount = simFare,
                                            matched = false,
                                            actionTaken = "Ignored (Below condition)",
                                            timestamp = "Just now"
                                        ))
                                    }
                                },
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("test_simulate_button")
                            ) {
                                Text("Simulate")
                            }
                        }

                        simResult?.let { result ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (result.startsWith("MATCH")) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (result.startsWith("MATCH")) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (result.startsWith("MATCH")) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = result,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (result.startsWith("MATCH")) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Device Setup & Enable Guide Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SettingsAccessibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How to Enable on Real Device",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "To make background auto-clicking work on your physical Android phone:\n\n" +
                                   "1. Open Phone Settings → Accessibility → Installed Apps / Services.\n" +
                                   "2. Locate 'Fare Filter Assistant' and toggle service ON.\n" +
                                   "3. Grant 'Display over other apps' (Overlay Permission) if prompted.\n" +
                                   "4. When a trip popup arrives, the Accessibility Service detects the fare text and automatically performs a tap on the Accept node.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Activity Log History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(logs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("log_card_${log.id}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (log.matched) Color(0xFF10B981) else Color(0xFFEF4444)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (log.matched) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${log.appName} - ₹${log.fareAmount}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = log.actionTaken,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = log.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
