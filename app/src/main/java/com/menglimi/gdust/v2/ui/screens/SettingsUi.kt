package com.menglimi.gdust.v2.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun SettingsSectionTitle(text: String) {
    SmallTitle(
        text = text,
        textColor = LocalDs.current.sub,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp), content = content)
    Spacer(Modifier.height(AppUiTokens.CardGap))
}

@Composable
internal fun SettingsSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = LocalDs.current.sub.copy(alpha = 0.1f),
    )
}

@Composable
internal fun SettingsCardExpand(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val transition = updateTransition(visible, label = "settings-expand")
    val opacity = transition.animateFloat(transitionSpec = { tween(180) }, label = "opacity") {
        if (it) 1f else 0f
    }
    val blur = transition.animateFloat(
        transitionSpec = { tween(if (targetState) 420 else 160) }, label = "blur",
    ) { if (it) 0f else 12f }
    val animating by remember(transition) {
        derivedStateOf { transition.currentState != transition.targetState }
    }
    transition.AnimatedVisibility(
        visible = { it },
        enter = expandVertically(
            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
            expandFrom = Alignment.Top,
        ),
        exit = shrinkVertically(
            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
            shrinkTowards = Alignment.Top,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().then(
                if (animating) Modifier.graphicsLayer {
                    alpha = opacity.value
                    val radius = blur.value * density
                    renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && radius > 0f) {
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else null
                } else Modifier,
            ),
            content = content,
        )
    }
}

/** 先判定按下位置，再识别横拖，避免抢走输入框的横向选择和滚动。 */
internal fun Modifier.settingsEdgeBack(onBack: () -> Unit): Modifier = pointerInput(onBack) {
    val edge = 32.dp.toPx()
    val threshold = 96.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x !in 0f..edge) return@awaitEachGesture
        var distance = 0f
        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
            if (overSlop > 0f) {
                distance = overSlop
                change.consume()
            }
        }
        if (drag != null) {
            val completed = horizontalDrag(drag.id) { change ->
                distance += change.positionChange().x
                change.consume()
            }
            if (completed && distance >= threshold) onBack()
        }
    }
}
