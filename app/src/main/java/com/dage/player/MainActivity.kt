package com.dage.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dage.player.data.Prefs
import com.dage.player.ui.AppRoot
import com.dage.player.ui.theme.DageTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by Prefs.themeFlow.collectAsState()
            DageTheme(mode) {
                AppRoot()
            }
        }
    }
}
