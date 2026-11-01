package com.muhammadridha.fjalar

import android.app.Application
import com.muhammadridha.fjalar.di.androidPlatformModule
import com.muhammadridha.fjalar.di.androidPresentationModule
import com.muhammadridha.fjalar.di.startFjalar
import org.koin.android.ext.koin.androidContext

class FjalarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startFjalar(androidPlatformModule) {
            androidContext(this@FjalarApplication)
            modules(androidPresentationModule)
        }
    }
}
