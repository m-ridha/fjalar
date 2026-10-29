package com.muhammadridha.fjalar

import android.Manifest
import android.os.Build

internal enum class LocationPermissionStep {
    FOREGROUND,
    BACKGROUND_RUNTIME,
    GRANTED,
}

internal fun nextLocationPermissionStep(
    apiLevel: Int,
    foregroundGranted: Boolean,
    backgroundGranted: Boolean,
): LocationPermissionStep = when {
    !foregroundGranted -> LocationPermissionStep.FOREGROUND
    apiLevel < Build.VERSION_CODES.Q || backgroundGranted -> LocationPermissionStep.GRANTED
    else -> LocationPermissionStep.BACKGROUND_RUNTIME
}

internal fun foregroundPermissions(apiLevel: Int): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

internal fun shouldOpenForegroundLocationSettings(
    foregroundGranted: Boolean,
    requestCompleted: Boolean,
    fineLocationRationale: Boolean,
    coarseLocationRationale: Boolean,
): Boolean = !foregroundGranted && requestCompleted && !fineLocationRationale && !coarseLocationRationale

internal fun shouldOpenBackgroundLocationSettings(
    backgroundGranted: Boolean,
    requestCompleted: Boolean,
    backgroundRationale: Boolean,
): Boolean = !backgroundGranted && requestCompleted && !backgroundRationale
