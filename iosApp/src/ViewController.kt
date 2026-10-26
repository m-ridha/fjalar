package com.muhammadridha.fjalar

import androidx.compose.runtime.DisposableEffect
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
): UIViewController = ComposeUIViewController {
    DisposableEffect(viewModel) {
        viewModel.syncSchedules()

        val observer = try {
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

        onDispose {
            if (observer != null) {
                try {
                    NSNotificationCenter.defaultCenter.removeObserver(observer)
                } catch (_: Throwable) {
                    // Defensive in headless test environments
                }
            }
        }
    }

    App(
        container = container,
        viewModel = viewModel,
    )
}
