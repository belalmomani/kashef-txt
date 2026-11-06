package com.kashef.textsearch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
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
import com.kashef.textsearch.engine.CaseReport
import com.kashef.textsearch.engine.SearchQuery
import com.kashef.textsearch.ui.search.ContextScreen
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
    val viewer by vm.viewer.collectAsState()
    val report by vm.report.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    fun openExternal(uri: String, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uri), mime.ifBlank { "text/plain" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            toast("لا يوجد تطبيق يستطيع فتح هذا الملف")
        }
    }
    fun shareReport(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            ctx.startActivity(Intent.createChooser(send, "مشاركة التقرير").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("تعذرت المشاركة")
        }
    }
    fun startSearch() {
        when {
            vm.query.isBlank() -> toast("اكتب عبارة للبحث أولًا")
            SearchQuery.create(vm.query, vm.mode, vm.flexible) == null -> toast("العبارة فارغة بعد تجاهل التشكيل والمسافات")
            vm.folders.isEmpty() -> toast("اختر مجلدًا واحدًا على الأقل للبحث فيه")
            else -> {
                // Android 13+: ask once for the notification permission so the "search is running" notice can show.
                if (Build.VERSION.SDK_INT >= 33 && !vm.notifAsked() &&
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    vm.markNotifAsked()
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                vm.start()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(ScreenGradient),
        bottomBar = { KashefBottomBar(selected = tab, onSelect = { tab = it; vm.closeReport() }) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            val current = ui
            val view = viewer
            val rep = report
            if (rep != null) {
                ReportScreen(rep, onBack = vm::closeReport, onShare = { shareReport(CaseReport.toText(rep)) })
            } else if (tab == BottomTab.Search && view != null) {
                ContextScreen(view, onBack = vm::closeViewer, onOpenExternal = { openExternal(view.uri, view.mime) })
            } else if (tab == BottomTab.Search && current != null) {
                SearchScreen(
                    current,
                    onStop = vm::stop, onBack = vm::close, onRetry = ::startSearch,
                    onOpenContext = vm::openContext,
                    onOpenExternal = { r -> openExternal(r.uri, r.mime) },
                    onReport = vm::showReport,
                )
            } else if (tab == BottomTab.Search) {
                HomeScreen(
                    query = vm.query, onQueryChange = { vm.query = it },
                    selected = vm.scopeIndex, onSelect = { vm.scopeIndex = it },
                    mode = vm.mode, onMode = { vm.mode = it },
                    flexible = vm.flexible, onFlexible = vm::updateFlexible,
                    folders = vm.folders, onAddFolder = { picker.launch(null) },
                    onRemoveFolder = vm::removeFolder,
                    onStart = ::startSearch,
                )
            } else if (tab == BottomTab.Protection) {
                ProtectionScreen(vm)
            } else if (tab == BottomTab.Files) {
                FilesScreen(vm, onAddFolder = { picker.launch(null) })
            } else if (tab == BottomTab.Memory) {
                MemoryScreen(
                    vm,
                    onRerun = { r -> vm.prefill(r); tab = BottomTab.Search },
                    onReport = { r -> vm.showReportFor(r) },
                )
            } else if (tab == BottomTab.Settings) {
                SettingsScreen(vm)
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
