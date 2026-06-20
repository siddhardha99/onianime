package com.onianime.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.onianime.ui.theme.Oni

private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Mystery", "Psychological",
)
private val KEY_ROWS = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Composable
fun SearchScreen(vm: AppViewModel) {
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }?.let { vm.search(it) }
    }
    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search anime")
        }
        runCatching { voiceLauncher.launch(intent) }.onFailure { vm.toast = "Voice search isn't available here" }
    }

    val searching = vm.query.isNotBlank() || vm.activeGenre != null
    val trending = remember(vm.homeRows.toList()) {
        (vm.homeRows.firstOrNull { it.title == "Trending Now" } ?: vm.homeRows.firstOrNull())?.items ?: emptyList()
    }
    val items = if (searching) vm.results.toList() else trending

    Column(Modifier.fillMaxSize().background(Oni.Bg).padding(start = 48.dp, end = 48.dp, top = 22.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⌕", color = Oni.Accent, fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Text(
                (vm.query.ifEmpty { "Search anime" }) + " |",
                color = if (vm.query.isEmpty()) Oni.Muted else Oni.TextHi,
                fontSize = 30.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxSize()) {
            // Keyboard
            Keyboard(vm, ::launchVoice)
            Spacer(Modifier.width(44.dp))

            // Right side: genres, recent, results
            Column(Modifier.fillMaxSize()) {
                Text("Browse by genre", color = Oni.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GENRES.forEach { g -> Chip(g, selected = vm.activeGenre == g) { vm.selectGenre(g) } }
                }

                if (!searching && vm.recentSearches.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Recent", color = Oni.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        vm.recentSearches.forEach { q -> Chip("🕘  $q", selected = false) { vm.search(q) } }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    when {
                        vm.query.isNotBlank() -> "${vm.results.size} results for “${vm.query}”"
                        vm.activeGenre != null -> "${vm.activeGenre} anime"
                        items.isNotEmpty() -> "Trending now"
                        else -> "Type, pick a genre, or use voice"
                    },
                    color = Oni.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(14.dp))

                if (vm.searchLoading) {
                    ShimmerGrid()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 184.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(items, key = { it.id }) { media ->
                            PosterCard(
                                imageUrl = media.coverImage,
                                seedColor = parseColor(media.coverColor),
                                title = media.displayTitle,
                                width = 184.dp, height = 262.dp,
                                onClick = { vm.recordSearch(); vm.openDetail(media) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Keyboard(vm: AppViewModel, onVoice: () -> Unit) {
    Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KEY_ROWS.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                row.forEach { ch ->
                    KeyButton(ch.toString(), 46.dp) { vm.search(vm.query + ch) }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            KeyButton("🎤", 64.dp, accent = true) { onVoice() }
            Spacer(Modifier.width(8.dp))
            KeyButton("SPACE", 200.dp) { vm.search(vm.query + " ") }
            Spacer(Modifier.width(8.dp))
            KeyButton("DEL", 84.dp) { vm.search(vm.query.dropLast(1)) }
            Spacer(Modifier.width(8.dp))
            KeyButton("CLEAR", 104.dp) { vm.search("") }
        }
    }
}

@Composable
private fun KeyButton(label: String, width: Dp, accent: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.width(width).height(54.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Oni.White else if (accent) Oni.Accent else Oni.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (focused) Oni.Bg else if (accent) Oni.White else Oni.Text,
            fontSize = if (label.length > 1) 13.sp else 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.onFocusChanged { focused = it.isFocused }.clickable { onClick() }
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Oni.White else if (selected) Oni.Accent else Oni.Surface)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color = if (focused) Oni.Bg else if (selected) Oni.White else Oni.Text2,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ShimmerGrid() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.10f, targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha",
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 184.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(12) {
            Box(Modifier.size(184.dp, 262.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = alpha)))
        }
    }
}
