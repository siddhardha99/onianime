package com.onianime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.onianime.ui.theme.Oni
import com.onianime.ui.theme.focusCard

@Composable
fun DetailScreen(vm: AppViewModel) {
    val media = vm.detailMedia ?: return
    Box(Modifier.fillMaxSize().background(Oni.Bg)) {
        // Banner
        AsyncImage(
            model = media.bannerImage ?: media.coverImage,
            contentDescription = media.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(540.dp).background(parseColor(media.coverColor)),
        )
        Box(Modifier.fillMaxWidth().height(540.dp).background(Brush.horizontalGradient(0f to Oni.Bg.copy(alpha = 0.97f), 0.56f to Oni.Bg.copy(alpha = 0.52f), 1f to Oni.Bg.copy(alpha = 0.18f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.14f to Oni.Bg, 0.52f to Color.Transparent)))

        Column(Modifier.padding(start = 56.dp, top = 44.dp, end = 56.dp).fillMaxWidth()) {
            Text(media.genres.take(3).joinToString("  •  ").uppercase(), color = Oni.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text(media.displayTitle, color = Oni.TextHi, fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 920.dp))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                media.seasonYear?.let { Chip("$it") }
                media.scoreOutOfTen?.let { Chip("★ ${"%.1f".format(it)}", accent = Oni.Green) }
                media.episodes?.let { Chip("$it Episodes") }
                Chip(media.statusLabel)
                SubDubToggle(vm)
            }
            Spacer(Modifier.height(18.dp))
            media.plainDescription?.let {
                Text(it, color = Oni.Text2, fontSize = 18.sp, maxLines = 3, modifier = Modifier.widthIn(max = 760.dp))
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(if (vm.episodes.isEmpty()) "▶  Play" else "▶  Play Ep ${vm.episodes.first()}") {
                    if (vm.episodes.isNotEmpty()) vm.playEpisode(0)
                }
                GhostButton("⚙  Fix match") { vm.toast = "Fix match — manual override coming in a later phase" }
                if (vm.detailStatus.isNotEmpty()) Text(vm.detailStatus, color = Oni.Muted, fontSize = 15.sp)
            }
        }

        // Episodes rail
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 30.dp)) {
            Text("Episodes", color = Oni.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 56.dp, bottom = 14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(start = 56.dp, end = 40.dp)) {
                itemsIndexed(vm.episodes, key = { _, ep -> ep }) { index, ep ->
                    EpisodeCard(epLabel = ep, seedColor = parseColor(media.coverColor), thumb = media.coverImage, onClick = { vm.playEpisode(index) })
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, accent: Color = Oni.Text) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Oni.SurfaceStrong, RoundedCornerShape(8.dp)).background(Oni.Surface).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(text, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SubDubToggle(vm: AppViewModel) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.onFocusChanged { focused = it.isFocused }.clickable { vm.toggleMode() }
            .clip(RoundedCornerShape(8.dp)).focusCard(focused, radius = 8.dp)
            .border(1.dp, if (focused) Oni.White else Oni.SurfaceStrong, RoundedCornerShape(8.dp)),
    ) {
        listOf("sub", "dub").forEach { m ->
            val active = vm.mode == m
            Box(Modifier.background(if (active) Oni.Accent else Color.Transparent).padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(m.uppercase(), color = if (active) Oni.White else Oni.Muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.onFocusChanged { focused = it.isFocused }.clickable { onClick() }
            .clip(RoundedCornerShape(11.dp)).focusCard(focused, radius = 11.dp)
            .background(Oni.White).padding(horizontal = 34.dp, vertical = 15.dp),
    ) {
        Text(text, color = Oni.Bg, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.onFocusChanged { focused = it.isFocused }.clickable { onClick() }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Oni.Accent.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Text(text, color = if (focused) Oni.White else Oni.Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EpisodeCard(epLabel: String, seedColor: Color, thumb: String?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(260.dp)) {
        Box(
            Modifier.size(260.dp, 146.dp).onFocusChanged { focused = it.isFocused }.clickable { onClick() }
                .focusCard(focused, radius = 10.dp).clip(RoundedCornerShape(10.dp)).background(seedColor),
        ) {
            AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
            Text("$epLabel", color = Oni.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopStart).padding(start = 11.dp, top = 7.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text("Episode $epLabel", color = Oni.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
