package app.n_zik.android.components.player

import app.n_zik.android.components.glass.GlassSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerSeekOpticsTest {
    @Test fun `zero optics stay clear throughout press and deployment motion`() {
        val settings = GlassSettings(blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
        for (press in listOf(0f, .5f, 1f)) for (exposure in listOf(0f, .5f, 1f)) {
            assertEquals(PlayerSeekOptics(0f, 1f, 0f, 0f), playerSeekOptics(settings, press, exposure))
        }
    }

    @Test fun `press and deployment preserve the configured optical proportions`() {
        val settings = GlassSettings(blur = 12f, vibrancy = 2f, depth = .25f, amount = .5f)
        assertEquals(PlayerSeekOptics(12f, 2f, 0f, 0f), playerSeekOptics(settings, 0f, 1f))
        assertEquals(PlayerSeekOptics(0f, 2f, 12f, 24f), playerSeekOptics(settings, 1f, 1f))
        assertEquals(PlayerSeekOptics(3f, 1.5f, 3f, 6f), playerSeekOptics(settings, .5f, .5f))
    }
}
