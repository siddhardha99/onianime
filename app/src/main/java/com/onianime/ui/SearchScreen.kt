package com.onianime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.onianime.ui.theme.Oni

private val KEYS: List<String> =
    ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() } + listOf("SPACE", "DEL", "CLR")

@Composable
fun SearchScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(Oni.Bg).padding(start = 48.dp, end = 48.dp, top = 22.dp, bottom = 28.dp)) {
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
            Column(Modifier.width(548.dp).fillMaxHeight()) {
                KEYS.chunked(6).forEach { rowKeys ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowKeys.forEach { key -> Key(key, vm, Modifier.weight(1f)) }
                        repeat(6 - rowKeys.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.width(44.dp))
            // Results — fall back to Trending when there's no query, so the screen is never empty.
            val searching = vm.query.isNotBlank()
            val trending = remember(vm.homeRows.toList()) {
                (vm.homeRows.firstOrNull { it.title == "Trending Now" } ?: vm.homeRows.firstOrNull())?.items ?: emptyList()
            }
            val items = if (searching) vm.results.toList() else trending
            Column(Modifier.fillMaxSize()) {
                Text(
                    when {
                        searching -> "${vm.results.size} results for “${vm.query}”"
                        items.isNotEmpty() -> "Trending now"
                        else -> "Type to search"
                    },
                    color = Oni.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
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
                            onClick = { vm.openDetail(media) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(key: String, vm: AppViewModel, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val label = when (key) { "SPACE" -> "SPACE"; "DEL" -> "DEL"; "CLR" -> "CLEAR"; else -> key }
    val small = key.length > 1
    Box(
        modifier.height(64.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable { vm.search(applyKey(vm.query, key)) }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Oni.Accent else Oni.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) Oni.White else Oni.Text, fontSize = if (small) 13.sp else 22.sp, fontWeight = FontWeight.Bold)
    }
}

private fun applyKey(query: String, key: String): String = when (key) {
    "DEL" -> query.dropLast(1)
    "SPACE" -> "$query "
    "CLR" -> ""
    else -> query + key
}
