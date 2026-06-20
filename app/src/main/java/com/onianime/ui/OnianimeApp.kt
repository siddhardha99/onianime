package com.onianime.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
                    TopNav(vm)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (vm.route) {
                            Route.Home -> HomeScreen(vm)
                            Route.Search -> SearchScreen(vm)
                            Route.Detail -> DetailScreen(vm)
                            Route.MyList -> MyListScreen(vm)
                            Route.Settings -> SettingsScreen(vm)
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
private fun TopNav(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Oni.Accent))
        Row(Modifier.padding(start = 11.dp)) {
            Text("oni", color = Oni.TextHi, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("anime", color = Oni.Accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(48.dp))
        NavTab("Search", active = vm.route == Route.Search) { vm.goSearch() }
        NavTab("Home", active = vm.route == Route.Home || vm.route == Route.Detail) { vm.goHome() }
        NavTab("My List", active = vm.route == Route.MyList) { vm.goMyList() }
        NavTab("Settings", active = vm.route == Route.Settings) { vm.goSettings() }
    }
}

@Composable
private fun NavTab(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.padding(horizontal = 6.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .clip(RoundedCornerShape(9.dp))
            .background(if (focused) Oni.Accent else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color = when { focused -> Oni.White; active -> Oni.TextHi; else -> Oni.Muted },
            fontSize = 17.sp,
            fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
