package app.n_zik.android.components.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContentCapturePolicyTest {
    @Test fun `content capture follows actual hardware renderer and global switch`() {
        for (enabled in listOf(false, true)) for (profile in GlassQuality.entries) {
            for (sdk in listOf(30, 31, 32, 33, 35)) for (lowRam in listOf(false, true)) {
                val settings = glassProfileDefaults(profile).copy(enabled = enabled)
                val renderer = selectGlassRenderer(settings, sdk, lowRam)
                assertEquals("enabled=$enabled profile=$profile sdk=$sdk lowRam=$lowRam",
                    enabled && profile != GlassQuality.Performance && sdk >= 31 && !lowRam,
                    glassNeedsContentCapture(settings, renderer))
            }
        }
    }

    @Test fun `switching back to optical profile resumes content capture without changing area modes`() {
        val original = GlassSettings(areaModes = mapOf(
            GlassArea.MiniPlayer to GlassAreaMode.Disabled,
            GlassArea.ActionButtons to GlassAreaMode.GlassOnly))
        val performance = original.copy(quality = GlassQuality.Performance)
        assertFalse(glassNeedsContentCapture(performance, selectGlassRenderer(performance, 33, false)))
        assertTrue(glassNeedsContentCapture(original, selectGlassRenderer(original, 33, false)))
        assertEquals(GlassAreaMode.Disabled, original.mode(GlassArea.MiniPlayer))
        assertTrue(original.usesGlassMaterial(GlassArea.ActionButtons))
    }
}
