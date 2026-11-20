package com.example.cachecleaner

import android.app.Application
import com.google.android.material.color.DynamicColors

class CleanerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
