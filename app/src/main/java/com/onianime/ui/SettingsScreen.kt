package com.onianime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.onianime.ui.theme.Oni

private val Glass = Color(0x12FFFFFF)
private val GlassFocused = Color(0x26FFFFFF)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    Column(Modifier.fillMaxSize().background(Oni.Bg).padding(start = 56.dp, end = 56.dp, top = 24.dp)) {
        Text("Settings", color = Oni.TextHi, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Select a row to change it", color = Oni.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(26.dp))

        Column(Modifier.widthIn(max = 880.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingRow("🔊", "Default audio", s.defaultMode.uppercase(), "What to play by default — subtitled or dubbed") {
                vm.updateSettings(s.copy(defaultMode = if (s.defaultMode == "sub") "dub" else "sub"))
            }
            SettingRow("✦", "Preferred quality", qualityLabel(s.preferredQuality), "Pick this resolution when available") {
                vm.updateSettings(s.copy(preferredQuality = nextQuality(s.preferredQuality)))
            }
            SettingRow("⏭", "Auto-skip intro / outro", if (s.autoSkip) "On" else "Off", "Skip openings & endings automatically (AniSkip)", toggle = s.autoSkip) {
                vm.updateSettings(s.copy(autoSkip = !s.autoSkip))
            }
            SettingRow("▶", "Autoplay next episode", if (s.autoPlayNext) "On" else "Off", "Continue to the next episode automatically", toggle = s.autoPlayNext) {
                vm.updateSettings(s.copy(autoPlayNext = !s.autoPlayNext))
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: String,
    label: String,
    value: String,
    hint: String,
    toggle: Boolean? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(78.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) GlassFocused else Glass)
            .border(2.dp, if (focused) Oni.Accent else Color.Transparent, RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar on focus
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (focused) Oni.Accent else Color.Transparent))
        Spacer(Modifier.width(18.dp))
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1AFFFFFF)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 20.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Oni.TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(hint, color = Oni.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        // Value: an On/Off pill (toggle) or a value chip
        val pillColor = when (toggle) {
            true -> Oni.Accent
            false -> Color(0x2EFFFFFF)
            null -> Oni.Accent
        }
        Box(
            Modifier.padding(end = 18.dp).clip(RoundedCornerShape(9.dp)).background(pillColor).padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Text(value, color = if (toggle == false) Oni.Muted else Oni.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun qualityLabel(q: String): String = when (q) {
    "best" -> "Best"
    "worst" -> "Lowest"
    else -> "${q}p"
}

private fun nextQuality(q: String): String = when (q) {
    "best" -> "1080"
    "1080" -> "720"
    "720" -> "480"
    "480" -> "worst"
    else -> "best"
}
