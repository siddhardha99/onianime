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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun HomeScreen(vm: AppViewModel) {
    val rows = remember(vm.homeRows.toList(), vm.continueWatching.toList()) {
        buildList {
            if (vm.continueWatching.isNotEmpty()) add(HomeRow("Continue Watching", vm.continueWatching.toList()))
            addAll(vm.homeRows)
        }
    }
    var hero by remember { mutableStateOf<AniListMedia?>(null) }
    if (hero == null) hero = rows.firstOrNull()?.items?.firstOrNull()

    Row(Modifier.fillMaxSize().background(Oni.Bg)) {
        NavRail(vm)
        Box(Modifier.fillMaxSize()) {
            hero?.let { HeroBackdrop(it) }
            hero?.let { HeroText(it, vm) }

            if (rows.isEmpty()) {
                Text(
                    if (vm.homeLoading) "Loading…" else "Nothing to show",
                    color = Oni.Muted, fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(560.dp)
                        .verticalScroll(rememberScrollState()).padding(bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    rows.forEachIndexed { i, row ->
                        RowSection(
                            row = row,
                            landscape = row.title == "Continue Watching",
                            onFocusedMedia = { hero = it },
                            onClick = { vm.openDetail(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRail(vm: AppViewModel) {
    val items = listOf("⌕" to "Search", "⌂" to "Home", "＋" to "My List", "⚙" to "Settings")
    Column(
        Modifier.width(104.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Oni.Bg.copy(alpha = 0.96f), Color.Transparent)))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { (icon, label) ->
            var focused by remember { mutableStateOf(false) }
            Column(
                Modifier.size(74.dp, 70.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable {
                        when (label) {
                            "Search" -> vm.goSearch()
                            "Home" -> vm.goHome()
                            else -> vm.toast = "$label — coming soon"
                        }
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (focused) Oni.Accent.copy(alpha = 0.2f) else Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(icon, color = if (focused) Oni.White else Oni.Muted, fontSize = 25.sp)
                Text(label, color = if (focused) Oni.White else Oni.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroBackdrop(media: AniListMedia) {
    Box(Modifier.fillMaxWidth().height(560.dp)) {
        AsyncImage(
            model = media.bannerImage ?: media.coverImage,
            contentDescription = media.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().background(parseColor(media.coverColor)),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to Oni.Bg.copy(alpha = 0.96f), 0.45f to Oni.Bg.copy(alpha = 0.6f), 0.75f to Color.Transparent))
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Oni.Bg))
        )
    }
}

@Composable
private fun HeroText(media: AniListMedia, vm: AppViewModel) {
    Column(Modifier.padding(start = 56.dp, top = 54.dp).widthIn(max = 760.dp)) {
        Text(
            media.genres.take(3).joinToString("  •  ").uppercase(),
            color = Oni.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            media.displayTitle,
            color = Oni.TextHi,
            fontSize = 46.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 820.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            media.scoreOutOfTen?.let { Text("★ ${"%.1f".format(it)}", color = Oni.Green, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            media.seasonYear?.let { Text("$it", color = Oni.Text2, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            media.episodes?.let { Text("$it Episodes", color = Oni.Text2, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            Text(media.statusLabel, color = Oni.Text2, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        media.plainDescription?.let {
            Text(it, color = Color(0xFFB8B8C8), fontSize = 18.sp, maxLines = 3, modifier = Modifier.widthIn(max = 620.dp))
        }
    }
}

/** Netflix-style contextual pill: rank for Trending, "NEW" for recent, score otherwise. */
private fun badgeFor(rowTitle: String, index: Int, media: AniListMedia): String? {
    val sc = media.scoreOutOfTen
    return when {
        rowTitle == "Trending Now" && index < 10 -> "#${index + 1}"
        rowTitle == "Recently Updated" -> "NEW"
        sc != null && sc >= 7.5 -> "★ ${"%.1f".format(sc)}"
        else -> null
    }
}

@Composable
private fun RowSection(
    row: HomeRow,
    landscape: Boolean,
    onFocusedMedia: (AniListMedia) -> Unit,
    onClick: (AniListMedia) -> Unit,
) {
    Column {
        Text(row.title, color = Oni.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
        ) {
            itemsIndexed(row.items, key = { _, m -> m.id }) { index, media ->
                if (landscape) {
                    PosterCard(
                        imageUrl = media.bannerImage ?: media.coverImage,
                        seedColor = parseColor(media.coverColor),
                        title = media.displayTitle,
                        width = 320.dp, height = 180.dp,
                        onFocused = { onFocusedMedia(media) },
                        onClick = { onClick(media) },
                        bottomOverlay = {
                            Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Oni.Accent).padding(horizontal = 9.dp, vertical = 4.dp)) {
                                Text("▶ RESUME", color = Oni.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(media.displayTitle, color = Oni.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                        },
                    )
                } else {
                    PosterCard(
                        imageUrl = media.coverImage,
                        seedColor = parseColor(media.coverColor),
                        title = media.displayTitle,
                        width = 200.dp, height = 286.dp,
                        topBadge = badgeFor(row.title, index, media),
                        onFocused = { onFocusedMedia(media) },
                        onClick = { onClick(media) },
                    )
                }
            }
        }
    }
}
