package com.onianime.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.onianime.ui.AppViewModel
import com.onianime.ui.theme.Oni
import com.onianime.ui.theme.focusCard
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, userAgent: String) {
    val stream = vm.playerStream
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (stream == null) {
            Text(vm.playerStatus.ifEmpty { "Loading…" }, color = Oni.Text, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        val context = LocalContext.current
        var position by remember(stream.url) { mutableLongStateOf(0L) }
        var duration by remember(stream.url) { mutableLongStateOf(0L) }
        var playing by remember(stream.url) { mutableStateOf(true) }

        val exoPlayer = remember(stream.url) {
            val factory = DefaultHttpDataSource.Factory().setUserAgent(userAgent).setAllowCrossProtocolRedirects(true)
            stream.referer?.let { factory.setDefaultRequestProperties(mapOf("Referer" to it)) }
            ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
                setMediaItem(MediaItem.fromUri(stream.url))
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) vm.nextEpisode()
                    }
                })
            }
        }
        DisposableEffect(stream.url) { onDispose { exoPlayer.release() } }
        LaunchedEffect(exoPlayer) {
            while (true) {
                position = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                playing = exoPlayer.isPlaying
                delay(500)
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
        )

        // Top bar
        val media = vm.detailMedia
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.72f), 1f to Color.Transparent)).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = Oni.Text2, fontSize = 30.sp, modifier = Modifier.clickable { vm.back() }.padding(end = 16.dp))
            Column {
                Text("${media?.displayTitle ?: ""}  •  EPISODE ${vm.episodes.getOrNull(vm.playerIndex) ?: ""}", color = Oni.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Episode ${vm.episodes.getOrNull(vm.playerIndex) ?: ""}", color = Oni.TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // AniSkip: Skip Intro/Outro button while the playhead is inside an op/ed interval.
        val activeSkip = vm.skipIntervals.firstOrNull { it.contains(position) }
        if (activeSkip != null) {
            val skipFocus = remember { FocusRequester() }
            var skipFocused by remember { mutableStateOf(false) }
            LaunchedEffect(activeSkip.type, activeSkip.startMs) { runCatching { skipFocus.requestFocus() } }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 150.dp)
                    .focusRequester(skipFocus)
                    .onFocusChanged { skipFocused = it.isFocused }
                    .clickable { exoPlayer.seekTo(activeSkip.endMs) }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (skipFocused) Oni.White else Color(0xD9141420))
                    .border(1.dp, if (skipFocused) Oni.White else Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(activeSkip.label, color = if (skipFocused) Oni.Bg else Oni.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // Bottom controls
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))).padding(start = 40.dp, end = 40.dp, top = 26.dp, bottom = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (vm.playerIndex - 1 in vm.episodes.indices) ControlButton("⏮") { vm.prevEpisode() }
                ControlButton("«10") { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) }
                ControlButton(if (playing) "⏸" else "▶") { exoPlayer.playWhenReady = !exoPlayer.playWhenReady }
                ControlButton("10»") {
                    val d = exoPlayer.duration
                    val t = exoPlayer.currentPosition + 10_000
                    exoPlayer.seekTo(if (d > 0) t.coerceAtMost(d) else t)
                }
                if (vm.playerIndex + 1 in vm.episodes.indices) ControlButton("⏭") { vm.nextEpisode() }
                Text(mmss(position), color = Oni.Text2, fontSize = 14.sp, modifier = Modifier.width(52.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Oni.SurfaceStrong)) {
                    val frac = if (duration > 0) position.toFloat() / duration else 0f
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Oni.Accent))
                }
                Text(if (duration > 0) mmss(duration) else "--:--", color = Oni.Text2, fontSize = 14.sp, modifier = Modifier.width(52.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("←/→ move between controls  ·  «10 / 10» seek 10s  ·  Enter activate  ·  Back to episodes", color = Oni.Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ControlButton(glyph: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.height(50.dp).widthIn(min = 50.dp)
            .onFocusChanged { focused = it.isFocused }.clickable { onClick() }
            .focusCard(focused, radius = 25.dp).clip(RoundedCornerShape(25.dp))
            .background(if (focused) Oni.White else Oni.SurfaceStrong)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (focused) Oni.Bg else Oni.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private fun mmss(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
