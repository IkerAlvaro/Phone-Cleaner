package com.ikeralvaro.phonecleaner

import android.app.Application

class PhoneCleanerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
