package com.menglimi.ksu.ui

import android.content.Context
import android.graphics.RenderEffect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// 背景采样层与底栏分离；折射只变换背景，标签与图标保持清晰。
private const val LENS = """
uniform shader content;
uniform float2 resolution;
uniform float depth;
half4 main(float2 p) {
    float2 c = resolution * 0.5;
    float r = c.y;
    float2 q = abs(p - c) - float2(max(c.x - r, 0.0), 0.0);
    float2 n = max(q, 0.0);
    float distance = length(n);
    float edge = smoothstep(r - depth, r, distance);
    float2 direction = normalize(n + float2(0.001)) * sign(p - c);
    float2 samplePoint = p - direction * edge * depth * 0.24;
    return content.eval(clamp(samplePoint, float2(0.5), resolution - float2(0.5)));
}
"""

@Composable
fun GlassBottomBar(backdrop: GraphicsLayer, selected: Int, onSelect: (Int) -> Unit, tabs: List<NavItem>,
                   modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val gravity by rememberGravityHighlight()
    val highlight by animateFloatAsState(gravity, spring(stiffness = 180f), label = "gravityHighlight")
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    val shader = remember { RuntimeShader(LENS) }
    val blur = with(density) { 4.dp.toPx() }
    val effect = remember(shader, blur) {
        RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "content"),
            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP),
        ).asComposeRenderEffect()
    }
    val shape = RoundedCornerShape(50)
    Box(modifier.heightIn(min = 64.dp).shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = .12f),
        spotColor = Color.Black.copy(alpha = .12f)).clip(shape)) {
        Canvas(Modifier.matchParentSize().onGloballyPositioned { offset = it.positionInRoot() }
            .onSizeChanged {
                shader.setFloatUniform("resolution", it.width.toFloat(), it.height.toFloat())
                shader.setFloatUniform("depth", with(density) { 24.dp.toPx() })
            }.graphicsLayer { renderEffect = effect }) {
            translate(-offset.x, -offset.y) { drawLayer(backdrop) }
        }
        Canvas(Modifier.matchParentSize()) {
            drawRect(colors.surface.copy(alpha = .78f))
            drawRoundRect(Brush.linearGradient(
                listOf(Color.White.copy(alpha = .42f), Color.White.copy(alpha = .04f)),
                start = Offset(size.width * (.5f + highlight * .35f), 0f),
                end = Offset(size.width * (.5f - highlight * .35f), size.height),
            ), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2), style = Stroke(1.dp.toPx()))
        }
        Row(Modifier.fillMaxSize().selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, (label, icon) ->
                val active = selected == index
                val color by animateColorAsState(if (active) colors.primary else colors.onSurfaceVariant, spring(), label = "navColor")
                val fill by animateColorAsState(if (active) colors.primaryContainer.copy(alpha = .9f) else Color.Transparent,
                    spring(), label = "navFill")
                val scale by animateFloatAsState(if (active) 1f else .94f, spring(), label = "navScale")
                Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(shape).background(fill)
                    .selectable(active, role = Role.Tab, onClick = {
                        if (!active) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    }).padding(vertical = 6.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    KIcon(icon, Modifier.size(23.dp), color)
                    Text(label, color = color, fontSize = 11.sp, lineHeight = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/** 仅前台订阅低频重力传感器；不可用时退化为静态高光。 */
@Composable
private fun rememberGravityHighlight(): State<Float> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val position = remember { mutableStateOf(0f) }
    DisposableEffect(context, owner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        var registered = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                if (kotlin.math.abs(value - position.value) > .025f) position.value = value
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun update() {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (!registered && sensor != null) registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            } else if (registered) {
                manager?.unregisterListener(listener)
                registered = false
            }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            manager?.unregisterListener(listener)
        }
    }
    return position
}
