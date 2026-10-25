package com.muhammadridha.fjalar

import androidx.compose.ui.window.ComposeUIViewController
import com.muhammadridha.fjalar.di.FjalarIosContainer
import platform.UIKit.UIViewController

fun ViewController(): UIViewController = ViewController(FjalarIosContainer())

fun ViewController(
    container: FjalarIosContainer,
): UIViewController = ComposeUIViewController {
    App(container = container)
}
