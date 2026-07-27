package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LicenseInfo(
    val isRemoteEnabled: Boolean = true,
    val expiryTimestampMs: Long = 0L,
    val activePlanName: String = "No Active Plan",
    val deviceId: String = "",
    val activationKey: String = "",
    val lastSyncTimeMs: Long = 0L,
    val remoteConfigUrl: String = "https://raw.githubusercontent.com/DriveTechSoft/FareFilterConfig/main/config.json",
    val adminNote: String = ""
) {
    val isAccessGranted: Boolean
        get() = isRemoteEnabled && System.currentTimeMillis() <= expiryTimestampMs

    val remainingDays: Long
        get() {
            val diffMs = expiryTimestampMs - System.currentTimeMillis()
            return if (diffMs > 0) TimeUnit.MILLISECONDS.toDays(diffMs) else 0L
        }

    val formattedExpiryDate: String
        get() {
            if (expiryTimestampMs <= 0L) return "Not Activated"
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            return sdf.format(Date(expiryTimestampMs))
        }
}

object LicenseManager {
    private const val TAG = "LicenseManager"
    private const val PREFS_NAME = "fare_filter_license_prefs"

    const val TELEGRAM_URL = "https://t.me/DriveTechSoft"
    const val TELEGRAM_USERNAME = "@DriveTechSoft"

    const val PLAN_WEEK_PRICE = "₹100"
    const val PLAN_MONTH_PRICE = "₹299"

    private val _licenseInfo = MutableStateFlow(LicenseInfo())
    val licenseInfo: StateFlow<LicenseInfo> = _licenseInfo.asStateFlow()

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val devId = getOrCreateDeviceId(context, prefs)

        // Default initial setup: If never configured, set 1-day initial trial or setup state
        val isFirstLaunch = !prefs.contains("is_remote_enabled")
        
        val isEnabled = prefs.getBoolean("is_remote_enabled", true)
        val expiry = if (isFirstLaunch) {
            // First install trial - 1 day validity
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
        } else {
            prefs.getLong("expiry_timestamp", 0L)
        }
        val plan = prefs.getString("active_plan", if (isFirstLaunch) "1-Day Free Trial" else "Expired / Inactive") ?: "Expired"
        val key = prefs.getString("activation_key", "") ?: ""
        val url = prefs.getString("remote_config_url", "https://raw.githubusercontent.com/DriveTechSoft/FareFilterConfig/main/config.json") ?: "https://raw.githubusercontent.com/DriveTechSoft/FareFilterConfig/main/config.json"

        if (isFirstLaunch) {
            prefs.edit()
                .putBoolean("is_remote_enabled", true)
                .putLong("expiry_timestamp", expiry)
                .putString("active_plan", "1-Day Free Trial")
                .apply()
        }

        _licenseInfo.value = LicenseInfo(
            isRemoteEnabled = isEnabled,
            expiryTimestampMs = expiry,
            activePlanName = plan,
            deviceId = devId,
            activationKey = key,
            remoteConfigUrl = url,
            lastSyncTimeMs = prefs.getLong("last_sync_time", 0L)
        )
    }

    private fun getOrCreateDeviceId(context: Context, prefs: SharedPreferences): String {
        var id = prefs.getString("device_id", null)
        if (id.isNullOrEmpty()) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"
            val raw = "$androidId-${Build.MANUFACTURER}-${Build.MODEL}"
            id = hashString(raw).take(10).uppercase()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isAccessGranted(context: Context): Boolean {
        if (_licenseInfo.value.deviceId.isEmpty()) {
            init(context)
        }
        return _licenseInfo.value.isAccessGranted
    }

    fun generateKeyForDevice(deviceId: String, planCode: String): String {
        val secret = "DriveTechSoft2026!"
        val timestamp = (System.currentTimeMillis() / 1000).toString(36).uppercase()
        val raw = "$deviceId-$planCode-$timestamp-$secret"
        val hash = hashString(raw).take(6).uppercase()
        return "$planCode-$timestamp-$hash"
    }

    fun activateWithCode(context: Context, codeInput: String): Pair<Boolean, String> {
        val cleanCode = codeInput.trim().uppercase()
        if (cleanCode.isEmpty()) {
            return Pair(false, "Please enter an activation code.")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val usedKeys = prefs.getStringSet("used_keys", emptySet()) ?: emptySet()
        val now = System.currentTimeMillis()

        // Master Admin Override
        if (cleanCode == "ADMIN-ENABLE" || cleanCode == "ENABLE") {
            val newExpiry = now + TimeUnit.DAYS.toMillis(365)
            updateLicenseState(context, isEnabled = true, expiry = newExpiry, plan = "VIP Admin Access (1 Year)", key = cleanCode)
            return Pair(true, "Access Granted! Admin 365 Days Plan Activated.")
        }
        if (cleanCode == "ADMIN-DISABLE" || cleanCode == "REVOKE") {
            updateLicenseState(context, isEnabled = false, expiry = 0L, plan = "Revoked / Disabled", key = cleanCode)
            return Pair(true, "Access Disabled / Revoked successfully.")
        }

        // Short Test Keys for quick verification (Admin overrides, won't enforce used key)
        when {
            cleanCode == "TEST1MIN" || cleanCode == "DRIVE1MIN" -> {
                val newExpiry = now + TimeUnit.MINUTES.toMillis(1)
                updateLicenseState(context, isEnabled = true, expiry = newExpiry, plan = "1-Minute Quick Test Plan", key = cleanCode)
                return Pair(true, "1-Minute Test Plan Activated! App will shut down in 60 seconds.")
            }
            cleanCode == "TEST5MIN" || cleanCode == "DRIVE5MIN" -> {
                val newExpiry = now + TimeUnit.MINUTES.toMillis(5)
                updateLicenseState(context, isEnabled = true, expiry = newExpiry, plan = "5-Minute Quick Test Plan", key = cleanCode)
                return Pair(true, "5-Minute Test Plan Activated! App will shut down in 5 minutes.")
            }
            cleanCode == "TEST1DAY" || cleanCode == "DRIVE1DAY" -> {
                val newExpiry = now + TimeUnit.DAYS.toMillis(1)
                updateLicenseState(context, isEnabled = true, expiry = newExpiry, plan = "1-Day Test Plan", key = cleanCode)
                return Pair(true, "1-Day Test Plan Activated! App will shut down in 24 hours.")
            }
        }

        // --- NEW CRYPTOGRAPHIC KEY VALIDATION ---
        // Format: [PLAN]-[TIMESTAMP]-[HMAC]
        // E.g., WK-LZ123X-A8F9C2
        val parts = cleanCode.split("-")
        if (parts.size == 3) {
            val planCode = parts[0]
            val timestamp = parts[1]
            val hmac = parts[2]
            val deviceId = _licenseInfo.value.deviceId

            // Reconstruct the expected hash
            val secret = "DriveTechSoft2026!"
            val raw = "$deviceId-$planCode-$timestamp-$secret"
            val expectedHmac = hashString(raw).take(6).uppercase()

            if (hmac == expectedHmac) {
                if (usedKeys.contains(cleanCode)) {
                    return Pair(false, "This activation key has already been used on this device.")
                }

                // Valid key! Determine duration
                val durationMs = when (planCode) {
                    "1M" -> TimeUnit.MINUTES.toMillis(1)
                    "5M" -> TimeUnit.MINUTES.toMillis(5)
                    "1D" -> TimeUnit.DAYS.toMillis(1)
                    "WK" -> TimeUnit.DAYS.toMillis(7)
                    "MO" -> TimeUnit.DAYS.toMillis(30)
                    else -> TimeUnit.DAYS.toMillis(7) // default to 7 if unknown plan code but valid hash
                }
                val planName = when (planCode) {
                    "1M" -> "1-Minute Quick Test"
                    "5M" -> "5-Minute Test"
                    "1D" -> "1-Day Plan"
                    "WK" -> "1 Week Plan ($PLAN_WEEK_PRICE)"
                    "MO" -> "30 Days Plan ($PLAN_MONTH_PRICE)"
                    else -> "Custom Plan"
                }

                val currentExpiry = _licenseInfo.value.expiryTimestampMs.coerceAtLeast(now)
                val newExpiry = currentExpiry + durationMs

                // Save to used keys
                val newUsedKeys = usedKeys.toMutableSet().apply { add(cleanCode) }
                prefs.edit().putStringSet("used_keys", newUsedKeys).apply()

                updateLicenseState(context, isEnabled = true, expiry = newExpiry, plan = planName, key = cleanCode)
                return Pair(true, "Success! $planName activated.")
            }
        }

        return Pair(false, "Invalid activation key. Please contact @DriveTechSoft on Telegram to receive a valid key for Device ID: ${_licenseInfo.value.deviceId}.")
    }

    fun grantCustomPlan(context: Context, days: Int, planName: String) {
        val now = System.currentTimeMillis()
        val currentExpiry = _licenseInfo.value.expiryTimestampMs.coerceAtLeast(now)
        val newExpiry = currentExpiry + TimeUnit.DAYS.toMillis(days.toLong())
        updateLicenseState(context, isEnabled = true, expiry = newExpiry, plan = planName, key = "MANUAL_ADMIN_GRANT")
    }

    fun grantCustomDurationMs(context: Context, durationMs: Long, planName: String) {
        val now = System.currentTimeMillis()
        val newExpiry = now + durationMs
        updateLicenseState(context, isEnabled = true, expiry = newExpiry, plan = planName, key = "QUICK_TEST_GRANT")
    }

    fun setRemoteEnabled(context: Context, enabled: Boolean) {
        val current = _licenseInfo.value
        updateLicenseState(context, isEnabled = enabled, expiry = current.expiryTimestampMs, plan = current.activePlanName, key = current.activationKey)
    }

    fun setRemoteUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("remote_config_url", url).apply()
        _licenseInfo.value = _licenseInfo.value.copy(remoteConfigUrl = url)
    }

    private fun updateLicenseState(context: Context, isEnabled: Boolean, expiry: Long, plan: String, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_remote_enabled", isEnabled)
            .putLong("expiry_timestamp", expiry)
            .putString("active_plan", plan)
            .putString("activation_key", key)
            .putLong("last_sync_time", System.currentTimeMillis())
            .apply()

        _licenseInfo.value = _licenseInfo.value.copy(
            isRemoteEnabled = isEnabled,
            expiryTimestampMs = expiry,
            activePlanName = plan,
            activationKey = key,
            lastSyncTimeMs = System.currentTimeMillis()
        )
    }

    /**
     * Asynchronously fetches remote JSON config to update access status live over the network.
     */
    suspend fun syncRemoteConfig(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val url = _licenseInfo.value.remoteConfigUrl
            if (url.isBlank()) return@withContext Pair(false, "No remote URL configured.")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Pair(false, "Remote server returned HTTP ${response.code}")
            }

            val bodyString = response.body?.string() ?: ""
            if (bodyString.isBlank()) return@withContext Pair(false, "Empty response from remote config server.")

            val json = JSONObject(bodyString)
            val devId = _licenseInfo.value.deviceId

            var remoteEnabled = json.optBoolean("is_global_enabled", true)
            var note = json.optString("admin_note", "")

            // Check if specific device is blocked or explicitly enabled
            if (json.has("blocked_devices")) {
                val blockedArray = json.getJSONArray("blocked_devices")
                for (i in 0 until blockedArray.length()) {
                    if (blockedArray.getString(i).equals(devId, ignoreCase = true)) {
                        remoteEnabled = false
                        note = "Device $devId blocked remotely by admin."
                        break
                    }
                }
            }

            // Check if remote device validity extensions are defined
            var remoteExpiry = _licenseInfo.value.expiryTimestampMs
            var remotePlan = _licenseInfo.value.activePlanName

            if (json.has("device_subscriptions")) {
                val devSubs = json.getJSONObject("device_subscriptions")
                if (devSubs.has(devId)) {
                    val subInfo = devSubs.getJSONObject(devId)
                    if (subInfo.has("expiry_timestamp")) {
                        remoteExpiry = subInfo.getLong("expiry_timestamp")
                    }
                    if (subInfo.has("plan_name")) {
                        remotePlan = subInfo.getString("plan_name")
                    }
                    if (subInfo.has("is_enabled")) {
                        remoteEnabled = subInfo.getBoolean("is_enabled")
                    }
                }
            }

            withContext(Dispatchers.Main) {
                updateLicenseState(context, isEnabled = remoteEnabled, expiry = remoteExpiry, plan = remotePlan, key = _licenseInfo.value.activationKey)
                _licenseInfo.value = _licenseInfo.value.copy(adminNote = note)
            }

            return@withContext Pair(true, "Synced with remote server. Status: ${if (remoteEnabled) "Active" else "Disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync remote config", e)
            return@withContext Pair(false, "Remote sync error: ${e.localizedMessage ?: "Network failed"}")
        }
    }
}
