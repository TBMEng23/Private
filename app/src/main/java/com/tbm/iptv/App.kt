package com.tbm.iptv

import android.app.Application
import com.tbm.iptv.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(this)
        CrashLog.watchdog()
        Prefs.init(this)
        I18n.init()
        runCatching { com.tbm.iptv.data.Cloud.init() }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
