package com.muhammadridha.fjalar.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FjalarAppUiLogicTest {

    @Test
    fun shouldDisplayEditorNotice_suppressesAllNoticesOnMapPickerOrWhenNull() {
        for (notice in AlarmEditorNotice.entries) {
            assertFalse(
                shouldDisplayEditorNotice(
                    notice = notice,
                    isMapPickerRoute = true,
                    isBottomBarVisible = true,
                    hasActiveDraft = true,
                ),
            )
        }
        assertFalse(
            shouldDisplayEditorNotice(
                notice = null,
                isMapPickerRoute = false,
                isBottomBarVisible = true,
                hasActiveDraft = true,
            ),
        )
    }

    @Test
    fun shouldDisplayEditorNotice_displaysOnConfigureAndTopLevelTabsWhenAllowed() {
        assertTrue(
            shouldDisplayEditorNotice(
                notice = AlarmEditorNotice.NAME_REQUIRED,
                isMapPickerRoute = false,
                isBottomBarVisible = false,
                hasActiveDraft = true,
            ),
        )
        assertTrue(
            shouldDisplayEditorNotice(
                notice = AlarmEditorNotice.NO_DESTINATION,
                isMapPickerRoute = false,
                isBottomBarVisible = false,
                hasActiveDraft = true,
            ),
        )
        assertTrue(
            shouldDisplayEditorNotice(
                notice = AlarmEditorNotice.UPDATE_FAILED,
                isMapPickerRoute = false,
                isBottomBarVisible = false,
                hasActiveDraft = false,
            ),
        )
        assertTrue(
            shouldDisplayEditorNotice(
                notice = AlarmEditorNotice.ALARM_DELETED,
                isMapPickerRoute = false,
                isBottomBarVisible = true,
                hasActiveDraft = false,
            ),
        )
        assertFalse(
            shouldDisplayEditorNotice(
                notice = AlarmEditorNotice.NAME_REQUIRED,
                isMapPickerRoute = false,
                isBottomBarVisible = false,
                hasActiveDraft = false,
            ),
        )
    }

    @Test
    fun shouldShowLocationPermissionBanner_requiresMissingPermissionUndismissedAndUnsuppressed() {
        assertTrue(
            shouldShowLocationPermissionBanner(
                hasLocationPermission = false,
                permissionBannerDismissed = false,
                suppressPermissionBanner = false,
            ),
        )
        assertFalse(
            shouldShowLocationPermissionBanner(
                hasLocationPermission = true,
                permissionBannerDismissed = false,
                suppressPermissionBanner = false,
            ),
        )
        assertFalse(
            shouldShowLocationPermissionBanner(
                hasLocationPermission = false,
                permissionBannerDismissed = true,
                suppressPermissionBanner = false,
            ),
        )
        assertFalse(
            shouldShowLocationPermissionBanner(
                hasLocationPermission = false,
                permissionBannerDismissed = false,
                suppressPermissionBanner = true,
            ),
        )
    }
}
