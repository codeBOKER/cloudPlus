package com.umbo.cloudplus.data

import android.content.Context
import androidx.core.content.edit

class OnboardingRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cloudplus_onboarding", Context.MODE_PRIVATE)

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean("is_first_launch", true)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit { putBoolean("is_first_launch", false) }
    }

    fun resetFirstLaunch() {
        prefs.edit { putBoolean("is_first_launch", true) }
    }
}
