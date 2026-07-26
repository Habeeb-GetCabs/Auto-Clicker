package com.example.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FareRule
import com.example.model.TripNotificationLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FareFilterDashboardScreen() {
    var minFareInput by remember { mutableStateOf("300") }
    var exactOnly by remember { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf("All Apps") }
    
    var simFareInput by remember { mutableStateOf("300") }
    var simAppName by remember { mutableStateOf("Rapido") }
    var simResult by remember { mutableStateOf<String?>(null) }

    val logs = remember {
        mutableStateListOf(
            TripNotificationLog("1", "Rapido", 320, true, "Triggered simulated accept tap", "10:42 AM"),
            TripNotificationLog("2", "Ola", 250, false, "Ignored (Below minimum fare threshold ₹300)", "10:38 AM"),
            TripNotificationLog("3", "Uber", 300, true, "Matched target fare rule", "10:15 AM")
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
                // Service status banner & toggle
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (serviceEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
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
                                        .background(if (serviceEnabled) Color(0xFF10B981) else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (serviceEnabled) "Filter Active" else "Filter Paused",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (serviceEnabled) "Monitoring ride alerts against target price" else "Tap toggle to resume fare calculation",
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
                                    
                                    if (isMatch && serviceEnabled) {
                                        simResult = "MATCH! Simulated tap on $simAppName trip ₹$simFare"
                                        logs.add(0, TripNotificationLog(
                                            id = System.currentTimeMillis().toString(),
                                            appName = simAppName,
                                            fareAmount = simFare,
                                            matched = true,
                                            actionTaken = "Simulated accept tap executed",
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
                // Educational Accessibility Service Guide
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How Android Background Automation Works",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Android AccessibilityService allows helper tools to inspect screen nodes and detect fare texts on trip popups.\n" +
                                   "• DispatchGesture API enables touch interactions without covering or obstructing the screen.\n" +
                                   "• System policy requires explicit user confirmation in Accessibility Settings before background automation is enabled.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
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
