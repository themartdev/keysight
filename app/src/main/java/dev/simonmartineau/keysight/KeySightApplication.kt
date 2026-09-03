package dev.simonmartineau.keysight

import android.app.Application
import dev.simonmartineau.keysight.di.AppContainer

class KeySightApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.midiDeviceManager.start()
    }
}
