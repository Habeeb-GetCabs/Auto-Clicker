package com.example.model

data class FareRule(
    val minFare: Int = 300,
    val exactOnly: Boolean = false,
    val enabled: Boolean = true,
    val targetApp: String = "All Apps"
)

data class TripNotificationLog(
    val id: String,
    val appName: String,
    val fareAmount: Int,
    val matched: Boolean,
    val actionTaken: String,
    val timestamp: String
)
