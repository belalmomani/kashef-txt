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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import com.kashef.textsearch.engine.CaseReport
import com.kashef.textsearch.ui.search.SearchViewModel
import com.kashef.textsearch.ui.search.folderLabel
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Gold400
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

/** Folders the app may read, and the optional local index of each folder. */
@Composable
fun FilesScreen(vm: SearchViewModel, onAddFolder: () -> Unit) {
    val roots by vm.indexRoots.collectAsState()
    val status by vm.index.collectAsState()
    var confirmDeleteAll by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("الملفات والفهرس", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        item {
            FCard {
                Text("الفهرس المحلي (اختياري)", color = Cyan400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "يسرّع البحث المتكرر: يتذكر الفهرس لكل ملف بصمة مجزأة لتتابعات الحروف، فيتجاوز كاشف الملفات التي لم تتغير والتي " +
                        "لا يمكن أن تحتوي عبارتك دون أن يقرأها. تُحدَّث الفهرسة تلقائيًا للملفات الجديدة أو المعدّلة عند البحث.",
                    color = TextPrimary, fontSize = 12.sp,
                )
                Text(
                    "الخصوصية: لا يُفهرس أي مجلد إلا بعد تفعيلك له. الفهرس داخل التطبيق فقط ولا يُرفع إلى أي مكان، ولا يحتوي نصًا مقروءًا، " +
                        "لكن من يملك الوصول إلى بيانات التطبيق قد يختبر به احتمال وجود عبارة معينة. يمكنك حذفه في أي وقت.",
                    color = TextSecondary, fontSize = 11.sp,
                )
            }
        }

        val st = status
        if (st != null) {
            item {
                FCard {
                    Text("الفهرسة: ${st.label}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (st.running) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("فُهرس ${st.indexed} ملفًا حتى الآن…", color = TextSecondary, fontSize = 12.sp)
                        OutlinedButton(onClick = vm::cancelIndex, modifier = Modifier.fillMaxWidth()) { Text("إيقاف الفهرسة") }
                    } else {
                        Text(
                            when {
                                st.message.isNotEmpty() -> "تعثرت الفهرسة: ${st.message}"
                                st.cancelled -> "أُوقفت الفهرسة؛ فُهرس ${st.indexed} ملفًا قبل الإيقاف."
                                else -> "اكتملت الفهرسة: فُهرس ${st.indexed} ملفًا جديدًا أو معدّلًا" +
                                    (if (st.problems > 0) "، وتعذرت قراءة ${st.problems}." else ".")
                            },
                            color = TextSecondary, fontSize = 12.sp,
                        )
                        TextButton(onClick = vm::dismissIndexStatus) { Text("إخفاء") }
                    }
                }
            }
        }

        item { Text("المجلدات المسموح بها", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (vm.folders.isEmpty()) {
            item { Text("لم تختر أي مجلد بعد.", color = TextSecondary, fontSize = 13.sp) }
        }
        items(vm.folders.toList(), key = { it }) { f ->
            val info = roots.firstOrNull { it.uri == f }
            val on = info != null
            FCard {
                Text(folderLabel(f), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("فهرسة هذا المجلد محليًا", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { vm.setIndexing(f, it) })
                }
                if (info != null) {
                    val kb = (info.bytes + 1023) / 1024
                    Text(
                        "ملفات مفهرسة: ${info.files} • حجم الفهرس: $kb كيلوبايت" +
                            (if (info.indexedAt > 0) " • آخر تحديث: ${CaseReport.formatTime(info.indexedAt)}" else ""),
                        color = TextSecondary, fontSize = 11.sp,
                    )
                    OutlinedButton(
                        onClick = { vm.buildIndex(f) },
                        enabled = status?.running != true,
                    ) { Text("تحديث الفهرس الآن") }
                }
                OutlinedButton(onClick = { vm.removeFolder(f) }) { Text("إزالة المجلد (ويحذف فهرسه)") }
            }
        }
        item { Button(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) { Text("إضافة مجلد") } }
        if (roots.isNotEmpty()) {
            item {
                OutlinedButton(onClick = { confirmDeleteAll = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("حذف كل الفهارس")
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("حذف كل الفهارس؟") },
            text = { Text("يُحذف الفهرس وتُعطَّل الفهرسة لكل المجلدات. تبقى المجلدات المسموح بها كما هي، ويصبح البحث أبطأ حتى تفعّل الفهرسة مجددًا.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllIndexes(); confirmDeleteAll = false }) { Text("حذف", color = Gold400) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun FCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardNavy)
            .border(BorderStroke(1.dp, Outline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
