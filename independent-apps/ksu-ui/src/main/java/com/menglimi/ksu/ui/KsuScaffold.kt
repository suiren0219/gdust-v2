package com.menglimi.ksu.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

// 导航只包含显示信息，UI 库没有学习通或电量业务依赖。
data class NavItem(val label: String, val glyph: Glyph)

@Composable
fun KsuScaffold(tab: Int, onTab: (Int) -> Unit, tabs: List<NavItem>, content: @Composable (Int) -> Unit) {
    require(tabs.isNotEmpty())
    val selected = tab.coerceIn(tabs.indices)
    val currentTab by rememberUpdatedState(selected)
    val changeTab by rememberUpdatedState(onTab)
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val lightBars = colors.background.luminance() > .5f
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
            window.isNavigationBarContrastEnforced = false
        }
    }
    BackHandler(selected != 0) { onTab(0) }
    val pager = rememberPagerState(initialPage = selected, pageCount = { tabs.size })
    LaunchedEffect(selected) {
        if (pager.currentPage != selected) pager.animateScrollToPage(selected)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect {
            if (it != currentTab) changeTab(it)
        }
    }
    val backgroundLayer = rememberGraphicsLayer()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val rail = maxWidth >= 600.dp
        Box(Modifier.fillMaxSize().drawWithContent {
            backgroundLayer.record { this@drawWithContent.drawContent() }
            drawLayer(backgroundLayer)
        }.background(colors.background)) {
            HorizontalPager(pager, beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = if (rail) 96.dp else 0.dp)) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) { content(page) }
                }
            }
        }
        if (rail) {
            NavigationRail(Modifier.align(Alignment.CenterStart).width(96.dp).fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
                containerColor = colors.surfaceContainerLow) {
                Spacer(Modifier.weight(1f))
                tabs.forEachIndexed { index, item ->
                    NavigationRailItem(selected = selected == index, onClick = { onTab(index) },
                        icon = { KIcon(item.glyph, Modifier.size(24.dp)) }, label = { Text(item.label) })
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.weight(1f))
            }
        } else {
            val barHeight = (64 + ((fontScale - 1f).coerceIn(0f, 1f) * 20)).dp
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp)
                .widthIn(max = 480.dp).fillMaxWidth().heightIn(min = barHeight).height(barHeight)) {
                GlassBottomBar(backgroundLayer, selected, onTab, tabs, Modifier.fillMaxSize())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
