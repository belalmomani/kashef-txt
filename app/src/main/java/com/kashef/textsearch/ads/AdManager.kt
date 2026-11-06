package com.kashef.textsearch.ads

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kashef.textsearch.BuildConfig
import com.startapp.sdk.adsbase.StartAppSDK

/**
 * Start.io integration (calls follow the official Start.io Android docs).
 *
 * Ads are OFF unless the build sets ADS_ENABLED=true AND provides STARTIO_APP_ID (a GitHub secret);
 * no ID is ever stored in the code. Ads appear only as a banner on the home screen, never over the
 * search progress or the results. Personalized ads are OFF until the user turns them on.
 */
object AdManager {
    /** True once the SDK reported that it is ready to request ads. */
    var ready by mutableStateOf(false)
        private set

    fun configured(): Boolean = BuildConfig.ADS_ENABLED && BuildConfig.STARTIO_APP_ID.isNotBlank()

    fun initIfEnabled(context: Context, personalized: Boolean, consentTime: Long) {
        if (!configured()) return
        if (BuildConfig.DEBUG) StartAppSDK.setTestAdsEnabled(true) // test ads only in debug builds
        StartAppSDK.initParams(context, BuildConfig.STARTIO_APP_ID)
            .setReturnAdsEnabled(false)
            .setCallback { ready = true }
            .init()
        // Report the user's consent flag right after init, as Start.io requires.
        StartAppSDK.setUserConsent(context, "pas", consentTime, personalized)
    }

    /** Re-sends the consent flag whenever the user changes it. */
    fun updateConsent(context: Context, personalized: Boolean, time: Long) {
        if (!configured()) return
        StartAppSDK.setUserConsent(context, "pas", time, personalized)
    }
}
