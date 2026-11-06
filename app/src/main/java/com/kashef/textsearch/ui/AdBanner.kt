package com.kashef.textsearch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kashef.textsearch.ads.AdManager
import com.kashef.textsearch.ui.theme.TextSecondary
import com.startapp.sdk.ads.banner.Banner

/** A single Start.io banner. Shows nothing unless ads are configured and the SDK is ready. */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!AdManager.configured() || !AdManager.ready) return
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("إعلان", color = TextSecondary, fontSize = 10.sp)
        AndroidView(factory = ::Banner)
    }
}
