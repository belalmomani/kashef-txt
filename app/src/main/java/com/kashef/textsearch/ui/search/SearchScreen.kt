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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
fun SearchScreen(ui: ScanUi, onStop: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onBack, modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue500),
                    ) { Text("بحث جديد") }
                    OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f).height(48.dp)) { Text("إعادة البحث") }
                }
            }
        }
        items(s.results) { ResultCard(it) }
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
        if (s.unsupportedDocs > 0) {
            Text("ملفات لم تُفحص لأن نوعها غير مدعوم بعد (PDF وDOCX وغيرهما): ${s.unsupportedDocs}", color = Gold400, fontSize = 12.sp)
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

@Composable
private fun ResultCard(r: FileResult) {
    Card {
        Text(r.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(r.path, color = TextSecondary, fontSize = 11.sp)
        val more = if (r.truncated) "+" else ""
        Text("عدد الأسطر المطابقة: ${r.matches.size}$more", color = Cyan400, fontSize = 12.sp)
        r.matches.take(3).forEach { MatchRow(it) }
        if (r.matches.size > 3) Text("… و${r.matches.size - 3} أسطر أخرى", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun MatchRow(m: MatchLine) {
    val text = buildAnnotatedString {
        append(m.snippet)
        m.ranges.forEach {
            val end = (it.last + 1).coerceAtMost(m.snippet.length)
            if (it.first in 0 until end) {
                addStyle(SpanStyle(background = Gold400.copy(alpha = 0.35f), fontWeight = FontWeight.Bold), it.first, end)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("سطر ${m.line}", color = TextSecondary, fontSize = 11.sp)
        Text(text, color = TextPrimary.copy(alpha = 0.9f), fontSize = 13.sp)
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
