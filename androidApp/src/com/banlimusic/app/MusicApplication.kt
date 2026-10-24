package com.banlimusic.app

import android.app.Application
import com.banlimusic.app.system.notification.Notifications
import com.banlimusic.app.widget.PlayerWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MusicApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        appScope.launch {
            runCatching {
                val settings = container.settingsRepository.current()
                container.cacheRepository.trimTo(settings.cacheMaxBytes)
                PlayerWidgetUpdater.setTheme(this@MusicApplication, settings.widgetTheme)
            }
        }
    }
}
