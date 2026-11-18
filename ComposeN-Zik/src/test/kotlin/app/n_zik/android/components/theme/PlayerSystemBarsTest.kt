package app.n_zik.android.components.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSystemBarsTest {
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
