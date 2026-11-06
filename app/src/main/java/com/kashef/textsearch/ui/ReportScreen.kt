package com.kashef.textsearch.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashef.textsearch.engine.CaseRecord
import com.kashef.textsearch.engine.CaseReport
import com.kashef.textsearch.ui.theme.Blue500
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

/** Case report: states only what the app actually knows about one investigation. */
@Composable
fun ReportScreen(r: CaseRecord, onBack: () -> Unit, onShare: () -> Unit) {
    BackHandler { onBack() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("تقرير القضية", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        items(CaseReport.sections(r)) { sec ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardNavy)
                    .border(BorderStroke(1.dp, Outline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(sec.title, color = Cyan400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                sec.lines.forEach { Text("• $it", color = TextPrimary, fontSize = 13.sp) }
            }
        }
        item {
            Text(
                "هذا التقرير لا يحتوي محتوى الملفات ولا أسماءها.",
                color = TextSecondary, fontSize = 11.sp,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onBack, modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue500),
                ) { Text("رجوع") }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).height(48.dp)) { Text("مشاركة التقرير") }
            }
        }
    }
}
