package com.tbm.iptv

import android.app.Application
import com.tbm.iptv.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
