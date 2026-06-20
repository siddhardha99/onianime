package com.onianime.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.onianime.ui.AppViewModel
import com.onianime.ui.theme.Oni
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
        var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

        var controlsVisible by remember { mutableStateOf(true) }
        var interactionTick by remember { mutableIntStateOf(0) }
        val playFocus = remember { FocusRequester() }
        val rootFocus = remember { FocusRequester() }

        val exoPlayer = remember(stream.url) {
            val factory = DefaultHttpDataSource.Factory().setUserAgent(userAgent).setAllowCrossProtocolRedirects(true)
            stream.referer?.let { factory.setDefaultRequestProperties(mapOf("Referer" to it)) }
            ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
                setMediaItem(MediaItem.fromUri(stream.url))
                prepare()
                val resume = vm.resumePositionMs()
                if (resume > 0) seekTo(resume)
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED && vm.settings.autoPlayNext) vm.nextEpisode()
                    }
                })
            }
        }
        DisposableEffect(stream.url) {
            onDispose {
                if (exoPlayer.duration > 0) vm.saveProgress(exoPlayer.currentPosition, exoPlayer.duration)
                exoPlayer.release()
            }
        }
        LaunchedEffect(exoPlayer) {
            var tick = 0
            while (true) {
                position = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                playing = exoPlayer.isPlaying
                if (++tick % 10 == 0 && playing && duration > 0) vm.saveProgress(position, duration)
                delay(500)
            }
        }

        // Auto-hide: any interaction shows controls and restarts the 4.5s hide timer.
        LaunchedEffect(interactionTick) {
            controlsVisible = true
            delay(4500)
            controlsVisible = false
        }
        // Snap focus: to Play when controls appear, to the (focusable) root when they hide.
        LaunchedEffect(controlsVisible) {
            runCatching { if (controlsVisible) playFocus.requestFocus() else rootFocus.requestFocus() }
        }

        Box(
            Modifier.fillMaxSize()
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) {
                        val wasHidden = !controlsVisible
                        interactionTick++ // reveal + restart timer
                        wasHidden // consume only the wake-up press
                    } else false
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
                update = { it.resizeMode = resizeMode },
            )

            // AniSkip — auto-skip (setting) or a focusable button while inside an op/ed interval.
            val activeSkip = vm.skipIntervals.firstOrNull { it.contains(position) }
            if (activeSkip != null && vm.settings.autoSkip) {
                LaunchedEffect(activeSkip.type, activeSkip.startMs) { exoPlayer.seekTo(activeSkip.endMs) }
            } else if (activeSkip != null) {
                val skipFocus = remember { FocusRequester() }
                var skipFocused by remember { mutableStateOf(false) }
                LaunchedEffect(activeSkip.type, activeSkip.startMs) { runCatching { skipFocus.requestFocus() } }
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = if (controlsVisible) 250.dp else 60.dp)
                        .focusRequester(skipFocus).onFocusChanged { skipFocused = it.isFocused }
                        .clickable { exoPlayer.seekTo(activeSkip.endMs) }
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (skipFocused) Oni.White else Color(0xD9141420))
                        .border(2.dp, if (skipFocused) Oni.White else Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                ) {
                    Text(activeSkip.label, color = if (skipFocused) Oni.Bg else Oni.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut() + slideOutVertically { it / 3 },
            ) {
                // Glass scrim — anchored at the bottom, darkest at the bottom, fading up.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.92f)))) {
                    val media = vm.detailMedia
                    // Top: title
                    Row(Modifier.fillMaxWidth().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${media?.displayTitle ?: ""}  •  EPISODE ${vm.episodes.getOrNull(vm.playerIndex) ?: ""}", color = Oni.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Episode ${vm.episodes.getOrNull(vm.playerIndex) ?: ""}", color = Oni.TextHi, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    // Bottom control stack: Zone 1 scrubber, Zone 2 primary, Zone 3 secondary.
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Zone 1 — scrubber (focusable; ←/→ seek 10s, hold to scrub)
                        var scrubFocused by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(mmss(position), color = Oni.Text2, fontSize = 14.sp, modifier = Modifier.width(58.dp))
                            Box(
                                Modifier.weight(1f).height(if (scrubFocused) 14.dp else 6.dp)
                                    .onFocusChanged { scrubFocused = it.isFocused }
                                    .focusable()
                                    .onKeyEvent { e ->
                                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                        when (e.key) {
                                            Key.DirectionLeft -> { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)); true }
                                            Key.DirectionRight -> {
                                                val d = exoPlayer.duration
                                                val t = exoPlayer.currentPosition + 10_000
                                                exoPlayer.seekTo(if (d > 0) t.coerceAtMost(d) else t); true
                                            }
                                            else -> false
                                        }
                                    }
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0x33FFFFFF))
                                    .then(if (scrubFocused) Modifier.border(2.dp, Oni.White, RoundedCornerShape(7.dp)) else Modifier),
                            ) {
                                val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(Oni.Accent))
                                if (scrubFocused) {
                                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                        if (frac > 0f) Spacer(Modifier.weight(frac.coerceIn(0.001f, 0.999f)))
                                        Box(Modifier.size(18.dp).clip(CircleShape).background(Oni.White))
                                        Spacer(Modifier.weight((1f - frac).coerceIn(0.001f, 0.999f)))
                                    }
                                }
                            }
                            Text(if (duration > 0) mmss(duration) else "--:--", color = Oni.Text2, fontSize = 14.sp, modifier = Modifier.width(58.dp))
                        }

                        Spacer(Modifier.height(18.dp))

                        // Zone 2 — primary controls (centered, play largest)
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Control("«10") { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) }
                            Control(if (playing) "⏸" else "▶", big = true, focusRequester = playFocus) {
                                exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                            }
                            Control("10»") {
                                val d = exoPlayer.duration
                                val t = exoPlayer.currentPosition + 10_000
                                exoPlayer.seekTo(if (d > 0) t.coerceAtMost(d) else t)
                            }
                            if (vm.playerIndex + 1 in vm.episodes.indices) Control("⏭") { vm.nextEpisode() }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Zone 3 — secondary controls (left utilities / right options)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Control(vm.currentQualityLabel(), small = true) { vm.cycleQuality() }
                                Control("⤢ ${aspectLabel(resizeMode)}", small = true) { resizeMode = nextResize(resizeMode) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Control("CC", small = true) { vm.toast = "Subtitles are baked into the video (soft subs coming later)" }
                                Control(vm.mode.uppercase(), small = true) { vm.switchAudio() }
                                Control("≣ Episodes", small = true) { vm.back() }
                                Control("⚙", small = true) { vm.toast = "Settings — coming soon" }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A control with the "god" focus state: bright white fill + ring + scale on focus. */
@Composable
private fun Control(
    glyph: String,
    big: Boolean = false,
    small: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val base = Modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .clickable { onClick() }
        .graphicsLayer { val s = if (focused) 1.12f else 1f; scaleX = s; scaleY = s }

    if (big) {
        Box(
            base.size(72.dp).clip(CircleShape)
                .background(if (focused) Oni.White else Color(0x33FFFFFF))
                .then(if (focused) Modifier.border(3.dp, Oni.White, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, color = if (focused) Oni.Bg else Oni.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
    } else {
        Box(
            base.height(if (small) 42.dp else 52.dp).widthIn(min = if (small) 42.dp else 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (focused) Oni.White else Color(0x33FFFFFF))
                .then(if (focused) Modifier.border(3.dp, Oni.White, RoundedCornerShape(26.dp)) else Modifier)
                .padding(horizontal = if (small) 16.dp else 20.dp),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, color = if (focused) Oni.Bg else Oni.White, fontSize = if (small) 14.sp else 17.sp, fontWeight = FontWeight.Bold) }
    }
}

private fun aspectLabel(mode: Int): String = when (mode) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
    else -> "Fit"
}

private fun nextResize(mode: Int): Int = when (mode) {
    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

private fun mmss(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
