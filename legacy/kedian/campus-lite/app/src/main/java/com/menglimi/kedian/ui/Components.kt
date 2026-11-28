package com.menglimi.kedian.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Glyph { BOOK, BOLT, SETTINGS, REFRESH, ARROW, BACK, LINK, CLOCK, CHECK, CHART, BELL, INFO, CLOSE, SEARCH, MOON }

@Composable
fun KIcon(glyph: Glyph, modifier: Modifier = Modifier.size(24.dp), color: Color = MaterialTheme.colorScheme.onSurface,
          description: String? = null) {
    Canvas(if (description == null) modifier else modifier.semantics { contentDescription = description }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x, y), Offset(x2, y2), 1.8f, StrokeCap.Round)
            fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)
            when (glyph) {
                Glyph.BOOK -> {
                    path { moveTo(12f, 6f); cubicTo(9f, 3f, 5f, 3f, 3f, 4f); lineTo(3f, 19f); cubicTo(6f, 18f, 9f, 18f, 12f, 21f)
                        cubicTo(15f, 18f, 18f, 18f, 21f, 19f); lineTo(21f, 4f); cubicTo(18f, 3f, 15f, 3f, 12f, 6f); close() }
                    line(12f, 6f, 12f, 21f); line(6f, 8f, 9f, 9f); line(15f, 9f, 18f, 8f)
                }
                Glyph.BOLT -> path { moveTo(13f, 2f); lineTo(4f, 14f); lineTo(11f, 14f); lineTo(10f, 22f)
                    lineTo(20f, 9f); lineTo(13f, 9f); close() }
                Glyph.SETTINGS -> {
                    drawCircle(color, 7.1f, Offset(12f, 12f), style = stroke)
                    drawCircle(color, 2.5f, Offset(12f, 12f), style = stroke)
                    for (i in 0..7) {
                        val a = Math.PI * i / 4; val x = kotlin.math.cos(a).toFloat(); val y = kotlin.math.sin(a).toFloat()
                        line(12 + x * 7, 12 + y * 7, 12 + x * 10, 12 + y * 10)
                    }
                }
                Glyph.REFRESH -> {
                    drawArc(color, 35f, 290f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    path { moveTo(20f, 3f); lineTo(20f, 8f); lineTo(15f, 8f) }
                }
                Glyph.ARROW -> path { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
                Glyph.BACK -> { path { moveTo(12f, 5f); lineTo(5f, 12f); lineTo(12f, 19f) }; line(5f, 12f, 21f, 12f) }
                Glyph.LINK -> {
                    path { moveTo(9f, 8f); lineTo(12f, 5f); cubicTo(17f, 0f, 24f, 7f, 19f, 12f); lineTo(16f, 15f) }
                    path { moveTo(15f, 16f); lineTo(12f, 19f); cubicTo(7f, 24f, 0f, 17f, 5f, 12f); lineTo(8f, 9f) }
                    line(8f, 16f, 16f, 8f)
                }
                Glyph.CLOCK -> { drawCircle(color, 9f, Offset(12f, 12f), style = stroke); line(12f, 6f, 12f, 12f); line(12f, 12f, 16f, 14f) }
                Glyph.CHECK -> path { moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f) }
                Glyph.CHART -> { line(4f, 3f, 4f, 20f); line(4f, 20f, 22f, 20f); path { moveTo(7f, 15f); lineTo(11f, 10f); lineTo(15f, 12f); lineTo(21f, 5f) } }
                Glyph.BELL -> {
                    path { moveTo(4f, 17f); lineTo(6f, 14f); lineTo(6f, 9f); cubicTo(6f, 1f, 18f, 1f, 18f, 9f)
                        lineTo(18f, 14f); lineTo(20f, 17f); close() }
                    drawArc(color, 0f, 180f, false, Offset(9f, 18f), Size(6f, 4f), style = stroke)
                }
                Glyph.INFO -> { drawCircle(color, 9f, Offset(12f, 12f), style = stroke); line(12f, 11f, 12f, 17f); drawCircle(color, 1f, Offset(12f, 7f)) }
                Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                Glyph.SEARCH -> { drawCircle(color, 6.5f, Offset(10f, 10f), style = stroke); line(15f, 15f, 21f, 21f) }
                Glyph.MOON -> path { moveTo(19f, 16f); cubicTo(8f, 20f, 4f, 7f, 12f, 3f); cubicTo(0f, 2f, 0f, 20f, 12f, 21f); cubicTo(15f, 21f, 18f, 19f, 19f, 16f) }
            }
        }
    }
}

@Composable
fun KCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
          color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
          content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(stiffness = 500f), label = "cardPress")
    val haptic = LocalHapticFeedback.current
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(KsuTokens.CardRadius)).background(color)
        .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
        } else Modifier).padding(18.dp), content = content)
}

@Composable
fun StatusTag(text: String, container: Color = MaterialTheme.colorScheme.secondaryContainer,
              foreground: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(text, color = foreground, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight(750),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(container).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
fun HeroCard(tags: List<String>, title: String, subtitle: String, glyph: Glyph, modifier: Modifier = Modifier,
             warning: Boolean = false, content: @Composable ColumnScope.() -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val background = if (warning) colors.errorContainer else colors.primaryContainer
    val foreground = if (warning) colors.onErrorContainer else colors.onPrimaryContainer
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(KsuTokens.HeroRadius)).background(background)
        .drawBehind {
            drawCircle(foreground.copy(alpha = .055f), size.width * .38f, Offset(size.width * .95f, size.height * .95f))
            drawCircle(foreground.copy(alpha = .04f), size.width * .27f, Offset(size.width * .95f, size.height * .95f))
        }) {
        KIcon(glyph, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp).size(130.dp), foreground.copy(alpha = .07f))
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { StatusTag(it, foreground.copy(alpha = .10f), foreground) }
            }
            Text(title, color = foreground, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = foreground, fontSize = 13.sp, lineHeight = 20.sp)
            content()
        }
    }
}

@Composable
fun PageHeader(title: String, subtitle: String, onRefresh: (() -> Unit)? = null, loading: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
        if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        else if (onRefresh != null) IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
            KIcon(Glyph.REFRESH, description = "刷新$title")
        }
    }
}

@Composable
fun SectionTitle(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun RowScope.StatCard(value: String, label: String) {
    KCard(Modifier.weight(1f).heightIn(min = 92.dp)) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun MessageCard(message: String, onDismiss: (() -> Unit)? = null) {
    KCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.Top) {
            KIcon(Glyph.INFO, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(message, fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f))
            onDismiss?.let { IconButton(onClick = it, modifier = Modifier.size(48.dp)) {
                KIcon(Glyph.CLOSE, Modifier.size(18.dp), MaterialTheme.colorScheme.onErrorContainer, "关闭提示")
            } }
        }
    }
}

@Composable
fun EmptyCard(title: String, text: String, glyph: Glyph) {
    KCard(Modifier.fillMaxWidth()) {
        KIcon(glyph, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 21.sp,
            modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
fun SettingRow(title: String, summary: String, glyph: Glyph? = null, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        glyph?.let { KIcon(it, Modifier.size(22.dp), MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(summary, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(12.dp)); action()
    }
}

fun displayTime(time: Long, pattern: String = "MM-dd HH:mm"): String = if (time <= 0) "尚未更新" else
    DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
