package com.muhammadridha.fjalar

import android.app.Application

class FjalarApplication : Application() {

    lateinit var container: FjalarAppContainer

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = FjalarAppContainer(this)
    }

    companion object {
        @Volatile
        var instance: FjalarApplication? = null
            internal set
    }
}
