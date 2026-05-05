package com.vaish.applock

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.util.*

class RiskEngine(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)

    enum class RiskLevel {
        LOW, MEDIUM, HIGH
    }

    /**
     * Calculates risk score (0-100) based on multiple factors.
     */
    fun calculateRisk(packageName: String): Int {
        var score = 0

        // 1. Failed Attempts Factor (0-60 points)
        val failedAttempts = sharedPrefs.getInt("RecentFailedAttempts", 0)
        score += (failedAttempts * 20).coerceAtMost(60)
        if (failedAttempts > 0) {
            Log.d("RiskEngine", "Failed Attempts Factor: +${(failedAttempts * 20).coerceAtMost(60)}")
        }

        // 2. Frequency Factor (0-40 points) - Opening apps too quickly
        val lastOpenTime = sharedPrefs.getLong("LastAppOpenTime", 0L)
        val timeDiff = System.currentTimeMillis() - lastOpenTime
        if (lastOpenTime > 0 && timeDiff < 10000) { // Less than 10 seconds since last app open
            score += 40
            Log.d("RiskEngine", "Frequency Factor: +40 (Rapid app switching detected: ${timeDiff}ms)")
        }

        // 3. App Sensitivity Factor (0-20 points)
        if (isSensitiveApp(packageName)) {
            score += 20
            Log.d("RiskEngine", "Sensitivity Factor: +20 (Financial/Social App)")
        }

        // 4. Location Factor (0-10 points)
        if (!isSafeLocation()) {
            score += 10
            Log.d("RiskEngine", "Location Factor: +10 (Unknown location)")
        }

        Log.d("RiskEngine", "Total Risk Score for $packageName: $score")
        return score
    }

    fun getRiskLevel(packageName: String): RiskLevel {
        val score = calculateRisk(packageName)
        return when {
            score < 30 -> RiskLevel.LOW
            score < 70 -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }
    }

    private fun isSensitiveApp(packageName: String): Boolean {
        val sensitiveKeywords = listOf("bank", "wallet", "crypto", "social", "chat", "mail", "gallery", "photo", "contact")
        val isInSensitiveList = sharedPrefs.getStringSet("SensitiveApps", emptySet())?.contains(packageName) == true
        val matchesKeyword = sensitiveKeywords.any { packageName.contains(it, ignoreCase = true) }
        return isInSensitiveList || matchesKeyword
    }

    private fun isSafeLocation(): Boolean {
        // 1. Check if the user has opted for Safe Zone protection
        if (!sharedPrefs.getBoolean("SafeZoneEnabled", false)) return true

        // 2. Check WiFi SSID if available
        val currentSsid = getCurrentWifiSsid()
        val trustedSsids = sharedPrefs.getStringSet("TrustedSSIDs", emptySet()) ?: emptySet()
        
        if (currentSsid != null && trustedSsids.contains(currentSsid)) {
            Log.d("RiskEngine", "Safe Location: Connected to trusted WiFi ($currentSsid)")
            return true
        }

        // 3. Optional: Add GPS-based safe zones here if needed

        return false
    }

    private fun getCurrentWifiSsid(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val ssid = info.ssid
            if (ssid != null && ssid != "<unknown ssid>" && ssid != "0x") {
                return ssid.replace("\"", "")
            }
        }
        return null
    }

    fun recordFailedAttempt() {
        val current = sharedPrefs.getInt("RecentFailedAttempts", 0)
        sharedPrefs.edit().putInt("RecentFailedAttempts", current + 1).apply()
        
        // Reset failed attempts after 30 minutes of no activity
        sharedPrefs.edit().putLong("LastFailedAttemptTime", System.currentTimeMillis()).apply()
    }

    fun resetFailedAttempts() {
        sharedPrefs.edit().putInt("RecentFailedAttempts", 0).apply()
    }
}
