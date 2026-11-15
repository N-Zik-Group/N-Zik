package app.n_zik.android.components.settings.appearance.preview

import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassTintSourceKey
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.utils.colorPaletteNameKey
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
    fun currentAppearanceLeavesUnsupportedGroupsOutOfPortableDocument() {
        val glass = mapOf(glassTintSourceKey to GlassTintSource.Custom.name)
        for (palette in listOf(ColorPaletteName.Customized, ColorPaletteName.CustomColor)) {
            val plain = mapOf(colorPaletteNameKey to palette.name)
            assertTrue(runCatching { encodeThemeV1(plain, glass) }.exceptionOrNull() is IllegalArgumentException)
            val document = encodeThemeV1(plain, glass, omitUnsupportedCategories = true)
            val portable = parseThemeV1(document)
            assertEquals(setOf(ThemeCategory.PLAYER_AND_QUEUE, ThemeCategory.NAVIGATION), portable.keys)
        }
    }

    @Test
    fun cleanupRetainsReferencesAndOnlyRemovesManagedFiles() {
        val imports = folder.newFolder("theme-submission-imports")
        val capture = folder.newFolder("theme-submission-previews", "capture")
        val current = File(capture, "navigation-normal.webp").apply { writeText("current") }
        val replaced = File(capture, "player-normal.webp").apply { writeText("old") }
        val marker = File(capture, "complete").apply { writeText("document") }
        val discarded = File(imports, "preview.webp").apply { writeText("discarded") }
        val unrelated = folder.newFile("unrelated.webp")

        pruneSubmissionPreviewFiles(folder.root, listOf(current, replaced, discarded, unrelated), listOf(current))
        assertTrue(current.isFile)
        assertTrue(unrelated.isFile)
        assertTrue(marker.isFile)
        assertFalse(replaced.exists())
        assertFalse(discarded.exists())

        pruneSubmissionPreviewFiles(folder.root, listOf(current), emptyList())
        assertFalse(capture.exists())
    }
}
