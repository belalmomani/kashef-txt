package com.kashef.textsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashef.textsearch.ads.AdManager
import com.kashef.textsearch.diag.DiagLog
import com.kashef.textsearch.ui.search.SearchViewModel
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Gold400
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

private enum class Confirm { Memory, Indexes, Folders, Everything }

/** Privacy of the app's own data: what is stored and where, the ads/consent status, and deletion. */
@Composable
fun ProtectionScreen(vm: SearchViewModel, onShare: (String, String) -> Unit) {
    val roots by vm.indexRoots.collectAsState()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf(listOf<String>()) }
    val indexFiles = roots.sumOf { it.files }
    val indexKb = (roots.sumOf { it.bytes } + 1023) / 1024

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("الحماية", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        item {
            PCard {
                Text("ما الذي يخزنه كاشف على جهازك، وأين؟", color = Cyan400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("كل ما يلي داخل مساحة التطبيق الخاصة، ولا يصل إليه تطبيق آخر في الوضع العادي، ويُحذف بحذف التطبيق:", color = TextSecondary, fontSize = 11.sp)
                Line("المجلدات المسموح بها", "${vm.folders.size} مجلد (القائمة + صلاحية قراءة منحها أندرويد)")
                Line("ذاكرة المحقق", if (vm.memoryEnabled) "مفعّلة • ${vm.records.size} عملية (العبارة والتاريخ والأعداد فقط)" else "معطّلة • ${vm.records.size} عملية محفوظة سابقًا")
                Line("الفهرس المحلي", if (roots.isEmpty()) "لا يوجد" else "${roots.size} مجلد • $indexFiles ملف • $indexKb كيلوبايت (بصمات مجزأة، لا نص مقروء)")
                Line("الخيارات", "إعدادات صغيرة مثل كاشف فوق التطبيقات")
                Line("سجل التشخيص", "أنواع الأعطال ومواضعها وأعداد البحث فقط (حتى 200 سطر)")
                Text(
                    "لا يخزّن التطبيق أبدًا: محتوى الملفات، ولا نتائج البحث، ولا أسماء الملفات أو مساراتها في الذاكرة.",
                    color = Gold400, fontSize = 12.sp,
                )
            }
        }
        item {
            PCard {
                Text("الاتصال بالإنترنت", color = Cyan400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "لا يرفع التطبيق أي نص أو ملف إلى أي مكان، والبحث والفهرسة محليان بالكامل. إذن الإنترنت موجود لإعلانات Start.io فقط، " +
                        "ولا تستخدمه ميزات البحث.",
                    color = TextPrimary, fontSize = 13.sp,
                )
            }
        }
        item {
            PCard {
                Text("الإعلانات", color = Cyan400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (!AdManager.configured()) {
                    Text("غير مفعّلة في هذا الإصدار: لا يتصل التطبيق بأي شبكة إعلانية ولا يظهر إعلان.", color = TextPrimary, fontSize = 13.sp)
                } else {
                    Text(
                        "الإعلانات مفعّلة في هذا الإصدار، وتظهر كشريط واحد في الشاشة الرئيسية فقط، ولا تغطي تقدّم البحث ولا النتائج. " +
                            "قد تعرض Start.io أيضًا نافذة إفصاح وموافقة خاصة بها عند التشغيل.",
                        color = TextPrimary, fontSize = 13.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("السماح بإعلانات مخصصة", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(checked = vm.adsPersonalized, onCheckedChange = { vm.updateAdsPersonalized(it) })
                    }
                    Text(
                        "معطّل افتراضيًا. محتوى ملفاتك وعبارات بحثك لا تُرسل إلى شبكة الإعلانات في أي حال؛ هذا الخيار يخص تخصيص الإعلانات فقط.",
                        color = TextSecondary, fontSize = 11.sp,
                    )
                }
            }
        }
        item {
            PCard {
                Text("تقرير التشخيص", color = Cyan400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "يسجّل التطبيق محليًا نوع الأعطال وموضعها في الكود وأعداد نتائج البحث فقط، بلا أسماء ملفات ولا محتواها ولا عبارات بحثك. " +
                        "لا يُرسل شيئًا تلقائيًا؛ تشاركه أنت لمن تشاء، ويفيد المطوّر في إصلاح الأخطاء.",
                    color = TextPrimary, fontSize = 12.sp,
                )
                OutlinedButton(
                    onClick = { onShare("مشاركة تقرير التشخيص", vm.diagnosticsReport()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("مشاركة تقرير التشخيص") }
                OutlinedButton(
                    onClick = {
                        showLog = !showLog
                        if (showLog) logLines = DiagLog.lines(15)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (showLog) "إخفاء آخر الأحداث" else "عرض آخر الأحداث") }
                if (showLog) {
                    if (logLines.isEmpty()) Text("لا أحداث مسجلة.", color = TextSecondary, fontSize = 11.sp)
                    logLines.forEach { Text(it, color = TextSecondary, fontSize = 10.sp) }
                }
                OutlinedButton(
                    onClick = { DiagLog.clear(); logLines = emptyList() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("مسح سجل التشخيص") }
            }
        }
        item {
            PCard {
                Text("حذف البيانات المحلية", color = Cyan400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { confirm = Confirm.Memory }, modifier = Modifier.fillMaxWidth()) { Text("مسح ذاكرة المحقق") }
                OutlinedButton(onClick = { confirm = Confirm.Indexes }, modifier = Modifier.fillMaxWidth()) { Text("حذف كل الفهارس") }
                OutlinedButton(onClick = { confirm = Confirm.Folders }, modifier = Modifier.fillMaxWidth()) { Text("سحب صلاحية كل المجلدات") }
                OutlinedButton(onClick = { confirm = Confirm.Everything }, modifier = Modifier.fillMaxWidth()) { Text("حذف كل البيانات المحلية") }
            }
        }
    }

    val c = confirm
    if (c != null) {
        val (title, body) = when (c) {
            Confirm.Memory -> "مسح ذاكرة المحقق؟" to "ستُحذف كل العمليات المحفوظة نهائيًا."
            Confirm.Indexes -> "حذف كل الفهارس؟" to "يُحذف الفهرس وتُعطَّل الفهرسة لكل المجلدات، ويصبح البحث أبطأ."
            Confirm.Folders -> "سحب صلاحية كل المجلدات؟" to "يُلغى وصول التطبيق إلى كل المجلدات وتُحذف فهارسها. يمكنك اختيارها مجددًا لاحقًا."
            Confirm.Everything -> "حذف كل البيانات المحلية؟" to "يُحذف كل شيء: المجلدات وصلاحياتها والفهارس وذاكرة المحقق والخيارات."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    when (c) {
                        Confirm.Memory -> vm.clearRecords()
                        Confirm.Indexes -> vm.deleteAllIndexes()
                        Confirm.Folders -> vm.removeAllFolders()
                        Confirm.Everything -> vm.deleteAllLocalData()
                    }
                    confirm = null
                }) { Text("حذف", color = Gold400) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun PCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardNavy)
            .border(BorderStroke(1.dp, Outline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
