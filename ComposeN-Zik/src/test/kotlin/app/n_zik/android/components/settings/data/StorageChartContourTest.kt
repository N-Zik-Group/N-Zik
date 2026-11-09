package app.n_zik.android.components.settings.data

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.glassSettingsCardRim
import app.n_zik.android.components.glass.usesGlassMaterial
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageChartContourTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `storage chart draws exactly one rim when contour is enabled`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val chartColor = StorageCategory.IMAGES.color
        val snapshot = requireNotNull(partitionStorage(100L, 0L, mapOf(StorageCategory.IMAGES to 100L)))
        compose.setContent {
            val settings = GlassSettings(
                quality = GlassQuality.Performance,
                film = 0f,
                areaModes = mapOf(GlassArea.SettingsCards to mode.value),
            )
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), CircleShape, CircleShape, CircleShape),
                LocalGlassPreviewSettings provides settings,
            ) {
                Column(Modifier.width(180.dp).background(Color.Black)) {
                    val fill = if (settings.usesGlassMaterial(GlassArea.SettingsCards)) {
                        chartColor.copy(alpha = chartColor.alpha * .68f)
                    } else chartColor
                    Box(Modifier.fillMaxWidth().height(8.dp).testTag("singleRimReference")
                        .glassSettingsCardRim(CircleShape).clip(CircleShape).background(fill))
                    StorageChart(snapshot)
                }
            }
        }

        for (selected in GlassAreaMode.entries) {
            compose.runOnIdle { mode.value = selected }
            val actual = compose.onNodeWithTag("data.storage.chart.bar").captureToImage().toPixelMap()
            val reference = compose.onNodeWithTag("singleRimReference").captureToImage().toPixelMap()
            val x = actual.width / 2
            assertEquals(selected.name, reference[x, 0], actual[x, 0])
            assertEquals(selected.name, reference[x, actual.height - 1], actual[x, actual.height - 1])
        }
    }
}
