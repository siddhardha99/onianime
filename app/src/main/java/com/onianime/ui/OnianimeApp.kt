package com.onianime.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.onianime.config.AllAnimeConfig
import com.onianime.player.PlayerScreen
import com.onianime.ui.theme.Oni
import kotlinx.coroutines.delay
import kotlin.math.min

/** The design is authored at 1920x1080; we render at that fixed canvas and scale-to-fit any TV. */
private const val DESIGN_W = 1920f
private const val DESIGN_H = 1080f

@Composable
fun OnianimeApp(vm: AppViewModel = viewModel()) {
    val agent = remember { AllAnimeConfig.BAKED_IN.agent }

    BackHandler(enabled = vm.route != Route.Home) { vm.back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Oni.Bg)) {
        val density = LocalDensity.current
        val designWpx = with(density) { DESIGN_W.dp.toPx() }
        val designHpx = with(density) { DESIGN_H.dp.toPx() }
        val scale = min(constraints.maxWidth / designWpx, constraints.maxHeight / designHpx)

        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(DESIGN_W.dp, DESIGN_H.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(Oni.Bg),
        ) {
            if (vm.route == Route.Player) {
                PlayerScreen(vm, agent)
            } else {
                Column(Modifier.fillMaxSize()) {
                    TopBar(vm)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (vm.route) {
                            Route.Home -> HomeScreen(vm)
                            Route.Search -> SearchScreen(vm)
                            Route.Detail -> DetailScreen(vm)
                            Route.Player -> Unit
                        }
                    }
                }
            }

            vm.toast?.let { msg ->
                LaunchedEffect(msg) { delay(2600); vm.toast = null }
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xF2141420)).padding(horizontal = 26.dp, vertical = 14.dp),
                ) {
                    Text(msg, color = Oni.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TopBar(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.route != Route.Home) {
            BackChip { vm.back() }
        }
        Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(Oni.Accent))
        Row(Modifier.padding(start = 11.dp)) {
            Text("oni", color = Oni.TextHi, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text("anime", color = Oni.Accent, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun BackChip(onBack: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(end = 18.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onBack() }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Oni.Accent else Oni.Surface)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("‹  Back", color = if (focused) Oni.White else Oni.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
