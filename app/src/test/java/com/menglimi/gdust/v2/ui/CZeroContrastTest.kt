package com.menglimi.gdust.v2.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CZeroContrastTest {
    private fun contrast(foreground: Color, background: Color): Float {
        val a = foreground.compositeOver(background).luminance()
        val b = background.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }

    @Test
    fun bodyAndLabelsRemainReadableAcrossAllCardStates() {
        listOf(false, true).forEach { dark ->
            val ds = dsFor(dark)
            val surfaces = listOf(ds.bg, ds.card, ds.successTint, ds.accentSoft.compositeOver(ds.card))
            surfaces.forEach { surface ->
                assertTrue("primary dark=$dark surface=$surface", contrast(ds.text, surface) >= 7f)
                assertTrue("secondary dark=$dark surface=$surface", contrast(ds.sub, surface) >= 4.5f)
            }
        }
    }

    @Test
    fun surfacesStayOpaqueOverArbitraryWallpaper() {
        listOf(false, true).forEach { dark ->
            val ds = dsFor(dark)
            listOf(ds.bg, ds.card, ds.successTint).forEach { surface ->
                assertEquals(1f, surface.alpha, 0.001f)
                assertEquals(surface.compositeOver(Color.Black), surface.compositeOver(Color.White))
            }
        }
    }

    @Test
    fun primaryButtonLabelHasNormalTextContrast() {
        listOf(false, true).forEach { dark ->
            assertTrue("button dark=$dark", contrast(Color.Black, dsFor(dark).accent) >= 4.5f)
        }
    }
}
