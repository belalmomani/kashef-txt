package com.kashef.textsearch.ads

import android.content.Context
import com.kashef.textsearch.BuildConfig
import com.startapp.sdk.adsbase.StartAppSDK

/**
 * Start.io integration point (prepared, disabled by default).
 * Ads start only when the build sets ADS_ENABLED=true AND provides STARTIO_APP_ID.
 * No ad is shown anywhere in the UI yet; ad placement belongs to the dedicated ads phase.
 */
object AdManager {
    @Volatile var ready: Boolean = false
        private set

    fun initIfEnabled(context: Context) {
        if (!BuildConfig.ADS_ENABLED || BuildConfig.STARTIO_APP_ID.isBlank()) return
        StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
        StartAppSDK.initParams(context, BuildConfig.STARTIO_APP_ID)
            .setReturnAdsEnabled(false)
            .setCallback { ready = true }
            .init()
    }
}
