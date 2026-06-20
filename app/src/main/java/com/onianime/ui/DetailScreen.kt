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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

private const val RANGE_SIZE = 100

@Composable
fun DetailScreen(vm: AppViewModel) {
    val media = vm.detailMedia ?: return
    val progress = vm.progressFor(media.id)

    val ranges = remember(vm.episodes.size) { vm.episodes.indices.chunked(RANGE_SIZE) }
    var rangeIndex by remember(media.id, vm.episodes.size) {
        mutableStateOf(((progress?.lastEpisodeIndex ?: 0) / RANGE_SIZE).coerceIn(0, (ranges.size - 1).coerceAtLeast(0)))
    }

    Box(Modifier.fillMaxSize().background(Oni.Bg)) {
        // Faint banner backdrop
        AsyncImage(
            model = media.bannerImage ?: media.coverImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Oni.Bg, 0.42f to Oni.Bg.copy(alpha = 0.9f), 1f to Oni.Bg.copy(alpha = 0.97f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Oni.Bg.copy(alpha = 0.5f), 0.45f to Color.Transparent, 1f to Oni.Bg.copy(alpha = 0.82f))))

        Row(Modifier.fillMaxSize().padding(top = 20.dp, bottom = 24.dp)) {
            // LEFT: info + range list
            Column(Modifier.width(660.dp).fillMaxHeight().padding(start = 56.dp, end = 32.dp)) {
                Text(media.genres.take(3).joinToString("  •  ").uppercase(), color = Oni.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Text(media.displayTitle, color = Oni.TextHi, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    media.seasonYear?.let { Chip("$it") }
                    media.scoreOutOfTen?.let { Chip("★ ${"%.1f".format(it)}", accent = Oni.Green) }
                    media.episodes?.let { Chip("$it eps") }
                    Chip(media.statusLabel)
                    SubDubToggle(vm)
                }
                Spacer(Modifier.height(14.dp))
                media.plainDescription?.let {
                    Text(it, color = Oni.Text2, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val resumeLabel = when {
                        progress != null -> "▶  Resume Ep ${progress.lastEpisodeLabel}"
                        vm.episodes.isNotEmpty() -> "▶  Play Ep ${vm.episodes.first()}"
                        else -> "▶  Play"
                    }
                    PrimaryButton(resumeLabel) {
                        if (vm.episodes.isNotEmpty()) vm.playEpisode(progress?.lastEpisodeIndex ?: 0)
                    }
                    val inList = vm.isInMyList(media.id)
                    ListButton(if (inList) "✓  My List" else "＋  My List", inList) { vm.toggleMyList() }
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("⚙  Fix match") { vm.toast = "Fix match — manual override coming later" }

                Spacer(Modifier.height(20.dp))
                Text(if (vm.episodes.isEmpty()) (vm.detailStatus.ifEmpty { "Loading…" }) else "Episodes", color = Oni.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (ranges.size > 1) {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(ranges) { i, idx ->
                            val first = vm.episodes[idx.first()]
                            val last = vm.episodes[idx.last()]
                            RangeRow(if (first == last) "Episode $first" else "Episodes $first–$last", selected = i == rangeIndex) { rangeIndex = i }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            // RIGHT: 2-column episode grid for the selected range
            val current = ranges.getOrNull(rangeIndex) ?: vm.episodes.indices.toList()
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 40.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(current, key = { it }) { globalIndex ->
                    EpisodeGridCard(
                        epLabel = vm.episodes[globalIndex],
                        seedColor = parseColor(media.coverColor),
                        thumb = media.coverImage,
                        runtimeMin = media.duration,
                        fraction = vm.episodeFraction(media.id, globalIndex),
                        onClick = { vm.playEpisode(globalIndex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, accent: Color = Oni.Text) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Oni.SurfaceStrong, RoundedCornerShape(8.dp)).background(Oni.Surface).padding(horizontal = 13.dp, vertical = 6.dp)) {
        Text(text, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RangeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.clickable { onClick() }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Oni.Accent else if (selected) Oni.Surface else Color.Transparent)
            .border(1.dp, if (selected || focused) Oni.SurfaceStrong else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = when { focused -> Oni.White; selected -> Oni.TextHi; else -> Oni.Muted },
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun EpisodeGridCard(epLabel: String, seedColor: Color, thumb: String?, runtimeMin: Int?, fraction: Float, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.onFocusChanged { focused = it.isFocused }.clickable { onClick() }.padding(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).focusCard(focused, radius = 10.dp).clip(RoundedCornerShape(10.dp)).background(seedColor),
        ) {
            AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f))))
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xB30A0A0F)).padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text("$epLabel", color = Oni.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (focused) {
                Box(
                    Modifier.align(Alignment.Center).size(50.dp).clip(RoundedCornerShape(25.dp)).background(Color(0x99000000)),
                    contentAlignment = Alignment.Center,
                ) { Text("▶", color = Oni.White, fontSize = 20.sp) }
            }
            if (fraction > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(Oni.Accent))
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text("Episode $epLabel", color = Oni.TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            runtimeMin?.let { Text("≈ $it min", color = Oni.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            val status = when {
                fraction >= 0.92f -> "✓ Watched"
                fraction > 0f -> "${(fraction * 100).toInt()}% — resume"
                else -> ""
            }
            if (status.isNotEmpty()) {
                Text(status, color = if (fraction >= 0.92f) Oni.Green else Oni.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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
            Box(Modifier.background(if (active) Oni.Accent else Color.Transparent).padding(horizontal = 13.dp, vertical = 6.dp)) {
                Text(m.uppercase(), color = if (active) Oni.White else Oni.Muted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
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
            .background(Oni.White).padding(horizontal = 30.dp, vertical = 14.dp),
    ) {
        Text(text, color = Oni.Bg, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ListButton(text: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.onFocusChanged { focused = it.isFocused }.clickable { onClick() }
            .clip(RoundedCornerShape(11.dp)).focusCard(focused, radius = 11.dp)
            .background(if (active) Oni.Accent.copy(alpha = 0.85f) else Oni.SurfaceStrong)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(text, color = Oni.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.onFocusChanged { focused = it.isFocused }.clickable { onClick() }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Oni.Accent.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (focused) Oni.White else Oni.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
