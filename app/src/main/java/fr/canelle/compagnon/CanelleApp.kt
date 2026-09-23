package fr.canelle.compagnon

import android.app.Application

class CanelleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifs.channels(this)
        if (Store.lastCheckin == 0L) Store.lastCheckin = System.currentTimeMillis()
        CheckinWorker.schedule(this)
    }
}
