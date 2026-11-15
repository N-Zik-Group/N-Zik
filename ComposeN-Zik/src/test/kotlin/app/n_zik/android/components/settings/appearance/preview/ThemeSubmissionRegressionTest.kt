package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassTintSourceKey
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.utils.colorPaletteNameKey
import java.io.File
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class ThemeSubmissionRegressionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun minimumCompatibilityMustUseASupportedVersionCode() {
        assertEquals(96, submissionMinimumVersionCode("96", current = 99, previous = null))
        assertEquals(99, submissionMinimumVersionCode(" 99 ", current = 99, previous = null))
        for (input in listOf("", "95", "100", "0", "-1", "8.6.0", "2147483648")) {
            assertNull(submissionMinimumVersionCode(input, current = 99, previous = null))
        }
    }

    @Test
    fun editingCanPreserveThePreviouslyDeclaredMinimum() {
        assertEquals(95, submissionMinimumVersionCode("95", current = 99, previous = 95))
        assertEquals(105, submissionMinimumVersionCode("105", current = 99, previous = 105))
        assertNull(submissionMinimumVersionCode("104", current = 99, previous = 105))
        assertEquals(99, submissionMinimumVersionCode("99", current = 99, previous = 105))
    }

    @Test
    fun currentAppearancePreservesCustomColorAndGlassCategories() {
        val glass = mapOf(glassTintSourceKey to GlassTintSource.Custom.name)
        for (palette in listOf(ColorPaletteName.Customized, ColorPaletteName.CustomColor)) {
            val plain = mapOf(colorPaletteNameKey to palette.name)
            val portable = parseThemeV1(encodeThemeV1(plain, glass))
            assertEquals(ThemeCategory.entries.toSet(), portable.keys)
            assertEquals(palette.name, portable.getValue(ThemeCategory.COLORS).plain[colorPaletteNameKey])
            assertEquals(GlassTintSource.Custom.name, portable.getValue(ThemeCategory.GLASS).glass[glassTintSourceKey])
        }
    }

    @Test
    fun animatedGifKeepsBothFramesAndRejectsOutOfBoundsInputs() {
        val gif = animatedGif()
        assertArrayEquals(gif, gif.inputStream().readSubmissionGifOrNull())
        assertNull("static image".byteInputStream().readSubmissionGifOrNull())
        val wide = gif.copyOf().apply { this[7] = 8 }
        assertThrows(IllegalArgumentException::class.java) { wide.inputStream().readSubmissionGifOrNull() }
        assertThrows(IllegalArgumentException::class.java) {
            gif.copyOfRange(0, gif.lastIndex).inputStream().readSubmissionGifOrNull()
        }
        assertThrows(IllegalArgumentException::class.java) {
            gif.copyOf(SubmissionPreviewLimit + 1).inputStream().readSubmissionGifOrNull()
        }
    }

    @Test
    fun cleanupRetainsReferencesAndOnlyRemovesManagedFiles() {
        val imports = folder.newFolder("theme-submission-imports")
        val capture = folder.newFolder("theme-submission-previews", "capture")
        val current = File(capture, "navigation-normal.webp").apply { writeText("current") }
        val replaced = File(capture, "player-normal.webp").apply { writeText("old") }
        val marker = File(capture, "complete").apply { writeText("document") }
        val discarded = File(imports, "preview.gif").apply { writeText("discarded") }
        val retainedGif = File(imports, "retained.gif").apply { writeText("draft") }
        val unrelated = folder.newFile("unrelated.webp")
        val unrelatedGif = folder.newFile("unrelated.gif")

        pruneSubmissionPreviewFiles(
            folder.root, listOf(current, replaced, discarded, retainedGif, unrelated, unrelatedGif), listOf(current, retainedGif),
        )
        assertTrue(current.isFile)
        assertTrue(unrelated.isFile)
        assertTrue(unrelatedGif.isFile)
        assertTrue(retainedGif.isFile)
        assertTrue(marker.isFile)
        assertFalse(replaced.exists())
        assertFalse(discarded.exists())

        pruneSubmissionPreviewFiles(folder.root, listOf(current), emptyList())
        assertFalse(capture.exists())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeSubmissionGifTest {
    @Test
    fun preflightAcceptsAnActualFrameAndRejectsAContainerWithoutImages() {
        val gif = animatedGif()
        val bytes = checkNotNull(gif.inputStream().readSubmissionGifOrNull())
        requireDecodableSubmissionGif(bytes)
        assertArrayEquals(gif, bytes)

        val noImage = "GIF89a".encodeToByteArray() + byteArrayOf(2, 0, 2, 0, 0, 0, 0, 0x3b)
        val headerOnly = checkNotNull(noImage.inputStream().readSubmissionGifOrNull())
        assertThrows(IllegalArgumentException::class.java) { requireDecodableSubmissionGif(headerOnly) }
    }
}

private fun animatedGif(): ByteArray = Base64.getDecoder().decode(
    "R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQADAAAACwAAAAAAgACAAAIBgABCAQQEAA" +
        "h+QQBDAABACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7",
)
