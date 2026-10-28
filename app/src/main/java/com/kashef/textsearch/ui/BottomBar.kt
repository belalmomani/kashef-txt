package com.kashef.textsearch.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.kashef.textsearch.ui.theme.Blue500
import com.kashef.textsearch.ui.theme.CardNavy
import com.kashef.textsearch.ui.theme.TextPrimary
import com.kashef.textsearch.ui.theme.TextSecondary

/** Order matches the approved mockup (right-to-left): الإعدادات، الملفات، البحث، الذاكرة، الحماية. */
enum class BottomTab(val label: String, val icon: ImageVector) {
    Settings("الإعدادات", Icons.Outlined.Settings),
    Files("الملفات", Icons.Outlined.FolderOpen),
    Search("بحث", Icons.Outlined.Search),
    Memory("الذاكرة", Icons.Outlined.History),
    Protection("الحماية", Icons.Outlined.Security),
}

@Composable
fun KashefBottomBar(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    NavigationBar(modifier = Modifier.fillMaxWidth(), containerColor = CardNavy) {
        BottomTab.entries.forEach { t ->
            NavigationBarItem(
                selected = selected == t,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, contentDescription = t.label) },
                label = { Text(t.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TextPrimary,
                    selectedTextColor = TextPrimary,
                    indicatorColor = Blue500,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                ),
            )
        }
    }
}
