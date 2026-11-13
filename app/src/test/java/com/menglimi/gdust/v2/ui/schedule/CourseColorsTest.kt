// Regression tests for the Nexio-derived course palette and contrast fallback.
package com.menglimi.gdust.v2.ui.schedule

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseColorsTest {
    private fun contrast(text: Color, background: Color): Float {
        val a = text.compositeOver(background).luminance()
        val b = background.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }

    @Test
    fun alreadyReadableTextKeepsItsPreferredColor() {
        val preferred = Color(0xFF182B50)
        assertEquals(preferred, CourseColors.readableText(preferred, Color.White))
        assertEquals(Color.White, CourseColors.readableText(Color.White, Color.Black))
    }

    @Test
    fun lowContrastChoosesReadableBlackOrWhite() {
        assertEquals(Color.Black, CourseColors.readableText(Color.Yellow, Color.White))
        assertEquals(Color.White, CourseColors.readableText(Color(0xFF171717), Color.Black))
    }

    @Test
    fun translucentPreferredColorKeepsColorWhenContrastIsSufficient() {
        val nearlyInvisibleBlack = Color.Black.copy(alpha = 0.05f)
        val nearlyInvisibleWhite = Color.White.copy(alpha = 0.05f)
        assertEquals(Color.Black, CourseColors.readableText(nearlyInvisibleBlack, Color.White))
        assertEquals(Color.White, CourseColors.readableText(nearlyInvisibleWhite, Color.Black))
        val translucent = Color.Black.copy(alpha = 0.74f)
        assertEquals(translucent, CourseColors.readableText(translucent, Color.White))
    }

    @Test
    fun everyPaletteColorIsReadableOnLightAndDarkCardSurfaces() {
        // Consecutive single-character names visit all 11 stable palette indices.
        val names = ('A'..'K').map { it.toString() }
        assertEquals(11, names.map { CourseColors.tint(it) }.distinct().size)
        listOf(false to Color(0xFFF7F7F7), true to Color(0xFF000000)).forEach { (dark, page) ->
            names.forEach { name ->
                val card = CourseColors.bg(name, dark).compositeOver(page.copy(alpha = 0.92f)).compositeOver(page)
                listOf(false, true).forEach { monochrome ->
                    val preferred = CourseColors.text(name, dark, onWallpaper = false, monochrome = monochrome)
                    val readable = CourseColors.readableText(preferred, card)
                    assertTrue("$name dark=$dark monochrome=$monochrome", contrast(readable, card) >= 6f)
                }
            }
        }
    }

    @Test
    fun paletteAndSourceAlphaAreStableAcrossRecompositionInputs() {
        assertEquals(CourseColors.tint("离散数学"), CourseColors.tint("离散数学"))
        assertEquals(0.12f, CourseColors.bg("离散数学", false).alpha, 0.003f)
        assertEquals(CourseColors.bg("离散数学", false), CourseColors.bg("离散数学", true))
        assertEquals(0xFF000000L, CourseColors.bg("离散数学") and 0xFF000000L)
        assertEquals(0xFF000000L, CourseColors.bgDark("离散数学") and 0xFF000000L)
    }
}
