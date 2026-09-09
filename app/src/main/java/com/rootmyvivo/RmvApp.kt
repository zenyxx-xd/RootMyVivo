package com.rootmyvivo

import android.app.Application
import com.rootmyvivo.shell.Transport

class RmvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Shizuku binder-листенеры (sticky — состояние придёт сразу)
        Transport.initShizuku(this)
    }

    companion object {
        lateinit var instance: RmvApp
            private set
    }
}
