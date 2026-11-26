package ru.landilf.memeorganizer

import android.app.Application
import ru.landilf.memeorganizer.core.logging.AppLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MemeOrganizerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
    }
}
