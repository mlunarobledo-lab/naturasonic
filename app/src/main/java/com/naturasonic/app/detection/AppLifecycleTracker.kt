package com.naturasonic.app.detection

import android.app.Activity
import android.app.Application
import android.os.Bundle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLifecycleTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var resumedCount = 0

    val isAppInForeground: Boolean get() = resumedCount > 0

    override fun onActivityResumed(activity: Activity) { resumedCount++ }
    override fun onActivityPaused(activity: Activity) { resumedCount-- }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
