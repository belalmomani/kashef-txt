package com.kashef.textsearch.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashef.textsearch.engine.LineMatcher
import com.kashef.textsearch.ui.theme.Blue500
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Gold400
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

/** Shows the lines around one match, read from the file itself, with the search words highlighted. */
@Composable
fun ContextScreen(v: ViewerUi, onBack: () -> Unit, onOpenExternal: () -> Unit) {
    BackHandler { onBack() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardNavy)
                    .border(BorderStroke(1.dp, Outline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(v.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(v.path, color = TextSecondary, fontSize = 11.sp)
                Text("السطر ${v.line}", color = Cyan400, fontSize = 12.sp)
            }
        }
        if (v.loading) {
            item { Text("جارٍ قراءة الملف…", color = TextSecondary, fontSize = 13.sp) }
        } else if (v.error.isNotEmpty()) {
            item { Text(v.error, color = Gold400, fontSize = 13.sp) }
        } else {
            items(v.lines) { (n, text) ->
                val target = n == v.line
                val styled = buildAnnotatedString {
                    append(text)
                    val q = v.query
                    if (q != null) {
                        LineMatcher.ranges(text, q).forEach {
                            val end = (it.last + 1).coerceAtMost(text.length)
                            if (it.first in 0 until end) {
                                addStyle(
                                    SpanStyle(background = Gold400.copy(alpha = 0.4f), fontWeight = FontWeight.Bold),
                                    it.first, end,
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (target) Blue500.copy(alpha = 0.25f) else CardNavy)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        n.toString(), color = if (target) Gold400 else TextSecondary, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.width(34.dp),
                    )
                    Text(styled, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "التطبيقات الخارجية لا تنتقل عادةً إلى سطر محدد؛ لذلك يعرض كاشف السياق هنا.",
                    color = TextSecondary, fontSize = 11.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onBack, modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue500),
                    ) { Text("رجوع للنتائج") }
                    OutlinedButton(onClick = onOpenExternal, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("فتح الملف")
                    }
                }
            }
        }
    }
}
