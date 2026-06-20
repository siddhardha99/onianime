package com.onianime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.onianime.ui.theme.Oni

/** "My Netflix"-style hub: Continue Watching + your list (in-memory for now). */
@Composable
fun MyListScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(Oni.Bg).padding(start = 48.dp, end = 48.dp, top = 24.dp)) {
        Text("My List", color = Oni.TextHi, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Continue Watching", color = Oni.Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))

        if (vm.continueWatching.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing here yet — play something and it'll show up here.",
                    color = Oni.Faint, fontSize = 17.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                items(vm.continueWatching, key = { it.id }) { media ->
                    PosterCard(
                        imageUrl = media.coverImage,
                        seedColor = parseColor(media.coverColor),
                        title = media.displayTitle,
                        width = 200.dp, height = 286.dp,
                        onClick = { vm.openDetail(media) },
                    )
                }
            }
        }
    }
}
