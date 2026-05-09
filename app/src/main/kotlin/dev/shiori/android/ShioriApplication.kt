package dev.shiori.android

import android.app.Application
import com.google.android.material.color.DynamicColors

class ShioriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
