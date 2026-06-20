package com.onianime.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.onianime.catalog.HomeRow
import com.onianime.metadata.AniListMedia
import com.onianime.ui.theme.Oni
import com.onianime.ui.theme.focusCard

private val CARD_W = 200.dp
private val CARD_EXPANDED_W = 480.dp
private val CARD_H = 286.dp

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

@Composable
private fun RowSection(row: HomeRow, onClick: (AniListMedia) -> Unit) {
    Column {
        Text(
            row.title,
            color = Oni.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
        ) {
            itemsIndexed(row.items, key = { _, m -> m.id }) { index, media ->
                ExpandingCard(
                    media = media,
                    badge = badgeFor(row.title, index, media),
                    onClick = { onClick(media) },
                )
            }
        }
    }
}

/** Netflix-style tile: a poster that expands sideways on focus to reveal info inline. */
@Composable
private fun ExpandingCard(media: AniListMedia, badge: String?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (focused) CARD_EXPANDED_W else CARD_W, label = "cardWidth")

    Box(
        Modifier.width(width).height(CARD_H)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .focusCard(focused)
            .clip(RoundedCornerShape(12.dp))
            .background(parseColor(media.coverColor)),
    ) {
        AsyncImage(
            model = if (focused) (media.bannerImage ?: media.coverImage) else media.coverImage,
            contentDescription = media.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))
            )
        )

        if (!focused && badge != null) {
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xCC0A0A0F))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(badge, color = Oni.Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        if (focused) {
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
