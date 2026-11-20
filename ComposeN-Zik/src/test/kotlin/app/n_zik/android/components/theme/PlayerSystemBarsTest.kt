package app.n_zik.android.components.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSystemBarsTest {
    @Test fun `an activity scrim can change Android icons and restore the page`() {
        val owner = SystemBarAppearanceOwner()
        assertEquals(SystemBarAppearance(false, false), owner.updatePage(isDark = false))
        assertEquals(SystemBarAppearance(true, true), owner.updatePage(isDark = true))
        assertEquals(SystemBarAppearance(false, false), owner.updatePage(isDark = false))
    }

    @Test fun `blurred movement cannot reverse the Android icons before the final sample`() {
        val settled = SystemBarAppearance(statusIsDark = true, navigationIsDark = false)
        for (preview in listOf(Color.White, Color.Black, Color.White)) {
            assertEquals(settled, resolvePlayerSystemBarAppearance(preview, preview, Color.White,
                samplingFinished = false, retained = settled, holdUnsettledContrast = true))
        }
        assertEquals(SystemBarAppearance(statusIsDark = false, navigationIsDark = true),
            resolvePlayerSystemBarAppearance(Color.White, Color.Black, Color.White,
                samplingFinished = true, retained = settled, holdUnsettledContrast = true))
    }

    @Test fun `status and navigation retain their own contrast until the next background has settled`() {
        val first = resolvePlayerSystemBarAppearance(Color.White, Color.Black, Color.Black,
            samplingFinished = true, retained = null)
        assertEquals(SystemBarAppearance(statusIsDark = false, navigationIsDark = true), first)
        assertEquals(first, resolvePlayerSystemBarAppearance(null, null, Color.White,
            samplingFinished = false, retained = first))
        assertEquals(SystemBarAppearance(statusIsDark = true, navigationIsDark = false),
            resolvePlayerSystemBarAppearance(Color.Black, Color.White, Color.White,
                samplingFinished = true, retained = first))
        assertEquals(SystemBarAppearance(statusIsDark = false, navigationIsDark = false),
            resolvePlayerSystemBarAppearance(null, null, Color.White,
                samplingFinished = true, retained = first))
    }
}
