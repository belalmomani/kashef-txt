package com.kashef.textsearch.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import com.kashef.textsearch.ui.search.InvestigationScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashef.textsearch.ui.theme.ScreenGradient
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

@Composable
fun KashefApp() {
    var tab by rememberSaveable { mutableStateOf(BottomTab.Search) }
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(0) }
    var investigating by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.background(ScreenGradient),
        bottomBar = { KashefBottomBar(selected = tab, onSelect = { tab = it }) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (tab == BottomTab.Search && investigating) {
                InvestigationScreen(query.trim(), scopes[scope].title) { investigating = false }
            } else if (tab == BottomTab.Search) {
                HomeScreen(
                    query = query, onQueryChange = { query = it },
                    selected = scope, onSelect = { scope = it },
                    onStart = {
                        if (query.isBlank()) {
                            Toast.makeText(ctx, "اكتب عبارة للبحث أولًا", Toast.LENGTH_SHORT).show()
                        } else investigating = true
                    },
                )
            } else {
                ComingSoon(tab.label)
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "هذا القسم غير مفعّل بعد، وسيُبنى في مرحلة لاحقة.",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
