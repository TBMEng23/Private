package com.tbm.iptv

import android.app.Application
import com.tbm.iptv.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(this)
        Prefs.init(this)
        I18n.init()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
