package com.kashef.textsearch.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kashef.textsearch.engine.SearchQuery
import com.kashef.textsearch.ui.search.SearchScreen
import com.kashef.textsearch.ui.search.SearchViewModel
import com.kashef.textsearch.ui.theme.ScreenGradient
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

@Composable
fun KashefApp(vm: SearchViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(BottomTab.Search) }
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    fun startSearch() {
        when {
            vm.query.isBlank() -> toast("اكتب عبارة للبحث أولًا")
            SearchQuery.create(vm.query, vm.mode) == null -> toast("العبارة فارغة بعد تجاهل التشكيل والمسافات")
            vm.folders.isEmpty() -> toast("اختر مجلدًا واحدًا على الأقل للبحث فيه")
            else -> vm.start()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(ScreenGradient),
        bottomBar = { KashefBottomBar(selected = tab, onSelect = { tab = it }) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            val current = ui
            if (tab == BottomTab.Search && current != null) {
                SearchScreen(current, onStop = vm::stop, onBack = vm::close, onRetry = ::startSearch)
            } else if (tab == BottomTab.Search) {
                HomeScreen(
                    query = vm.query, onQueryChange = { vm.query = it },
                    selected = vm.scopeIndex, onSelect = { vm.scopeIndex = it },
                    mode = vm.mode, onMode = { vm.mode = it },
                    folders = vm.folders, onAddFolder = { picker.launch(null) },
                    onRemoveFolder = vm::removeFolder,
                    onStart = ::startSearch,
                )
            } else if (tab == BottomTab.Settings) {
                SettingsScreen()
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
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "هذا القسم غير مفعّل بعد، وسيُبنى في مرحلة لاحقة.",
            color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
