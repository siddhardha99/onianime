package com.onianime.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onianime.R

/** onianime palette — lifted directly from the onianime.dc.html design tokens. */
object Oni {
    val Bg = Color(0xFF0A0A0F)
    val Accent = Color(0xFF7C5CFF)
    val AccentSoft = Color(0x8C7C5CFF)
    val TextHi = Color(0xFFF5F5FA)
    val Text = Color(0xFFEDEDF4)
    val Text2 = Color(0xFFCFCFE0)
    val Muted = Color(0xFF9A9AB0)
    val Faint = Color(0xFF6F6F82)
    val Green = Color(0xFF4ADE80)
    val GreenSoft = Color(0xFF86EFAC)
    val Surface = Color(0x12FFFFFF)
    val SurfaceStrong = Color(0x24FFFFFF)
    val White = Color(0xFFFFFFFF)
}

/** Manrope (variable font) — the brand typeface. Weights resolved via the wght axis (API 26+). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

const val FOCUS_SCALE = 1.09f

/**
 * The signature focus treatment from the design: scale-up + white ring + purple glow.
 * Apply to any focusable card/button. [focused] drives the animation.
 */
fun Modifier.focusCard(
    focused: Boolean,
    scale: Float = FOCUS_SCALE,
    radius: Dp = 12.dp,
): Modifier = this
    .graphicsLayer {
        val s = if (focused) scale else 1f
        scaleX = s
        scaleY = s
    }
    .then(
        if (focused) Modifier
            .border(3.dp, Oni.White, RoundedCornerShape(radius))
            .border(7.dp, Oni.AccentSoft, RoundedCornerShape(radius + 4.dp))
        else Modifier
    )

/** Soft brand gradient used as a placeholder behind posters while they load. */
fun placeholderBrush(seedColor: Color = Oni.Accent): Brush =
    Brush.linearGradient(listOf(seedColor.copy(alpha = 0.55f), Oni.Bg))

@Composable
fun animatedFocusScale(focused: Boolean, target: Float = FOCUS_SCALE): Float =
    animateFloatAsState(if (focused) target else 1f, label = "focusScale").value
