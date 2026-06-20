package com.onianime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
                // Adaptive: as many columns as fit (≈3 on a TV, more on wider screens).
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 40.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(current, key = { it }) { globalIndex ->
                    EpisodeGridCard(
                        epLabel = vm.episodes[globalIndex],
                        runtimeMin = media.duration,
                        fraction = vm.episodeFraction(media.id, globalIndex),
                        onClick = { vm.playEpisode(globalIndex) },
                        onLongClick = { vm.toggleEpisodeWatched(globalIndex) },
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
private fun EpisodeGridCard(epLabel: String, runtimeMin: Int?, fraction: Float, onClick: () -> Unit, onLongClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var downAt by remember { mutableLongStateOf(0L) }
    val watched = fraction >= 0.92f
    val watching = fraction > 0f && !watched
    val accent = when { watched -> Oni.Green; watching -> Oni.Accent; else -> Oni.Muted }
    val statusText = when {
        watched -> "Watched"
        watching -> "${(fraction * 100).toInt()}% — resume"
        else -> "Unwatched"
    }

    Box(
        Modifier.fillMaxWidth().height(96.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!select) return@onKeyEvent false
                when (e.type) {
                    // Hold ≥ 450ms = mark watched/unwatched; a short tap = play.
                    KeyEventType.KeyDown -> { if (downAt == 0L) downAt = System.currentTimeMillis(); true }
                    KeyEventType.KeyUp -> {
                        val held = System.currentTimeMillis() - downAt
                        downAt = 0L
                        if (held >= 450) onLongClick() else onClick()
                        true
                    }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .focusCard(focused, radius = 12.dp)
            .background(if (focused) Color(0x26FFFFFF) else Color(0x12FFFFFF))
            .border(1.dp, if (focused) Oni.White else Color(0x20FFFFFF), RoundedCornerShape(12.dp)),
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = if (watched || watching) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(epLabel, color = if (watched || watching) accent else Oni.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Episode $epLabel", color = Oni.TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(statusText, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    runtimeMin?.let { Text("· ${it}m", color = Oni.Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }
            }
            Text(
                if (watched) "✓" else if (focused) "▶" else "",
                color = if (watched) Oni.Green else Oni.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (watching) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Oni.Accent))
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
