package com.banlimusic.app

import android.app.Application
import com.banlimusic.app.system.notification.Notifications

class MusicApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
