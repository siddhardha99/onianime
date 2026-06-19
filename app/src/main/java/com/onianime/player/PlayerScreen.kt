package com.onianime.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.onianime.allanime.Stream

/**
 * Phase 1: play a single resolved [Stream] with ExoPlayer.
 *
 * The key allanime-specific detail is the Referer header — m3u8/mp4 hosts reject requests without
 * it. We set it (plus the spoofed user-agent) on the HTTP data source. With the media3-exoplayer-hls
 * module on the classpath, DefaultMediaSourceFactory auto-detects .m3u8 and builds an HlsMediaSource.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(stream: Stream, userAgent: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember(stream.url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                stream.referer?.let { setDefaultRequestProperties(mapOf("Referer" to it)) }
            }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(stream.url))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(stream.url) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
    )
}
