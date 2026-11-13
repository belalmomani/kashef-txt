package com.kashef.textsearch.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashef.textsearch.R
import com.kashef.textsearch.engine.FileResult
import com.kashef.textsearch.engine.Filters
import com.kashef.textsearch.engine.ResultOps
import com.kashef.textsearch.engine.SortMode
import com.kashef.textsearch.engine.MatchLine
import com.kashef.textsearch.engine.ScanAccumulator
import com.kashef.textsearch.ui.theme.Blue400
import com.kashef.textsearch.ui.theme.Blue500
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Gold400
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val runningLines = listOf(
    "همم... القضية تبدو مثيرة. أبدأ الفحص.",
    "أفتّش بين الملفات بعدستي...",
    "هذا الملف يبدو مشبوهًا، لكنه ربما بريء.",
    "لا تقلق، لن أخبر أحدًا بما كتبته في ملفاتك.",
    "أتابع الأدلة سطرًا سطرًا...",
)

fun dialogue(ui: ScanUi): String {
    val s = ui.snap
    return when (ui.stage) {
        ScanStage.Running -> runningLines[(s.scanned / 15) % runningLines.size]
        ScanStage.Finished -> when {
            s.matchedFiles > 0 -> "وجدت العبارة في ${s.matchedFiles} ملف! القضية تتحرك."
            else -> "أعتذر، لم أجد العبارة في الملفات التي فحصتها."
        }
        ScanStage.Cancelled -> "أوقفتَ التحقيق. ما وجدته حتى الآن معروض أمامك."
        ScanStage.Failed -> "تعثّر التحقيق: ${ui.message}"
    }
}

@Composable
fun SearchScreen(
    ui: ScanUi,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenContext: (FileResult, Int) -> Unit,
    onOpenExternal: (FileResult) -> Unit,
    onReport: () -> Unit,
    onShareText: (String, String) -> Unit,
) {
    BackHandler { onBack() }
    val s = ui.snap
    val running = ui.stage == ScanStage.Running

    val transition = rememberInfiniteTransition(label = "float")
    val wave by transition.animateFloat(
        initialValue = -7f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave",
    )
    val tilt by animateFloatAsState(if (ui.stage == ScanStage.Finished && s.matchedFiles > 0) -6f else 0f, label = "tilt")
    val density = LocalDensity.current.density
    val floatY = if (running) wave else 0f

    var sort by remember { mutableStateOf(SortMode.Relevance) }
    var exts by remember { mutableStateOf(setOf<String>()) }
    var roots by remember { mutableStateOf(setOf<String>()) }
    var days by remember { mutableStateOf(0) }
    var narrow by remember { mutableStateOf("") }
    val shown = remember(s.results, sort, exts, roots, days, narrow) {
        val since = if (days > 0) System.currentTimeMillis() - days * 86_400_000L else 0L
        ResultOps.apply(s.results, Filters(exts, roots, since, narrow), sort)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.kashef_full), "كاشف",
                    Modifier.height(280.dp).graphicsLayer {
                        translationY = floatY * density
                        rotationZ = tilt
                    },
                    contentScale = ContentScale.Fit,
                )
                Card {
                    Text("كاشف", color = Cyan400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(dialogue(ui), color = TextPrimary, fontSize = 15.sp)
                }
            }
        }
        item {
            Card {
                Text("القضية", color = TextSecondary, fontSize = 12.sp)
                Text("«${ui.query}»", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("النطاق: ${ui.scopeTitle} • الأسلوب: ${ui.modeLabel}", color = TextSecondary, fontSize = 13.sp)
            }
        }
        item { StatusCard(ui) }
        item {
            if (running) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue400, trackColor = Outline)
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("إيقاف التحقيق") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onBack, modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue500),
                        ) { Text("بحث جديد") }
                        OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f).height(48.dp)) { Text("إعادة البحث") }
                    }
                    OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("تقرير القضية") }
                }
            }
        }
        if (s.results.isNotEmpty()) {
            item {
                FiltersCard(
                    results = s.results,
                    sort = sort, onSort = { sort = it },
                    exts = exts, onExts = { exts = it },
                    roots = roots, onRoots = { roots = it },
                    days = days, onDays = { days = it },
                    narrow = narrow, onNarrow = { narrow = it },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "طابور المشتبه بهم (${shown.size} من ${s.results.size})",
                            color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { onShareText("مشاركة النتائج", ResultOps.toText(shown, ui.query)) },
                            enabled = shown.isNotEmpty(),
                        ) { Text("مشاركة") }
                    }
                    Text("المشاركة تتضمن مقتطفات من ملفاتك؛ اختر وجهتها بعناية.", color = TextSecondary, fontSize = 10.sp)
                }
            }
            itemsIndexed(shown, key = { _, r -> r.uri.ifEmpty { r.path } }) { i, r ->
                ResultCard(i + 1, r, onOpenContext, onOpenExternal)
            }
            if (shown.isEmpty()) {
                item { Card { Text("لا توجد نتائج تطابق الفلاتر الحالية.", color = TextSecondary, fontSize = 13.sp) } }
            }
        }
        if (s.problems.isNotEmpty()) {
            item {
                Card {
                    Text("ملفات تعذرت قراءتها (${s.problemsTotal})", color = Gold400, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    s.problems.take(20).forEach {
                        Text("${it.path}\n${it.reason}", color = TextSecondary, fontSize = 12.sp)
                    }
                    if (s.problemsTotal > 20) Text("… وغيرها", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(ui: ScanUi) {
    val s = ui.snap
    Card {
        Text("الملفات المفحوصة فعلًا: ${s.scanned}", color = TextPrimary, fontSize = 14.sp)
        Text("الملفات المطابقة: ${s.matchedFiles} (إجمالي المطابقات: ${s.matchCount})", color = TextPrimary, fontSize = 14.sp)
        if (ui.stage == ScanStage.Running && s.current.isNotEmpty()) {
            Text("أفحص الآن: ${s.current}", color = TextSecondary, fontSize = 12.sp)
        }
        if (s.skippedByIndex > 0) {
            Text(
                "استُبعد ${s.skippedByIndex} ملفًا عبر الفهرس المحلي دون قراءته (لم يتغير ولا يحتوي عبارتك).",
                color = Cyan400, fontSize = 12.sp,
            )
        }
        if (s.unsupportedDocs > 0) {
            Text("ملفات لم تُفحص لأن نوعها غير مدعوم بعد (PDF وDOC وXLS وPPT وغيرها): ${s.unsupportedDocs}", color = Gold400, fontSize = 12.sp)
        }
        if (s.problemsTotal > 0) {
            Text("ملفات تعذرت قراءتها: ${s.problemsTotal}", color = Gold400, fontSize = 12.sp)
        }
        when {
            s.limitReached -> Text(
                "توقف البحث عند حد ${ScanAccumulator.MAX_TOTAL_MATCHES} مطابقة؛ قد توجد نتائج أخرى.",
                color = Gold400, fontSize = 12.sp,
            )
            ui.stage == ScanStage.Finished -> Text(
                "اكتمل المرور على المجلدات المختارة (للأنواع المدعومة فقط).",
                color = TextSecondary, fontSize = 12.sp,
            )
            ui.stage == ScanStage.Cancelled -> Text("أُوقف البحث قبل اكتمال المرور؛ النتائج جزئية.", color = Gold400, fontSize = 12.sp)
        }
        if (ui.stage != ScanStage.Running) {
            Text("الوقت: ${"%.1f".format(s.elapsedMs / 1000.0)} ثانية", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

private fun formatDate(ms: Long): String = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(ms))

@Composable
private fun ResultCard(
    rank: Int,
    r: FileResult,
    onOpenContext: (FileResult, Int) -> Unit,
    onOpenExternal: (FileResult) -> Unit,
) {
    var expanded by remember(r.path) { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#$rank", color = Gold400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                r.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        Text(r.path, color = TextSecondary, fontSize = 11.sp)
        val more = if (r.truncated) "+" else ""
        val parts = ArrayList<String>()
        parts.add("الأسطر المطابقة: ${r.matches.size}$more")
        if (r.termsTotal > 1) parts.add("الكلمات: ${r.termsMatched} من ${r.termsTotal}")
        if (r.modified > 0) parts.add("التعديل: ${formatDate(r.modified)}")
        Text(parts.joinToString(" • "), color = Cyan400, fontSize = 12.sp)
        val list = if (expanded) r.matches else r.matches.take(3)
        list.forEach { m -> MatchRow(m) { onOpenContext(r, m.line) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onOpenExternal(r) }) { Text("فتح الملف") }
            if (r.matches.size > 3) {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "إخفاء" else "كل المطابقات (${r.matches.size})")
                }
            }
        }
    }
}

@Composable
private fun MatchRow(m: MatchLine, onClick: () -> Unit) {
    val text = buildAnnotatedString {
        append(m.snippet)
        m.ranges.forEach {
            val end = (it.last + 1).coerceAtMost(m.snippet.length)
            if (it.first in 0 until end) {
                addStyle(SpanStyle(background = Gold400.copy(alpha = 0.35f), fontWeight = FontWeight.Bold), it.first, end)
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("سطر ${m.line} — اضغط لعرض السياق", color = TextSecondary, fontSize = 11.sp)
        Text(text, color = TextPrimary.copy(alpha = 0.9f), fontSize = 13.sp)
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun FilterChipBtn(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        label,
        color = TextPrimary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(shape)
            .background(if (selected) Blue500.copy(alpha = 0.3f) else CardNavy)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Blue400 else Outline.copy(alpha = 0.5f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun FiltersCard(
    results: List<FileResult>,
    sort: SortMode, onSort: (SortMode) -> Unit,
    exts: Set<String>, onExts: (Set<String>) -> Unit,
    roots: Set<String>, onRoots: (Set<String>) -> Unit,
    days: Int, onDays: (Int) -> Unit,
    narrow: String, onNarrow: (String) -> Unit,
) {
    val extList = results.map { it.ext }.filter { it.isNotEmpty() }.distinct().sorted().take(10)
    val rootList = results.map { it.root }.filter { it.isNotEmpty() }.distinct().sorted()
    val hasDates = results.any { it.modified > 0 }
    Card {
        Text("ترتيب وتصفية النتائج", color = Cyan400, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = narrow,
            onValueChange = onNarrow,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ضيّق النتائج بكلمة (اسم الملف أو المقتطف)") },
        )
        ChipRow {
            FilterChipBtn("الأفضل تطابقًا", sort == SortMode.Relevance) { onSort(SortMode.Relevance) }
            FilterChipBtn("الأحدث", sort == SortMode.Newest) { onSort(SortMode.Newest) }
            FilterChipBtn("الاسم", sort == SortMode.Name) { onSort(SortMode.Name) }
        }
        if (sort == SortMode.Relevance) {
            Text(
                "الترتيب حسب: عدد كلمات البحث الموجودة في الملف، ثم عدد الأسطر المطابقة، ثم الأحدث. لا توجد نسبة مئوية مخترعة.",
                color = TextSecondary, fontSize = 11.sp,
            )
        }
        if (extList.size > 1) {
            Text("النوع", color = TextSecondary, fontSize = 12.sp)
            ChipRow {
                FilterChipBtn("الكل", exts.isEmpty()) { onExts(emptySet()) }
                extList.forEach { e -> FilterChipBtn(".$e", e in exts) { onExts(if (e in exts) exts - e else exts + e) } }
            }
        }
        if (rootList.size > 1) {
            Text("المجلد", color = TextSecondary, fontSize = 12.sp)
            ChipRow {
                FilterChipBtn("الكل", roots.isEmpty()) { onRoots(emptySet()) }
                rootList.forEach { r -> FilterChipBtn(r, r in roots) { onRoots(if (r in roots) roots - r else roots + r) } }
            }
        }
        if (hasDates) {
            Text("تاريخ التعديل", color = TextSecondary, fontSize = 12.sp)
            ChipRow {
                listOf(0 to "كل التواريخ", 7 to "آخر 7 أيام", 30 to "آخر 30 يومًا", 365 to "آخر سنة").forEach { (d, l) ->
                    FilterChipBtn(l, days == d) { onDays(d) }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardNavy)
            .border(BorderStroke(1.dp, Outline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}
