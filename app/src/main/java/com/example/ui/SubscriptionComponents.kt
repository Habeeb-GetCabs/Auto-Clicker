package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.LicenseInfo
import com.example.data.LicenseManager
import kotlinx.coroutines.launch

/**
 * Full-screen or modal dialog that appears when the app is disabled remotely or validity expires.
 */
@Composable
fun SubscriptionPaywallDialog(
    licenseInfo: LicenseInfo,
    onDismiss: () -> Unit,
    onKeyActivated: () -> Unit,
    onSyncRemote: suspend () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputKey by remember { mutableStateOf("") }
    var keyResultMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (licenseInfo.isAccessGranted) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = licenseInfo.isAccessGranted,
            dismissOnClickOutside = licenseInfo.isAccessGranted,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp)
                .testTag("subscription_paywall_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (licenseInfo.isAccessGranted)
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                else
                                    listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (licenseInfo.isAccessGranted) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (!licenseInfo.isRemoteEnabled) "Access Disabled Remotely"
                           else if (!licenseInfo.isAccessGranted) "Subscription Expired"
                           else "Subscription Active",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (!licenseInfo.isRemoteEnabled) "App access has been suspended by the administrator. Contact support on Telegram to re-enable."
                           else if (!licenseInfo.isAccessGranted) "Your validity plan has ended. Choose a plan below and contact on Telegram to enable instant access."
                           else "Your active plan: ${licenseInfo.activePlanName} (Expires: ${licenseInfo.formattedExpiryDate})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Device ID & Copy Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Your Device License ID:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = licenseInfo.deviceId.ifEmpty { "FETCHING..." },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Device ID", licenseInfo.deviceId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied Device ID: ${licenseInfo.deviceId}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_device_id_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy ID", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pricing Plans Box
                Text(
                    text = "Subscription Plans",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1 Week Plan
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("1 Week Plan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = LicenseManager.PLAN_WEEK_PRICE,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("7 Days Access", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // 30 Days Plan
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text("POPULAR", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("30 Days Plan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = LicenseManager.PLAN_MONTH_PRICE,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("30 Days Access", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Direct Telegram Button
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LicenseManager.TELEGRAM_URL))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser. Telegram: ${LicenseManager.TELEGRAM_URL}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("open_telegram_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF229ED9) // Official Telegram Blue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pay & Enable on Telegram (@DriveTechSoft)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider()

                Spacer(modifier = Modifier.height(12.dp))

                // Key Activation Input
                Text(
                    text = "Already paid? Enter Activation Key:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        placeholder = { Text("e.g. WK-LZ123X-A8F9C2", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("activation_key_input"),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val result = LicenseManager.activateWithCode(context, inputKey)
                            keyResultMessage = result
                            if (result.first) {
                                onKeyActivated()
                            }
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("submit_key_button")
                    ) {
                        Text("Activate")
                    }
                }

                keyResultMessage?.let { (success, msg) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (success) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Remote Sync & Close Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isSyncing = true
                                onSyncRemote()
                                isSyncing = false
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("sync_remote_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Check Remote Access Status", fontSize = 11.sp)
                    }

                    if (licenseInfo.isAccessGranted) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_paywall_button")
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Card Banner on Main Screen showing subscription status & live countdown timer.
 */
@Composable
fun SubscriptionStatusBanner(
    licenseInfo: LicenseInfo,
    onOpenPaywall: () -> Unit,
    onOpenAdminTools: () -> Unit
) {
    val context = LocalContext.current
    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // Live 1-second ticker loop to update countdown
    LaunchedEffect(licenseInfo.expiryTimestampMs) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val diffMs = (licenseInfo.expiryTimestampMs - currentTimeMs).coerceAtLeast(0L)
    val totalSeconds = diffMs / 1000
    val days = totalSeconds / (24 * 3600)
    val hours = (totalSeconds % (24 * 3600)) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val liveCountdownText = when {
        !licenseInfo.isAccessGranted -> "Contact @DriveTechSoft on Telegram to activate access"
        days > 0 -> "Valid until: ${licenseInfo.formattedExpiryDate} ($days days, ${hours}h remaining)"
        hours > 0 -> "Expires in: ${hours}h ${minutes}m ${seconds}s"
        else -> "⚡ EXPIRING SOON! Live Countdown: ${String.format("%02d:%02d", minutes, seconds)}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPaywall() }
            .testTag("subscription_status_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (licenseInfo.isAccessGranted)
                if (days == 0L && hours == 0L && minutes < 5) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (licenseInfo.isAccessGranted) Color(0xFF10B981) else Color.Red)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (licenseInfo.isAccessGranted) "License: ACTIVE (${licenseInfo.activePlanName})" else "License: EXPIRED / INACTIVE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = liveCountdownText,
                        fontSize = 11.sp,
                        fontWeight = if (days == 0L && hours == 0L && minutes < 5) FontWeight.Bold else FontWeight.Normal,
                        color = if (days == 0L && hours == 0L && minutes < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LicenseManager.TELEGRAM_URL))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Telegram: ${LicenseManager.TELEGRAM_URL}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("telegram_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Telegram Support",
                        tint = Color(0xFF229ED9),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenAdminTools,
                    modifier = Modifier.testTag("admin_tools_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin Remote Tools",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Admin Remote Control & License Key Generator Dialog for App Owner/Admin
 */
@Composable
fun AdminRemoteControlDialog(
    licenseInfo: LicenseInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var customUrl by remember { mutableStateOf(licenseInfo.remoteConfigUrl) }
    var keyGenOutput by remember { mutableStateOf("") }
    var clientDeviceId by remember { mutableStateOf("") }
    
    var isAuthenticated by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("admin_remote_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (!isAuthenticated) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock, 
                        contentDescription = "Admin Lock", 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Admin Access Restricted", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Enter the admin PIN to access the remote control panel.", 
                        fontSize = 14.sp, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { 
                            pinInput = it
                            pinError = false
                        },
                        label = { Text("Admin PIN") },
                        isError = pinError,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (pinError) {
                        Text("Invalid PIN", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (pinInput == "786786") { // Set your admin PIN here
                                isAuthenticated = true
                            } else {
                                pinError = true
                                pinInput = ""
                            }
                        }) {
                            Text("Unlock")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Admin Remote Controller", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("⚡ Quick Testing Options (Instant Validity):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                LicenseManager.grantCustomDurationMs(context, 60_000L, "1-Minute Test Plan")
                                Toast.makeText(context, "Granted 1 Minute Test Access!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                        ) {
                            Text("⚡ 1 Min", fontSize = 10.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                LicenseManager.grantCustomDurationMs(context, 300_000L, "5-Minute Test Plan")
                                Toast.makeText(context, "Granted 5 Minutes Test Access!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("⚡ 5 Min", fontSize = 10.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                LicenseManager.grantCustomDurationMs(context, -1000L, "Expired Plan")
                                Toast.makeText(context, "Expired Access Immediately!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("⚡ Expire", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Standard Production Plans:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                LicenseManager.grantCustomPlan(context, 7, "1 Week Plan (₹100)")
                                Toast.makeText(context, "Granted 7 Days Access!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1 Wk (₹100)", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                LicenseManager.grantCustomPlan(context, 30, "30 Days Plan (₹299)")
                                Toast.makeText(context, "Granted 30 Days Access!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("30 Days (₹299)", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                LicenseManager.setRemoteEnabled(context, true)
                                Toast.makeText(context, "Remote status set to ENABLED", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Enable App", fontSize = 11.sp, color = Color(0xFF10B981))
                        }

                        OutlinedButton(
                            onClick = {
                                LicenseManager.setRemoteEnabled(context, false)
                                Toast.makeText(context, "Remote status set to DISABLED", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disable App", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Generate Secure Key for Client:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    OutlinedTextField(
                        value = clientDeviceId,
                        onValueChange = { clientDeviceId = it },
                        label = { Text("Client Device ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (clientDeviceId.isNotBlank()) {
                                    keyGenOutput = LicenseManager.generateKeyForDevice(clientDeviceId, "1M")
                                } else {
                                    Toast.makeText(context, "Enter Client Device ID first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1 Min Key", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (clientDeviceId.isNotBlank()) {
                                    keyGenOutput = LicenseManager.generateKeyForDevice(clientDeviceId, "WK")
                                } else {
                                    Toast.makeText(context, "Enter Client Device ID first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1 Wk Key", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (clientDeviceId.isNotBlank()) {
                                    keyGenOutput = LicenseManager.generateKeyForDevice(clientDeviceId, "MO")
                                } else {
                                    Toast.makeText(context, "Enter Client Device ID first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("30 Day Key", fontSize = 10.sp)
                        }
                    }

                    if (keyGenOutput.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(keyGenOutput, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("License Key", keyGenOutput)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied key to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Key", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Remote Server Config JSON URL:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("JSON Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            LicenseManager.setRemoteUrl(context, customUrl)
                            Toast.makeText(context, "Updated Remote Config URL", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Remote URL")
                    }
                }
            }
        }
    }
}
