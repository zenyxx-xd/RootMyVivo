package com.rootmyvivo

import android.app.Application
import com.rootmyvivo.core.native.NativeLibs

class RmvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        NativeLibs.load()
    }

    companion object {
        lateinit var instance: RmvApp
            private set
    }
}
