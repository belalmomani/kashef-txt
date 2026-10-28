package com.kashef.textsearch.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashef.textsearch.BuildConfig
import com.kashef.textsearch.R
import com.kashef.textsearch.ui.theme.Blue400
import com.kashef.textsearch.ui.theme.Blue500
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Gold400
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary
import kotlinx.coroutines.delay

enum class Stage { Running, Finished, Cancelled, Failed }

/** Kashef's comic lines, grouped by investigation mood. Phase 2: text only (one character image). */
private val runningLines = listOf(
    "همم... القضية تبدو مثيرة. دعني أفكّر قليلًا." to 0.15f,
    "أبحث عن الأدلة بين الملفات... (محاكاة)" to 0.4f,
    "هذا الملف يبدو مشبوهًا، سأنظر إليه عن قرب." to 0.65f,
    "انتظر... وجدت خيطًا! أو ربما هو خيط معطفي." to 0.85f,
    "أُجهّز تقرير القضية..." to 1f,
)
private const val finishedLine = "أعلن انتهاء المحاكاة! المحرك الحقيقي لم يُركَّب بعد، فلا نتائج فعلية الآن."
private const val cancelledLine = "أوقفتَ التحقيق. لا بأس، القضية لن تهرب."
private const val failedLine = "عذرًا، تعثّر التحقيق. هذه حالة خطأ للتجربة فقط."

@Composable
fun InvestigationScreen(query: String, scopeTitle: String, onBack: () -> Unit) {
    var runId by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf(Stage.Running) }
    var line by remember { mutableStateOf(runningLines.first().first) }
    var progress by remember { mutableFloatStateOf(0f) }

    BackHandler { onBack() }

    // Cancelling = changing `stage`, which cancels this coroutine automatically.
    LaunchedEffect(runId, stage) {
        if (stage != Stage.Running) return@LaunchedEffect
        progress = 0f
        for ((text, p) in runningLines) {
            line = text
            progress = p
            delay(1300)
        }
        line = finishedLine
        stage = Stage.Finished
    }

    fun restart() { runId += 1; stage = Stage.Running; line = runningLines.first().first }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Character + speech bubble
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(CardNavy)
                    .border(BorderStroke(1.dp, Outline), RoundedCornerShape(20.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("كاشف", color = Cyan400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (stage) {
                        Stage.Running, Stage.Finished -> line
                        Stage.Cancelled -> cancelledLine
                        Stage.Failed -> failedLine
                    },
                    color = TextPrimary, fontSize = 15.sp,
                )
            }
            Image(
                painterResource(R.drawable.kashef_character), "كاشف",
                Modifier.height(150.dp), contentScale = ContentScale.Fit,
            )
        }

        InfoCard {
            Text("القضية", color = TextSecondary, fontSize = 12.sp)
            Text("«$query»", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("النطاق: $scopeTitle", color = TextSecondary, fontSize = 13.sp)
        }

        when (stage) {
            Stage.Running -> {
                InfoCard {
                    LinearProgressIndicator(
                        progress = { progress }, modifier = Modifier.fillMaxWidth(),
                        color = Blue400, trackColor = Outline,
                    )
                    Text("محاكاة لتجربة الواجهة فقط. لا يجري فحص أي ملف.", color = Gold400, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { stage = Stage.Cancelled }, modifier = Modifier.fillMaxWidth()) {
                    Text("إيقاف التحقيق")
                }
                if (BuildConfig.DEBUG) {
                    OutlinedButton(onClick = { stage = Stage.Failed }, modifier = Modifier.fillMaxWidth()) {
                        Text("(اختبار) محاكاة خطأ")
                    }
                }
            }
            Stage.Finished -> {
                NotExecutedCard()
                SampleResultCard(query)
                Actions(onBack, ::restart)
            }
            Stage.Cancelled -> {
                InfoCard {
                    Text("أُوقف التحقيق قبل اكتماله.", color = TextPrimary, fontSize = 15.sp)
                    Text("الملفات المفحوصة: 0 — النتائج: 0", color = TextSecondary, fontSize = 13.sp)
                }
                Actions(onBack, ::restart)
            }
            Stage.Failed -> {
                InfoCard {
                    Text("حدث خطأ أثناء التحقيق (حالة تجريبية).", color = Color(0xFFFF8A80), fontSize = 15.sp)
                    Text("في المحرك الحقيقي سنعرض هنا سبب الخطأ والملف المتأثر.", color = TextSecondary, fontSize = 13.sp)
                }
                Actions(onBack, ::restart)
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardNavy)
            .border(BorderStroke(1.dp, Outline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun NotExecutedCard() = InfoCard {
    Text("لم يُنفَّذ بحث فعلي", color = Gold400, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Text("الملفات المفحوصة فعلًا: 0", color = TextPrimary, fontSize = 14.sp)
    Text("الملفات المطابقة: 0", color = TextPrimary, fontSize = 14.sp)
    Text(
        "محرك البحث داخل المحتوى سيُبنى في المرحلة الثالثة. ما تراه الآن هو تجربة للواجهة فقط.",
        color = TextSecondary, fontSize = 12.sp,
    )
}

/** Clearly labelled mock card showing how a real result will look. It is NOT a real result. */
@Composable
private fun SampleResultCard(query: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Gold400.copy(alpha = 0.7f)), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("مثال توضيحي لشكل النتيجة — ليس نتيجة حقيقية", color = Gold400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("اسم_الملف.txt", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("المسار: (سيظهر موقع الملف هنا) • السطر: —", color = TextSecondary, fontSize = 12.sp)
        Text("… مقتطف نصي يحتوي «$query» سيظهر هنا …", color = TextPrimary.copy(alpha = 0.85f), fontSize = 13.sp)
    }
}

@Composable
private fun Actions(onNew: () -> Unit, onRetry: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onNew, modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue500),
        ) { Text("بحث جديد") }
        OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f).height(48.dp)) { Text("إعادة المحاكاة") }
    }
    Spacer(Modifier.size(8.dp))
}
