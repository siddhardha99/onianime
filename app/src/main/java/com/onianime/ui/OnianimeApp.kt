package com.onianime.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.onianime.config.AllAnimeConfig
import com.onianime.player.PlayerScreen
import com.onianime.ui.theme.Oni
import kotlinx.coroutines.delay

@Composable
fun OnianimeApp(vm: AppViewModel = viewModel()) {
    val agent = remember { AllAnimeConfig.BAKED_IN.agent }

    BackHandler(enabled = vm.route != Route.Home) { vm.back() }

    Box(Modifier.fillMaxSize().background(Oni.Bg)) {
        if (vm.route == Route.Player) {
            PlayerScreen(vm, agent)
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar()
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

@Composable
private fun TopBar() {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(Oni.Accent))
        Row(Modifier.padding(start = 11.dp)) {
            Text("oni", color = Oni.TextHi, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text("anime", color = Oni.Accent, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
