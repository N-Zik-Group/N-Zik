package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassTintSourceKey
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.utils.colorPaletteNameKey
import java.io.File
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        assertEquals(97, submissionMinimumVersionCode("97", current = 99, previous = null))
        assertEquals(99, submissionMinimumVersionCode(" 99 ", current = 99, previous = null))
        for (input in listOf("", "95", "96", "100", "0", "-1", "8.6.0", "2147483648")) {
            assertNull(submissionMinimumVersionCode(input, current = 99, previous = null))
        }
    }

    @Test
    fun editingPreservesFutureMinimumsButCannotBypassTheThemeStoreFloor() {
        assertNull(submissionMinimumVersionCode("95", current = 99, previous = 95))
        assertNull(submissionMinimumVersionCode("96", current = 99, previous = 96))
        assertEquals(105, submissionMinimumVersionCode("105", current = 99, previous = 105))
        assertNull(submissionMinimumVersionCode("104", current = 99, previous = 105))
        assertEquals(99, submissionMinimumVersionCode("99", current = 99, previous = 105))
        assertEquals(97, submissionDefaultMinimumVersionCode(null))
        assertEquals(97, submissionDefaultMinimumVersionCode(96))
        assertEquals(105, submissionDefaultMinimumVersionCode(105))
        assertEquals(97, submissionMinimumVersionCode("97", current = 96, previous = null))
    }

    @Test
    fun removingAnInheritedExtraSurvivesDraftResumeWithoutRestoringItsReplacement() {
        val extra = "extra-first-normal"
        val inherited = (automaticPreviews + (1..8).map { "extra-$it-normal" })
            .map { StorePreview("previews/$it.webp", "other", "a".repeat(64)) }
        val selection = SubmissionPreviewSelection()
        val initial = selection.published(inherited, reuseAutomatic = true)
        assertEquals(36, selection.gallerySlots(initial.keys, emptySet()).size)

        val removed = selection.withoutExtra("extra-1-normal").withoutExtra(extra)
        val resumed = Json.decodeFromString<SubmissionPreviewSelection>(Json.encodeToString(removed))
        val kept = resumed.published(inherited, reuseAutomatic = true)
        val gallery = resumed.gallerySlots(kept.keys, setOf(extra, "extra-1-normal"))

        assertEquals(35, gallery.size)
        assertFalse("extra-1-normal" in kept)
        assertFalse("extra-1-normal" in gallery)
        assertFalse(extra in gallery)
        assertTrue(gallery.containsAll(requiredPreviews))
        assertEquals("player-normal", gallery.first())
        assertFalse("extra-1-normal" in resumed.published(inherited, reuseAutomatic = false))
        assertTrue("extra-2-normal" in resumed.published(inherited, reuseAutomatic = false))
    }

    @Test
    fun requiredAndGeneratedSlotsCannotBeRemovedEvenByAnInvalidDraft() {
        for (slot in automaticPreviews) {
            assertThrows(IllegalArgumentException::class.java) { SubmissionPreviewSelection().withoutExtra(slot) }
        }
        val corrupted = SubmissionPreviewSelection(automaticPreviews.toSet())
        val published = automaticPreviews.map { StorePreview("previews/$it.webp", "other", "a".repeat(64)) }
        assertEquals(automaticPreviews.toSet(), corrupted.published(published, reuseAutomatic = true).keys)
        assertEquals(SubmissionPreviewSelection(), Json.decodeFromString<SubmissionPreviewSelection>("{}"))
    }

    @Test
    fun submissionViewerKeepsTheVisibleVariantOrderAndSharedExtrasWithoutEmptySlots() {
        val selected = SubmissionPreviewSelection().withoutExtra("removed-normal")
        val ready = setOf("queue-normal", "player-normal", "player-glass", "extra-glass", "shared-poster", "removed-normal")
        val slots = selected.gallerySlots(emptySet(), ready)
        val normal = submissionPreviewVariantSlots(slots, glass = false).filter { it in ready }
        val glass = submissionPreviewVariantSlots(slots, glass = true).filter { it in ready }

        assertEquals(listOf("player-normal", "queue-normal", "shared-poster"), normal)
        assertEquals(listOf("player-glass", "extra-glass", "shared-poster"), glass)
        assertFalse("lyrics-normal" in normal)
        assertFalse("removed-normal" in normal)
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
    fun oldDraftDropsUnsupportedFieldsButKeepsSupportedAppearanceAndSparseCategories() {
        val source = """
            {"schemaVersion":1,"retiredDraftField":true,"categories":{
                "colors":{"palette":"customized","mode":"light","customAccent":-1234,"retiredColor":true},
                "navigation":{"position":"top","labels":"icon-only","homeTabs":{
                    "order":["songs"],"songs":false,"albums":true,"artists":true,"playlists":true,
                    "retiredTab":false}},
                "retiredCategory":{"value":true}
            }}
        """.trimIndent()
        val restored = requireNotNull(compatibleThemeSubmissionDraftAppearance(source))
        val settings = parseThemeV1(restored)

        assertEquals(setOf(ThemeCategory.COLORS, ThemeCategory.NAVIGATION), settings.keys)
        assertEquals("Customized", settings.getValue(ThemeCategory.COLORS).plain[colorPaletteNameKey])
        assertEquals(-1234, settings.getValue(ThemeCategory.COLORS).plain[app.n_zik.android.utils.customColorKey])
        assertEquals("Top", settings.getValue(ThemeCategory.NAVIGATION).plain[app.n_zik.android.utils.navigationBarPositionKey])
        assertFalse(restored.contains("retired"))
        assertThrows(IllegalArgumentException::class.java) { parseThemeV1(source) }
    }

    @Test
    fun malformedDraftAppearanceRequestsFallbackAndValidDraftKeepsItsExactSettings() {
        val valid = defaultThemeAppearanceDocument()
        assertEquals("  $valid  ", compatibleThemeSubmissionDraftAppearance("  $valid  "))
        assertEquals(Json.parseToJsonElement(valid),
            Json.parseToJsonElement(requireNotNull(compatibleThemeSubmissionDraftAppearance(valid))))
        val root = Json.parseToJsonElement(valid) as JsonObject
        val wrongVersion = JsonObject(root + ("schemaVersion" to JsonPrimitive(2)))
        val wrongCategories = JsonObject(root + ("categories" to JsonPrimitive("invalid")))
        for (invalid in listOf("broken JSON", "[]", wrongVersion.toString(), wrongCategories.toString(),
            """{"schemaVersion":1,"categories":{"retiredCategory":{}}}""",
            """{"schemaVersion":1,"categories":{"colors":{"palette":"unknown","mode":"light"}}}""")) {
            assertNull(compatibleThemeSubmissionDraftAppearance(invalid))
        }
    }

    @Test
    fun resumingAnOlderPositiveDraftMinimumUpgradesItWithoutMaskingInvalidInput() {
        for (old in listOf("1", "95", " 96 ")) {
            assertEquals("97", submissionDraftMinimumVersionInput(old))
        }
        for (retained in listOf("97", "105", "0", "-1", "invalid", "2147483648")) {
            assertEquals(retained, submissionDraftMinimumVersionInput(retained))
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
