package com.charikot.parentlock

import android.app.Application

class CharikotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
    }
}
