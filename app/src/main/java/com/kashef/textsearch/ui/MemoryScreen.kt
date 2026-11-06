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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.kashef.textsearch.engine.CaseRecord
import com.kashef.textsearch.engine.CaseReport
import com.kashef.textsearch.ui.search.SearchViewModel
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Gold400
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

/** The investigator's memory: an optional local log of past searches (counts only). */
@Composable
fun MemoryScreen(vm: SearchViewModel, onRerun: (CaseRecord) -> Unit, onReport: (CaseRecord) -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("ذاكرة المحقق", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        item {
            MemCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "حفظ سجل عمليات البحث على هذا الجهاز",
                        color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    Switch(checked = vm.memoryEnabled, onCheckedChange = { vm.updateMemoryEnabled(it) })
                }
                Text(
                    "اختياري ومعطّل افتراضيًا. عند تفعيله يُحفظ لكل عملية: عبارة البحث وتاريخها وأسماء المجلدات وأعداد النتائج فقط. " +
                        "لا يُحفظ محتوى الملفات ولا أسماؤها ولا مساراتها. يبقى السجل داخل التطبيق ولا يُرفع إلى أي مكان. " +
                        "إيقاف الخيار لا يمسح ما حُفظ سابقًا؛ استعمل الحذف أدناه.",
                    color = TextSecondary, fontSize = 12.sp,
                )
                if (vm.records.isNotEmpty()) {
                    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("مسح السجل كاملًا")
                    }
                }
            }
        }
        if (vm.records.isEmpty()) {
            item {
                Text(
                    if (vm.memoryEnabled) "لا توجد عمليات محفوظة بعد. ستظهر هنا بعد أول بحث."
                    else "الذاكرة معطّلة ولا توجد عمليات محفوظة.",
                    color = TextSecondary, fontSize = 13.sp,
                )
            }
        } else {
            items(vm.records, key = { it.id }) { r ->
                MemCard {
                    Text("«${r.query}»", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${CaseReport.formatTime(r.startedAt)} • ${CaseReport.modeLabel(r.mode)} • ${r.scopeTitle}",
                        color = TextSecondary, fontSize = 11.sp,
                    )
                    Text(
                        "فُحص ${r.scanned} • مطابق ${r.matchedFiles} • تعذر ${r.problems} • ${CaseReport.statusLabel(r.status)}",
                        color = Cyan400, fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onReport(r) }) { Text("تقرير") }
                        OutlinedButton(onClick = { onRerun(r) }) { Text("إعادة") }
                        OutlinedButton(onClick = { vm.deleteRecord(r.id) }) { Text("حذف") }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح السجل كاملًا؟") },
            text = { Text("ستُحذف كل العمليات المحفوظة نهائيًا من هذا الجهاز.") },
            confirmButton = {
                TextButton(onClick = { vm.clearRecords(); confirmClear = false }) { Text("مسح", color = Gold400) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun MemCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardNavy)
            .border(BorderStroke(1.dp, Outline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
