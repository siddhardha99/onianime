package com.onianime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.onianime.ui.OnianimeApp
import com.onianime.ui.theme.OnianimeTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnianimeTheme {
                Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                    OnianimeApp()
                }
            }
        }
    }
}
