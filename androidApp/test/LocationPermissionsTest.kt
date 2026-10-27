package com.muhammadridha.fjalar

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationPermissionsTest {
    @Test
    fun foregroundMustBeGrantedBeforeRequestingBackgroundAccess() {
        for (apiLevel in listOf(28, 29, 30, 33, 37)) {
            for (backgroundGranted in listOf(false, true)) {
                assertEquals(
                    LocationPermissionStep.FOREGROUND,
                    nextLocationPermissionStep(apiLevel, false, backgroundGranted),
                )
            }
        }
    }

    @Test
    fun androidNineAndEarlierDoNotRequireSeparateBackgroundAccess() {
        assertEquals(LocationPermissionStep.GRANTED, nextLocationPermissionStep(28, true, false))
    }

    @Test
    fun androidTenRequestsBackgroundAccessSeparatelyAtRuntime() {
        assertEquals(LocationPermissionStep.BACKGROUND_RUNTIME, nextLocationPermissionStep(29, true, false))
    }

    @Test
    fun androidElevenAndLaterRequireTheSettingsFlow() {
        for (apiLevel in listOf(30, 31, 33, 37)) {
            assertEquals(
                LocationPermissionStep.BACKGROUND_SETTINGS,
                nextLocationPermissionStep(apiLevel, true, false),
            )
        }
    }

    @Test
    fun grantedBackgroundAccessCompletesTheFlowWithoutAnotherRequest() {
        for (apiLevel in listOf(29, 30, 33, 37)) {
            assertEquals(LocationPermissionStep.GRANTED, nextLocationPermissionStep(apiLevel, true, true))
        }
    }

    @Test
    fun backgroundIsNeverBundledWithForegroundOrNotificationRequests() {
        for (apiLevel in listOf(28, 29, 30, 33, 37)) {
            val permissions = foregroundPermissions(apiLevel).toList()
            assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
            assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
            assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in permissions)
        }
    }

    @Test
    fun notificationsAreRequestedOnlyOnAndroidThirteenAndLater() {
        assertFalse(Manifest.permission.POST_NOTIFICATIONS in foregroundPermissions(32))
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in foregroundPermissions(33))
    }

    @Test
    fun firstForegroundRequestDoesNotOpenSettingsWhenNeitherRationaleIsShown() {
        assertFalse(
            shouldOpenForegroundLocationSettings(
                foregroundGranted = false,
                requestCompleted = false,
                fineLocationRationale = false,
                coarseLocationRationale = false,
            ),
        )
    }

    @Test
    fun completedRequestWithNeitherRationaleOpensSettingsForMissingForegroundAccess() {
        assertTrue(
            shouldOpenForegroundLocationSettings(
                foregroundGranted = false,
                requestCompleted = true,
                fineLocationRationale = false,
                coarseLocationRationale = false,
            ),
        )
    }

    @Test
    fun eitherLocationRationaleKeepsTheNormalPermissionRequestAvailable() {
        for ((fineRationale, coarseRationale) in listOf(true to false, false to true, true to true)) {
            assertFalse(
                shouldOpenForegroundLocationSettings(
                    foregroundGranted = false,
                    requestCompleted = true,
                    fineLocationRationale = fineRationale,
                    coarseLocationRationale = coarseRationale,
                ),
            )
        }
    }

    @Test
    fun grantedForegroundAccessDoesNotOpenSettingsAfterACompletedRequest() {
        assertFalse(
            shouldOpenForegroundLocationSettings(
                foregroundGranted = true,
                requestCompleted = true,
                fineLocationRationale = false,
                coarseLocationRationale = false,
            ),
        )
    }
}
