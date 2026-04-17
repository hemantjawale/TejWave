package com.example.tejastra.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

object LocationModeManager {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            client.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(60_000)
                    .build(),
                null,
            ).await()
        } catch (_: Exception) {
            try {
                client.lastLocation.await()
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun syncModeFromCurrentLocation(
        context: Context,
        prefsManager: PrefsManager,
    ): String? {
        if (!prefsManager.isLocationAutomationEnabled) return null
        val location = getCurrentLocation(context) ?: return null

        val matchedModeId = findMatchingModeId(
            location = location,
            configs = prefsManager.getModeLocationConfigs().values.toList(),
        ) ?: return null

        if (prefsManager.activeFocusModeId != matchedModeId) {
            prefsManager.activeFocusModeId = matchedModeId
            return matchedModeId
        }

        return null
    }

    fun findMatchingModeId(
        location: Location,
        configs: List<ModeLocationConfig>,
    ): String? {
        return configs
            .filter { it.isEnabled }
            .mapNotNull { config ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    config.latitude,
                    config.longitude,
                    results,
                )
                val distance = results.firstOrNull() ?: return@mapNotNull null
                if (distance <= config.radiusMeters) config.modeId to distance else null
            }
            .minByOrNull { it.second }
            ?.first
    }
}
