package com.kashef.textsearch

import android.app.Application
import com.kashef.textsearch.diag.DiagLog

class KashefApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        // Record the kind and place of an uncaught crash (never its message), then let Android handle it as usual.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { DiagLog.crash(error) } catch (t: Throwable) { /* ignore */ }
            previous?.uncaughtException(thread, error)
        }
        DiagLog.log("app", "start v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
