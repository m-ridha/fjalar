package com.muhammadridha.fjalar

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.muhammadridha.fjalar.ui.FjalarViewModel
import org.koin.compose.viewmodel.koinViewModel
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIViewController
import platform.darwin.NSObject

internal fun isAlwaysLocationAuthorized(status: CLAuthorizationStatus): Boolean =
    status == kCLAuthorizationStatusAuthorizedAlways

private fun isForegroundLocationAuthorized(status: CLAuthorizationStatus): Boolean =
    status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse

fun ViewController(): UIViewController = ComposeUIViewController {
    val viewModel: FjalarViewModel = koinViewModel()
    val permissionManager = CLLocationManager()

    fun refreshLocationPermissionState() {
        val status = permissionManager.authorizationStatus
        val granted = isAlwaysLocationAuthorized(status)
        val previouslyGranted = viewModel.uiState.value.hasLocationPermission
        viewModel.onPermissionResult(granted)
        viewModel.onLocationAccessChecked(isForegroundLocationAuthorized(status))
        if (granted && !previouslyGranted) {
            viewModel.syncSchedules()
        }
    }

    DisposableEffect(viewModel) {
        val locationDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                refreshLocationPermissionState()
            }

            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                // Live fixes are delivered to IosCLLocationAdapter's active CLLocationManager.
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // Delegate method required for one-shot requestLocation() calls.
            }
        }
        permissionManager.delegate = locationDelegate
        viewModel.onAppForegroundChanged(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
        refreshLocationPermissionState()
        viewModel.syncSchedules()
        viewModel.requestCurrentLocation = {
            val active = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
            viewModel.onAppForegroundChanged(active)
            if (isForegroundLocationAuthorized(permissionManager.authorizationStatus)) {
                permissionManager.requestLocation()
            }
        }

        val foregroundObserver = try {
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationWillEnterForegroundNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                viewModel.syncSchedules()
            }
        } catch (_: Throwable) {
            null
        }

        val backgroundObserver = try {
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidEnterBackgroundNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                viewModel.syncSchedules()
            }
        } catch (_: Throwable) {
            null
        }

        val activeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
        ) {
            viewModel.onAppForegroundChanged(true)
            refreshLocationPermissionState()
        }
        val inactiveObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue,
        ) { viewModel.onAppForegroundChanged(false) }

        onDispose {
            viewModel.onAppForegroundChanged(false)
            viewModel.requestCurrentLocation = null
            permissionManager.delegate = null
            NSNotificationCenter.defaultCenter.removeObserver(activeObserver)
            NSNotificationCenter.defaultCenter.removeObserver(inactiveObserver)
            if (foregroundObserver != null) {
                try {
                    NSNotificationCenter.defaultCenter.removeObserver(foregroundObserver)
                } catch (_: Throwable) {
                    // Defensive in headless test environments
                }
            }
            if (backgroundObserver != null) {
                try {
                    NSNotificationCenter.defaultCenter.removeObserver(backgroundObserver)
                } catch (_: Throwable) {
                    // Defensive in headless test environments
                }
            }
        }
    }

    App(
        viewModel = viewModel,
        onRequestPermission = {
            when (permissionManager.authorizationStatus) {
                kCLAuthorizationStatusNotDetermined,
                kCLAuthorizationStatusAuthorizedWhenInUse -> permissionManager.requestAlwaysAuthorization()
                kCLAuthorizationStatusDenied,
                kCLAuthorizationStatusRestricted -> {
                    NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { settingsUrl ->
                        UIApplication.sharedApplication.openURL(
                            url = settingsUrl,
                            options = emptyMap<Any?, Any?>(),
                            completionHandler = null,
                        )
                    }
                }
                else -> Unit
            }
        },
    )
}
