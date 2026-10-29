package com.kashef.textsearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.kashef.textsearch.ads.AdManager
import com.kashef.textsearch.ui.KashefApp
import com.kashef.textsearch.ui.search.SearchViewModel
import com.kashef.textsearch.ui.theme.KashefTheme

class MainActivity : ComponentActivity() {
    private val vm: SearchViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
    }

    override fun onStop() {
        vm.setForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdManager.initIfEnabled(applicationContext) // no-op unless ads are enabled in the build
        setContent {
            // The app is Arabic-first: force RTL for the whole UI.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KashefTheme { KashefApp() }
            }
        }
    }
}
