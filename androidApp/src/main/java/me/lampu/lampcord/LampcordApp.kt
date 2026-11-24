package me.lampu.lampcord

import android.app.Application
import me.lampu.lampcord.shared.di.appModule
import me.lampu.lampcord.shared.settings.Settings
import me.lampu.lampcord.shared.settings.initSettings
import me.lampu.lampcord.shared.utils.AndroidContextProvider
import me.lampu.lampcord.shared.utils.Logging
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LampcordApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The crash report screen runs in its own process; skip app init there so a startup
        // crash can't repeat and take the report down too.
        if (CrashReporter.isCrashProcess()) return
        CrashReporter.install(this)
        AndroidContextProvider.applicationContext = this
        initSettings(this)

        Logging.debugEnabled = Settings.shared.verboseLogging

        startKoin {
            androidContext(this@LampcordApp)
            modules(appModule, androidNotificationModule)
        }

        NotificationHelper.ensureMessageChannels(this)

        AppLifecycleTracker.register(this)
    }
}
