package com.muhammadridha.fjalar

import androidx.compose.ui.window.ComposeUIViewController
import com.muhammadridha.fjalar.di.FjalarIosContainer
import com.muhammadridha.fjalar.ui.FjalarViewModel
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIViewController

fun ViewController(): UIViewController = ViewController(FjalarIosContainer())

fun ViewController(
    container: FjalarIosContainer,
    viewModel: FjalarViewModel = createDefaultViewModel(
        keyValueStorage = container.keyValueStorage,
        tracker = container.locationTracker,
        scheduler = container.alarmScheduler,
        ringer = container.alarmRinger,
        alarmService = container.alarmService,
        preferencesPort = container.preferences,
        syncSchedulesPort = container.syncSchedulesPort,
    ),
): UIViewController {
    // Reconcile schedules when ViewController opens
    viewModel.syncSchedules()

    // Reconcile schedules when the application returns to the foreground from background
    try {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            viewModel.syncSchedules()
        }
    } catch (_: Throwable) {
        // Defensive in headless test environments
    }

    return ComposeUIViewController {
        App(
            container = container,
            viewModel = viewModel,
        )
    }
}
