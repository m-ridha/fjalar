package com.muhammadridha.fjalar

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import org.koin.compose.viewmodel.koinViewModel
import com.muhammadridha.fjalar.ui.FjalarViewModel
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIViewController

fun ViewController(): UIViewController = ComposeUIViewController {
    val viewModel: FjalarViewModel = koinViewModel()
    DisposableEffect(viewModel) {
        viewModel.onAppForegroundChanged(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
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

        val activeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
        ) { viewModel.onAppForegroundChanged(true) }
        val inactiveObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue,
        ) { viewModel.onAppForegroundChanged(false) }

        onDispose {
            viewModel.onAppForegroundChanged(false)
            NSNotificationCenter.defaultCenter.removeObserver(activeObserver)
            NSNotificationCenter.defaultCenter.removeObserver(inactiveObserver)
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
        viewModel = viewModel,
    )
}
