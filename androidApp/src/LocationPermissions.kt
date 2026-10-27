package com.muhammadridha.fjalar

import android.Manifest
import android.os.Build

internal enum class LocationPermissionStep {
    FOREGROUND,
    BACKGROUND_RUNTIME,
    BACKGROUND_SETTINGS,
    GRANTED,
}

internal fun nextLocationPermissionStep(
    apiLevel: Int,
    foregroundGranted: Boolean,
    backgroundGranted: Boolean,
): LocationPermissionStep = when {
    !foregroundGranted -> LocationPermissionStep.FOREGROUND
    apiLevel < Build.VERSION_CODES.Q || backgroundGranted -> LocationPermissionStep.GRANTED
    apiLevel == Build.VERSION_CODES.Q -> LocationPermissionStep.BACKGROUND_RUNTIME
    else -> LocationPermissionStep.BACKGROUND_SETTINGS
}

internal fun foregroundPermissions(apiLevel: Int): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
