package app.n_zik.android.components.glass

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlassFilmOpacityTest {
    @Test fun `film zero half and full match for chrome overlays and fallbacks`() {
        listOf(0f, .5f, 1f).forEach { configured ->
            assertEquals(configured, glassFilmOpacity(configured, false))
            listOf(0f, .5f, 1f).forEach { progress ->
                assertEquals(configured, glassFilmOpacity(configured, true, progress = progress))
                assertEquals(configured, glassFilmOpacity(configured, true, progress = progress, fallback = true))
                assertEquals(configured, glassFilmOpacity(configured, true, progress = progress, fallback = true, dark = false))
            }
        }
        assertEquals(0f, glassFilmOpacity(-.5f, true))
        assertEquals(1f, glassFilmOpacity(1.5f, true))
    }

    @Test fun `menu handoff keeps the configured film without a second opacity layer`() {
        val start = glassFilmOpacity(.2f, true, progress = 0f)
        val middle = glassFilmOpacity(.2f, true, progress = .5f)
        val end = glassFilmOpacity(.2f, true, progress = 1f)
        assertEquals(.2f, start)
        assertEquals(.2f, middle)
        assertEquals(.2f, end)
        assertEquals(start, glassFilmOpacity(.2f, true, progress = -1f))
        assertEquals(end, glassFilmOpacity(.2f, true, progress = 2f))
    }
}
