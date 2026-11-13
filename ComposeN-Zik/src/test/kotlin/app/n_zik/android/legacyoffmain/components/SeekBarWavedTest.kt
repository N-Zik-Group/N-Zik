package app.n_zik.android.legacyoffmain.components

import app.n_zik.android.ui.components.WAVE_PATH_STEP_PX
import app.n_zik.android.ui.components.waveSampleXs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * gh-606 M11: [waveSampleXs] is the pure (Compose-Path-free) sampling core that
 * `wavePath()` loops over to build its Compose [androidx.compose.ui.graphics.Path]. It is
 * tested directly, in a plain JVM unit test with no Robolectric/Android runtime, because
 * constructing a real Compose `Path` on the android target requires a real
 * `android.graphics.Path`, unavailable outside Robolectric/instrumentation.
 *
 * This test file itself lives under `app.n_zik.android.*` -- not the legacy package -- per
 * the gh-606 spec's "no new file under app.n_zik.android.*" boundary; `internal`
 * visibility is module-scoped in Kotlin, not package-scoped, so it still resolves
 * [waveSampleXs]/[WAVE_PATH_STEP_PX] from here without needing to share their package
 * (same convention as `VideoOrSongInfoScreenLoadOffMainTest`/`GoToLinkResolveOffMainTest`, Lot 2).
 */
class SeekBarWavedTest {

    @Test
    fun `segment count decreases as the sampling step grows`() {
        val fine = waveSampleXs(width = 300f, step = 1f)
        val coarse = waveSampleXs(width = 300f, step = WAVE_PATH_STEP_PX)

        assertTrue(
            coarse.size < fine.size,
            "expected fewer samples with a bigger step: fine=${fine.size} coarse=${coarse.size}"
        )
    }

    @Test
    fun `default step matches the named WAVE_PATH_STEP_PX constant`() {
        assertEquals(waveSampleXs(width = 300f), waveSampleXs(width = 300f, step = WAVE_PATH_STEP_PX))
    }

    @Test
    fun `first sampled point is the origin, matching yFromX(0f)`() {
        val xs = waveSampleXs(width = 300f, step = WAVE_PATH_STEP_PX)

        assertEquals(0f, xs.first())
    }

    @Test
    fun `sampling always reaches the full width within one step`() {
        val width = 300f
        val step = WAVE_PATH_STEP_PX

        val xs = waveSampleXs(width, step)

        // The loop samples while currentX < width, so the last sample can fall short of the
        // full width by up to one step -- never more, i.e. it still "covers" the width.
        assertTrue(xs.last() < width)
        assertTrue(xs.last() >= width - step)
    }

    @Test
    fun `sampling covers the full width regardless of step size`() {
        val width = 97f

        for (step in listOf(1f, 3f, 5f, 10f)) {
            val xs = waveSampleXs(width, step)
            assertTrue(xs.last() >= width - step, "step=$step last=${xs.last()} width=$width")
        }
    }
}
