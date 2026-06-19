package com.onianime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.onianime.allanime.AllAnimeClient
import com.onianime.allanime.Stream
import com.onianime.allanime.StreamResolver
import com.onianime.config.AllAnimeConfig
import com.onianime.player.PlayerScreen
import com.onianime.ui.theme.OnianimeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1 smoke test: on launch, resolve the first episode of the top "one piece" result and play
 * its best stream. This proves end-to-end: scrape -> decrypt -> resolve -> ExoPlayer playback with
 * the right Referer. Real navigation (search/detail/home) arrives in Phase 2+.
 */
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnianimeTheme {
                Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                    PlaybackSmokeTest()
                }
            }
        }
    }
}

@Composable
private fun PlaybackSmokeTest() {
    val config = remember { AllAnimeConfig.BAKED_IN }
    var stream by remember { mutableStateOf<Stream?>(null) }
    var status by remember { mutableStateOf("Resolving stream…") }

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                val client = AllAnimeClient(config)
                val show = client.search("one piece", "sub")
                    .maxByOrNull { it.episodes.toIntOrNull() ?: 0 }
                    ?: error("no search results")
                val episode = client.episodesList(show.id, "sub").firstOrNull()
                    ?: error("no episodes")
                StreamResolver(client, config).resolve(show.id, "sub", episode)
                    .firstOrNull { it.url.startsWith("http") }
                    ?: error("no playable stream")
            }
        }.onSuccess { stream = it }
            .onFailure { status = "Error: ${it.message}" }
    }

    val current = stream
    if (current != null) {
        PlayerScreen(stream = current, userAgent = config.agent, modifier = Modifier.fillMaxSize())
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = status)
        }
    }
}
