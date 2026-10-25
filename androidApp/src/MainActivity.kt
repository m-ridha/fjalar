package com.muhammadridha.fjalar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as? FjalarApplication
            val storage = remember {
                app?.container?.keyValueStorage ?: SharedPreferencesKeyValueStorage(applicationContext)
            }
            App(keyValueStorage = storage)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(keyValueStorage = remember { InMemoryKeyValueStorage() })
}
