package app.n_zik.android.components.dialog.song

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "fr-w320dp-h640dp")
class EditMetadataDialogCoverArtLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Test
    fun `long gallery label wraps readably beside an existing cover in a narrow editor`() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        checkGalleryLayout(coverBytes = bytes, fontScale = 1f)
    }

    @Test
    fun `gallery label remains readable and clickable at enlarged font size without a cover`() {
        checkGalleryLayout(coverBytes = null, fontScale = 1.5f)
    }

    private fun checkGalleryLayout(coverBytes: ByteArray?, fontScale: Float) {
        var picks = 0
        var label = ""
        composeRule.setContent {
            val density = LocalDensity.current
            label = LocalContext.current.getString(R.string.pick_from_gallery)
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                // The real editor leaves a narrow content area after dialog and body padding.
                Box(Modifier.width(240.dp)) {
                    val dialog = EditMetadataDialog { null }
                    dialog.CoverArtField(coverBytes, onPickImage = { picks++ })
                }
            }
        }
        composeRule.waitForIdle()
        if (coverBytes != null) {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("Cover art").fetchSemanticsNodes().size == 1
            }
        }
        composeRule.onAllNodesWithText(label).assertCountEquals(1)
        val text = composeRule.onNodeWithText(label, useUnmergedTree = true)
        text.assertIsDisplayed()
        val bounds = text.getBoundsInRoot()
        assertTrue("gallery label needs usable horizontal space: $bounds", bounds.right - bounds.left >= 100.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("the French gallery label should wrap", layout.lineCount > 1)
        assertTrue("wrapping must not degrade to a vertical letter column", layout.lineCount <= 6)
        assertTrue("all gallery text remains visible", !layout.hasVisualOverflow)
        composeRule.onNodeWithText(label).performClick()
        composeRule.runOnIdle { assertEquals("gallery picker still opens once", 1, picks) }
    }
}
