package com.pastelcal.app

import android.app.Application
import com.pastelcal.app.diagnostics.CrashReporter

class PastelCalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
