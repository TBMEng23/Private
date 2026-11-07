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
        warmStateHolders()
    }

    /** יוצר מראש אובייקטים גלובליים עם Compose state, על ה-main thread ומחוץ לציור מסך (מונע את קריסת ה-snapshot) */
    private fun warmStateHolders() {
        runCatching {
            com.tbm.iptv.data.Catalog.ready.intValue
            com.tbm.iptv.data.Parental.unlocked
            com.tbm.iptv.data.Epg.version.intValue
            com.tbm.iptv.data.Updater.status
            com.tbm.iptv.player.PlayerHolder.miniActive
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
