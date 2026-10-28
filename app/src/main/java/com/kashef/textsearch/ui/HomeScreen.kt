package com.kashef.textsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashef.textsearch.R
import com.kashef.textsearch.ui.theme.Blue400
import com.kashef.textsearch.ui.theme.Blue500
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.Cyan400
import com.kashef.textsearch.ui.theme.Gold400
import com.kashef.textsearch.ui.theme.Navy800
import com.kashef.textsearch.ui.theme.Outline
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

private const val SAMPLE_QUERY = "المشروع يحتاج إلى قاعدة بيانات"

data class ScopeOption(val title: String, val subtitle: String, val icon: ImageVector)

val scopes = listOf(
    ScopeOption("كل الملفات النصية المتاحة", "TXT, MD, CSV, JSON, XML, …", Icons.Outlined.Description),
    ScopeOption("المستندات", "PDF, DOCX, TXT, …", Icons.Outlined.PictureAsPdf),
    ScopeOption("ملفات البرمجة والأكواد", "Kotlin, Java, HTML, JSON, XML, JS, …", Icons.Outlined.Code),
    ScopeOption("الملاحظات والملفات النصية", "TXT, MD, Notes, CSV, …", Icons.Outlined.NoteAlt),
)

@Composable
fun HomeScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: Int,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardNavy)
                .border(BorderStroke(1.dp, Outline.copy(alpha = 0.5f)), RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Search, null, tint = TextPrimary)
                Text("ابحث عن نص", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(27.dp),
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                placeholder = { Text(SAMPLE_QUERY, color = TextSecondary, fontSize = 16.sp) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Cancel, "مسح", tint = TextSecondary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onStart() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue400, unfocusedBorderColor = Blue400.copy(alpha = 0.6f),
                    cursorColor = Cyan400,
                ),
            )

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lightbulb, null, tint = Gold400, modifier = Modifier.size(18.dp))
                Text(
                    "يمكنك البحث عن كلمة أو جملة كاملة داخل محتوى الملفات، وليس في أسماء الملفات.",
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
            Text("البحث الفعلي داخل الملفات غير موصول بعد (المرحلة الثالثة)؛ التدفق الحالي محاكاة.", color = Gold400, fontSize = 11.sp)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.FolderOpen, null, tint = Gold400)
                Text("نطاق البحث", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            scopes.forEachIndexed { i, s -> ScopeRow(s, selected = i == selected) { onSelect(i) } }
        }

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue500, contentColor = Color.White),
        ) {
            Icon(Icons.Outlined.Search, null)
            Spacer(Modifier.size(8.dp))
            Text("بدء البحث", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) // points left = forward in RTL
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // First child sits on the right in RTL: logo + title + tagline.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Navy800)
                        .border(BorderStroke(1.dp, Cyan400), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Description, null, tint = Cyan400, modifier = Modifier.size(30.dp)) }
                Column {
                    Text("كاشف", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("النصوص", color = Cyan400, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text("ابحث عمّا كتبته... حتى لو نسيت أين", color = TextSecondary, fontSize = 14.sp)
        }
        Image(
            painter = painterResource(R.drawable.kashef_character),
            contentDescription = "شخصية كاشف",
            modifier = Modifier.height(210.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ScopeRow(option: ScopeOption, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Blue500.copy(alpha = 0.22f) else Color.Transparent)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Blue400 else Outline.copy(alpha = 0.5f)), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(option.icon, null, tint = Cyan400, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text(option.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(option.subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .border(BorderStroke(2.dp, if (selected) Blue400 else TextSecondary), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(Blue400)) }
    }
}
