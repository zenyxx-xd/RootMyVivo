package com.rootmyvivo

import android.app.Application
import com.rootmyvivo.core.ShellBridge
import com.rootmyvivo.core.native.NativeLibs

class RmvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        NativeLibs.init()
        // Shizuku binder-листенеры (sticky — сразу получим состояние)
        ShellBridge.initShizukuListeners(this)
    }

    companion object {
        lateinit var instance: RmvApp
            private set
    }
}
