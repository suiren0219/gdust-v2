package com.menglimi.kedian.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.menglimi.kedian.learning.LearningUiState
import com.menglimi.kedian.power.PowerUiState
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerRoom

@Composable
fun CampusRoot(
    tab: Int,
    onTab: (Int) -> Unit,
    appearance: Appearance,
    learning: LearningUiState,
    power: PowerUiState,
    notificationsEnabled: Boolean,
    onAppearance: (Appearance) -> Unit,
    onLogin: () -> Unit,
    onLearningRefresh: () -> Unit,
    onOpenWork: (String) -> Unit,
    onLearningClearError: () -> Unit,
    onPowerRefresh: () -> Unit,
    onQueryRooms: (String, Int) -> Unit,
    onBind: (String, Int, PowerRoom) -> Unit,
    onPowerClearError: () -> Unit,
    onNotifications: () -> Unit,
    onPreset: (PowerQueryPreset) -> Unit,
    onInterval: (Int) -> Unit,
    onThresholds: (Double, Double) -> Unit,
    onDisconnect: () -> Unit,
    onUnbind: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val lightBars = colors.background.luminance() > .5f
    SideEffect {
        val activity = view.context as? android.app.Activity
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
            window.isNavigationBarContrastEnforced = false
        }
    }
    BackHandler(tab != 0) { onTab(0) }
    val backgroundLayer = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxSize().drawWithContent {
            backgroundLayer.record { this@drawWithContent.drawContent() }
            drawLayer(backgroundLayer)
        }.background(colors.background)) {
            Crossfade(tab, animationSpec = tween(180), label = "page",
                modifier = Modifier.fillMaxSize().statusBarsPadding()) { page ->
                val contentModifier = Modifier.fillMaxSize()
                when (page) {
                    0 -> LearningScreen(learning, onLearningRefresh, onLogin, onOpenWork,
                        onLearningClearError, contentModifier)
                    1 -> PowerScreen(power, onPowerRefresh, onQueryRooms, onBind,
                        onPowerClearError, contentModifier)
                    else -> SettingsScreen(appearance, learning, power, notificationsEnabled,
                        onAppearance, onNotifications, onPreset, onInterval, onThresholds,
                        onDisconnect, onUnbind, contentModifier)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 28.dp, vertical = 12.dp)
            .widthIn(max = 480.dp).fillMaxWidth().height(64.dp)) {
            GlassBottomBar(backgroundLayer, tab, onTab, Modifier.fillMaxSize())
        }
    }
}
