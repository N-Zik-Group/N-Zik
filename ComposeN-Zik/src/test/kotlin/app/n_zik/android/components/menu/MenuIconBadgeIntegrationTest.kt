package app.n_zik.android.components.menu

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.ui.iconBadgeBackground
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuIconBadgeIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `actual MenuIcon list and grid retain native fills and use one Glass support around unchanged glyph`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        val mode = mutableStateOf(false to false)
        val referenceLayout = mutableStateOf<List<Int>?>(null)
        var density = 1f
        val shape = RoundedCornerShape(8.dp)
        val accent = Color(0xFF0088CC)
        val item = object : MenuIcon {
            override val iconId = R.drawable.search
            override val menuIconTitle: String @Composable get() = "Shuffle"
            override val modifier = Modifier.width(200.dp).testTag("entry")
            override fun onShortClick() = Unit
        }
        compose.setContent {
            val (grid, glass) = mode.value
            key(grid, glass) {
                CompositionLocalProvider(LocalAppearance provides Appearance(
                    colorPalette = ColorPalette.fallback.copy(accent = accent),
                    typography = Typography.fallback,
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                )) {
                    density = LocalDensity.current.density
                    val layout = referenceLayout.value
                    if (layout == null) {
                        if (grid) item.GridMenuItem() else item.ListMenuItem()
                    } else Box(Modifier.size((layout[0] / density).dp, (layout[1] / density).dp).testTag("entry")) {
                        Box(Modifier.offset((layout[2] / density).dp, (layout[3] / density).dp).size(32.dp)
                            .then(if (grid && !glass) Modifier.background(accent.copy(alpha = .1f), shape) else Modifier)
                            .iconBadgeBackground(accent.copy(alpha = .1f), shape),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.search), null, Modifier.size(18.dp), tint = accent)
                        }
                    }
                }
            }
        }
        for (glass in listOf(false, true)) for (grid in listOf(false, true)) {
            compose.runOnIdle {
                context.preferences.edit().putBoolean(glassEnabledKey, glass).commit()
                mode.value = grid to glass
                referenceLayout.value = null
            }
            val actual = compose.onNodeWithTag("entry").captureToImage().toPixelMap()
            val badgeSize = (32 * density).toInt()
            val left = if (grid) (actual.width - badgeSize) / 2 else 0
            val top = if (grid) (12 * density).toInt() else (actual.height - badgeSize) / 2
            // Keep glyphs at identical screen coordinates for exact native antialiasing parity.
            compose.runOnIdle { referenceLayout.value = listOf(actual.width, actual.height, left, top) }
            val reference = compose.onNodeWithTag("entry").captureToImage().toPixelMap()
            for (y in 0 until badgeSize) for (x in 0 until badgeSize) {
                assertEquals("grid=$grid glass=$glass badge at $x,$y",
                    reference[left + x, top + y], actual[left + x, top + y])
            }
        }
    }
}
