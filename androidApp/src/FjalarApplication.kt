package com.muhammadridha.fjalar

import android.app.Application
import com.muhammadridha.fjalar.di.androidPlatformModule
import com.muhammadridha.fjalar.di.androidPresentationModule
import com.muhammadridha.fjalar.di.startFjalar
import org.koin.android.ext.koin.androidContext

class FjalarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before any activity: read the saved light/dark choice synchronously, no coroutine wait.
        StartupTheme.syncApplicationNightMode(this)
        startFjalar(androidPlatformModule) {
            androidContext(this@FjalarApplication)
            modules(androidPresentationModule)
        }
    }
}
