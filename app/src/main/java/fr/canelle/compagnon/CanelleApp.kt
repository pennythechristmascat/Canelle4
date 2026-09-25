package fr.canelle.compagnon

import android.app.Application

class CanelleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Diagnostics.installCrashHandler()
        Diagnostics.collect(this)
        Coins.init()
        Lang.init(this)
        Notifs.channels(this)
        if (Store.lastCheckin == 0L) Store.lastCheckin = System.currentTimeMillis()
        CheckinWorker.schedule(this)
        BatteryWorker.schedule(this)
        BatteryWatch.register(this)
    }

    /** Android manque de mémoire et l'appli n'est pas à l'écran : on rend tout de suite la mémoire du cerveau. */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 40 && !visible) LocalModel.releaseSoon(0L) // 40 = TRIM_MEMORY_BACKGROUND
    }

    companion object {
        /** Vrai quand l'écran de Canelle est affiché. */
        @Volatile var visible = false
    }
}
