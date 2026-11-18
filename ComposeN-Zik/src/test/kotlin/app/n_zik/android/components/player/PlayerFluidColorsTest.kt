package app.n_zik.android.components.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.components.glass.GlassArtworkTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFluidColorsTest {
    @Test
    fun extremeBlackArtworkKeepsAnAnthraciteGradient() {
        val stops = PlayerFluidColors.ExtremeBlackCover
        assertEquals(Color.Black, stops.baseEnd)
        assertTrue(stops.baseStart.luminance() > stops.baseEnd.luminance())
        assertEquals(stops.baseStart.red, stops.baseStart.blue, 0f)
        assertTrue(stops.centerSurface().luminance() < .05f)
    }

    @Test
    fun playerAccentFlatBackgroundGlassAndSwatchesShareTheSameProgress() {
        fun paint(color: Color) = PlayerArtworkPaint(
            color, color, PlayerFluidColors(color, color, color, color),
            List(7) { color }, GlassArtworkTint(color),
        )
        val from = paint(Color.Black)
        val target = paint(Color.White)
        val middle = from.at(target, .5f)
        val expected = lerp(Color.Black, Color.White, .5f)

        assertEquals(expected, middle.accent)
        assertEquals(expected, middle.surface)
        assertEquals(expected, middle.fluid?.baseStart)
        assertEquals(List(7) { expected }, middle.swatches)
        assertEquals(expected, middle.glass.color)
    }

    @Test
    fun backgroundAndProvisionalInkSurfaceUseTheSameIntermediateStops() {
        val dark = PlayerFluidColors(Color(0xff302317), Color(0xff634220), Color(0xff231b17), Color(0xff654632))
        val light = PlayerFluidColors(Color.LightGray, Color.White, Color.Gray, Color.LightGray)
        val surface = mutableStateOf<Color?>(dark.centerSurface())
        val sampling = PlayerBackdropSampling(previousSurface = Color.Black, provisionalSurface = surface)
        val frames = (0..20).map { dark.at(light, it / 20f).centerSurface() }

        assertEquals(dark.centerSurface(), frames.first())
        assertEquals(light.centerSurface(), frames.last())
        assertTrue(frames.zipWithNext().all { (old, new) -> old.luminance() <= new.luminance() })
        frames.forEach {
            surface.value = it
            assertEquals(it, sampling.foregroundSurface)
        }
    }

    @Test
    fun swatchesFadeInPlaceWithoutReorderingThePalette() {
        val from = listOf(Color.Black, Color.Red, Color.Green, Color.Blue, Color.White, Color.Gray, Color.Yellow)
        val target = listOf(Color.White, Color.Blue, Color.Gray, Color.Red, Color.Black, Color.Green, Color.Magenta)
        val halfway = interpolatePlayerSwatches(from, target, 0.5f)

        assertEquals(from, interpolatePlayerSwatches(from, target, 0f))
        assertEquals(target, interpolatePlayerSwatches(from, target, 1f))
        assertEquals(from.size, halfway.size)
        assertTrue(halfway.first().luminance() in 0.01f..0.99f)
        assertTrue(halfway.last() != from.last() && halfway.last() != target.last())
    }
}
