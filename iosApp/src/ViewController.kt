package com.muhammadridha.fjalar

import androidx.compose.ui.window.ComposeUIViewController
import com.muhammadridha.fjalar.adapters.storage.UserDefaultsKeyValueStorage

fun ViewController() = ComposeUIViewController {
    App(keyValueStorage = UserDefaultsKeyValueStorage())
}
