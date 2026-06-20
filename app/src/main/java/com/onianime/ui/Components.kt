package com.onianime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.onianime.ui.theme.Oni
import com.onianime.ui.theme.focusCard
import com.onianime.ui.theme.placeholderBrush

fun parseColor(hex: String?): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Oni.Accent

/** A focusable poster/landscape card with the design's focus treatment and bottom gradient. */
@Composable
fun PosterCard(
    imageUrl: String?,
    seedColor: Color,
    title: String,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    topBadge: String? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
    bottomOverlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(width, height)
            .onFocusChanged { if (it.isFocused) { focused = true; onFocused() } else if (!it.hasFocus) focused = false }
            .clickable { onClick() }
            .focusCard(focused)
            .clip(RoundedCornerShape(12.dp))
            .background(placeholderBrush(seedColor)),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))
            )
        )
        if (topBadge != null) {
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xCC0A0A0F))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(topBadge, color = Oni.Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (bottomOverlay != null) {
            bottomOverlay()
        } else {
            Text(
                text = title,
                color = Oni.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth(),
            )
        }
    }
}
