package com.onianime.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import com.onianime.catalog.HomeRow
import kotlinx.coroutines.delay
import com.onianime.metadata.AniListMedia
import com.onianime.ui.theme.Oni
import com.onianime.ui.theme.focusCard

private val CARD_W = 200.dp
private val CARD_EXPANDED_W = 480.dp
private val CARD_H = 286.dp
private const val INTENT_DELAY_MS = 450L

@Composable
fun HomeScreen(vm: AppViewModel) {
    val rows = remember(vm.homeRows.toList(), vm.continueWatching.toList()) {
        buildList {
            if (vm.continueWatching.isNotEmpty()) add(HomeRow("Continue Watching", vm.continueWatching.toList()))
            addAll(vm.homeRows)
        }
    }

    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Oni.Bg), contentAlignment = Alignment.Center) {
            Text(if (vm.homeLoading) "Loading…" else "Nothing to show", color = Oni.Muted, fontSize = 18.sp)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Oni.Bg),
        contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        items(rows, key = { it.title }) { row ->
            RowSection(row) { vm.openDetail(it) }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RowSection(row: HomeRow, onClick: (AniListMedia) -> Unit) {
    // Per-row focus memory: entering the row (from above/below) redirects to the last-focused tile
    // (index 0 the first time), so vertical navigation lands predictably instead of on a
    // geometry-shifted neighbour caused by the expanding tile.
    val rowEntry = remember { FocusRequester() }
    var lastIndex by remember { mutableStateOf(0) }
    Column {
        Text(
            row.title,
            color = Oni.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier
                .focusGroup()
                .focusProperties { enter = { rowEntry } },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
        ) {
            itemsIndexed(row.items, key = { _, m -> m.id }) { index, media ->
                ExpandingCard(
                    media = media,
                    badge = badgeFor(row.title, index, media),
                    modifier = Modifier
                        .then(if (index == lastIndex) Modifier.focusRequester(rowEntry) else Modifier)
                        .onFocusChanged { if (it.isFocused) lastIndex = index },
                    onClick = { onClick(media) },
                )
            }
        }
    }
}

/** Netflix-style tile: a poster that expands sideways on focus to reveal info inline. */
@Composable
private fun ExpandingCard(media: AniListMedia, badge: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // Intent delay (debounce): only expand if the cursor lingers ~450ms. Moving away before then
    // cancels this effect (focused -> false restarts it) so fast scrolling never triggers expansion.
    LaunchedEffect(focused) {
        if (focused) {
            delay(INTENT_DELAY_MS)
            expanded = true
        } else {
            expanded = false
        }
    }

    val width by animateDpAsState(if (expanded) CARD_EXPANDED_W else CARD_W, label = "cardWidth")

    Box(
        modifier.width(width).height(CARD_H)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .focusCard(focused) // highlight ring is instant; only the expansion is debounced
            .clip(RoundedCornerShape(12.dp))
            .background(parseColor(media.coverColor)),
    ) {
        AsyncImage(
            model = if (expanded) (media.bannerImage ?: media.coverImage) else media.coverImage,
            contentDescription = media.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))
            )
        )

        if (!expanded && badge != null) {
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xCC0A0A0F))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(badge, color = Oni.Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        if (expanded) {
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(media.displayTitle, color = Oni.TextHi, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    media.scoreOutOfTen?.let { Text("★ ${"%.1f".format(it)}", color = Oni.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    media.seasonYear?.let { Text("$it", color = Oni.Text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    media.episodes?.let { Text("$it eps", color = Oni.Text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    Text(media.statusLabel, color = Oni.Text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                media.plainDescription?.let {
                    Spacer(Modifier.height(7.dp))
                    Text(it, color = Color(0xFFB8B8C8), fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Text(
                media.displayTitle,
                color = Oni.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth(),
            )
        }
    }
}

/** Netflix-style contextual pill: rank for Trending, "NEW" for recent, score otherwise. */
private fun badgeFor(rowTitle: String, index: Int, media: AniListMedia): String? {
    val sc = media.scoreOutOfTen
    return when {
        rowTitle == "Continue Watching" -> "▶ RESUME"
        rowTitle == "Trending Now" && index < 10 -> "#${index + 1}"
        rowTitle == "Recently Updated" -> "NEW"
        sc != null && sc >= 7.5 -> "★ ${"%.1f".format(sc)}"
        else -> null
    }
}
