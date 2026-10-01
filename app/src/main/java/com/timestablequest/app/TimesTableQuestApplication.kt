package com.timestablequest.app

import android.app.Application

class TimesTableQuestApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
