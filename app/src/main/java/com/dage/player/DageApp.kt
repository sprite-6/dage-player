package com.dage.player

import android.app.Application
import com.dage.player.data.Prefs
import com.dage.player.data.ProgressStore

class DageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ProgressStore.init(this)
    }
}
