package com.kashef.textsearch.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kashef.textsearch.BuildConfig
import com.kashef.textsearch.ui.search.SearchViewModel
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Gold400
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

@Composable
fun SettingsScreen(vm: SearchViewModel) {
    val adsOn = BuildConfig.ADS_ENABLED && BuildConfig.STARTIO_APP_ID.isNotBlank()
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var canDraw by remember { mutableStateOf(vm.overlay.canDraw()) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) canDraw = vm.overlay.canDraw()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("الإعدادات", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

        Section("كاشف فوق التطبيقات") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "إظهار كاشف فوق التطبيقات الأخرى عند مغادرة التطبيق أثناء البحث",
                    color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                Switch(checked = vm.overlayEnabled, onCheckedChange = { vm.updateOverlayEnabled(it) })
            }
            Line("إذن «الظهور فوق التطبيقات»", if (canDraw) "ممنوح" else "غير ممنوح")
            if (!canDraw) {
                Button(onClick = {
                    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        ctx.startActivity(i)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "تعذر فتح صفحة الإذن؛ افتحها من إعدادات التطبيق في الهاتف", Toast.LENGTH_LONG).show()
                    }
                }) { Text("منح إذن الظهور فوق التطبيقات") }
            }
            Text(
                "يظهر كاشف فقط حين تغادر التطبيق وبحثك جارٍ أو انتهى للتو. اسحبه لتحريكه، واضغط عليه لإخفائه.",
                color = TextSecondary, fontSize = 12.sp,
            )
        }

        Section("عن التطبيق") {
            Line("الاسم", "كاشف النصوص — Kashef Text Search")
            Line("الإصدار", BuildConfig.VERSION_NAME)
            Line("رقم البناء", BuildConfig.VERSION_CODE.toString())
            Line("الحزمة", BuildConfig.APPLICATION_ID)
        }

        Section("حالة البحث") {
            Text(
                "يبحث التطبيق داخل محتوى الملفات النصية (TXT, MD, CSV, JSON, XML, HTML) وملفات الأكواد في المجلدات التي تختارها. غير مدعوم بعد: PDF وDOCX وOCR.",
                color = Gold400, fontSize = 13.sp,
            )
        }

        Section("الخصوصية") {
            Text("يقرأ التطبيق فقط المجلدات التي تسمح بها، ويحفظ محليًا قائمة هذه المجلدات وخيار كاشف فوق التطبيقات فقط. لا يحفظ عبارات البحث ولا نتائجها ولا يرفع أي نص أو ملف إلى الإنترنت.", color = TextPrimary, fontSize = 14.sp)
            Text("أي سجل أو فهرس محلي سيُضاف لاحقًا سيكون اختياريًا مع إمكانية حذفه.", color = TextSecondary, fontSize = 12.sp)
        }

        Section("الإعلانات") {
            Line("الحالة في هذا البناء", if (adsOn) "مفعّلة (Start.io)" else "غير مفعّلة")
            Text("لا يظهر أي إعلان في هذه المرحلة.", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardNavy)
            .border(BorderStroke(1.dp, Outline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Cyan400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp)
    }
}
