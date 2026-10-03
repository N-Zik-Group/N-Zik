package app.n_zik.android.ui.saveable

import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.ui.styling.typographyOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import timber.log.Timber

/**
 * Issue #881 (gh-881): the crash fix for `ClassCastException: ArrayList cannot be cast to
 * MutableState` (2026-10-03T15:23:55, gh-881) — spec M6. A foreign payload (the exact `ArrayList`
 * of the field crash) must restore to the default WITHOUT throwing, while a valid payload
 * round-trips to the exact same value.
 */
class TolerantStateSaversTest {

    /** Minimal [SaverScope] for exercising `Saver.save` on the JVM. */
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    /** The exact crash payload of 15:23:55: a saved `List<Any>` delivered to a Boolean slot. */
    private val crashPayload: Any = ArrayList<Any>()

    /**
     * `Saver.save` is a member-extension (the SaverScope receiver) — this helper provides the
     * scope through a nested `with`, which is the call form that resolves under this toolchain
     * (a qualified `saver.save(...)` call does not pick up the implicit SaverScope receiver).
     */
    private fun <T, S : Any> Saver<T, S>.saveIn(sc: SaverScope, value: T): S? =
        with(sc) { save(value) }

    @Nested
    inner class BoolSaver {

        private val saver = TolerantBoolStateSaver("test.bool")

        @Test
        fun `round trip true`() {
            val saved = requireNotNull(saver.saveIn(scope, mutableStateOf(true)))
            assertEquals(true, saved)
            assertEquals(true, saver.restore(saved).value)
        }

        @Test
        fun `round trip false`() {
            val saved = requireNotNull(saver.saveIn(scope, mutableStateOf(false)))
            assertEquals(false, saved)
            assertEquals(false, saver.restore(saved).value)
        }

        @Test
        fun `the exact crash payload restores to the default without exception`() {
            val state = saver.restore(crashPayload)
            assertEquals(false, state.value)
        }

        @Test
        fun `any foreign payload restores to the default without exception`() {
            assertEquals(false, saver.restore("stale").value)
            assertEquals(false, saver.restore(123).value)
            assertEquals(false, saver.restore(listOf<Any>(1, 2)).value)
        }
    }

    @Nested
    inner class IntSaver {

        /** A valid PlayerSheet anchor default (`dismissedAnchor` = 0). */
        private val saver = TolerantIntStateSaver(default = 0, slot = "test.int")

        @Test
        fun `round trip`() {
            val saved = requireNotNull(saver.saveIn(scope, mutableStateOf(2)))
            assertEquals(2, saved)
            assertEquals(2, saver.restore(saved).value)
        }

        @Test
        fun `the crash payload restores to the valid anchor default without exception`() {
            assertEquals(0, saver.restore(crashPayload).value)
        }

        @Test
        fun `a foreign payload restores to the provided default`() {
            assertEquals(1, TolerantIntStateSaver(default = 1, slot = "test.int").restore("stale").value)
        }

        @Test
        fun `a type-correct but out-of-domain payload restores to the valid default`() {
            // The PlayerSheet anchor domain is {dismissed, collapsed, expanded} — a foreign Int
            // shifted into this slot passes the `as? Int` cast but must not reach the
            // `error("Unknown PlayerSheet anchor")` branch.
            val anchored = TolerantIntStateSaver(default = 0, slot = "test.anchor") { it in setOf(0, 1, 2) }
            assertEquals(0, anchored.restore(7).value)
            assertEquals(2, anchored.restore(2).value)
        }
    }

    @Nested
    inner class StringSaver {

        private val saver = TolerantStringStateSaver("test.string")

        @Test
        fun `round trip a saved string`() {
            val saved = requireNotNull(saver.saveIn(scope, mutableStateOf("edited title")))
            assertEquals("edited title", saved)
            assertEquals("edited title", saver.restore(saved).value)
        }

        @Test
        fun `a null value saves nothing`() {
            // A `null` state saves a `null` saveable — the registry takes the `init` lambda on
            // restore (nothing is delivered to `restore`), so the state stays `null`.
            assertNull(saver.saveIn(scope, mutableStateOf<String?>(null)))
        }

        @Test
        fun `a foreign payload restores to null without exception`() {
            assertNull(saver.restore(crashPayload).value)
            assertNull(saver.restore(true).value)
            assertNull(saver.restore(42).value)
        }
    }

    @Nested
    inner class PureHelpers {

        @Test
        fun `restoreTolerantBool handles valid and foreign payloads`() {
            assertEquals(true, restoreTolerantBool(true))
            assertEquals(false, restoreTolerantBool(false))
            assertEquals(false, restoreTolerantBool(null))
            assertEquals(false, restoreTolerantBool(crashPayload))
            assertEquals(true, restoreTolerantBool(crashPayload, default = true))
        }

        @Test
        fun `restoreTolerantInt handles valid and foreign payloads`() {
            assertEquals(7, restoreTolerantInt(7, default = 3))
            assertEquals(3, restoreTolerantInt(null, default = 3))
            assertEquals(3, restoreTolerantInt(crashPayload, default = 3))
        }

        @Test
        fun `restoreTolerantString handles valid and foreign payloads`() {
            assertEquals("x", restoreTolerantString("x"))
            assertNull(restoreTolerantString(null))
            assertNull(restoreTolerantString(crashPayload))
            assertNull(restoreTolerantString(42))
        }
    }

    @Nested
    inner class MismatchNet {

        @Test
        fun `a foreign payload is reported`() {
            assertTrue(reportSaveableMismatch<Boolean>("bool", crashPayload))
        }

        @Test
        fun `a matching payload is not reported`() {
            // Boxed primitives: the reified `is` check must match the wrapper classes.
            assertFalse(reportSaveableMismatch<Boolean>("bool", true))
            assertFalse(reportSaveableMismatch<Int>("int", 7))
        }

        @Test
        fun `a null payload (nothing saved) is not reported`() {
            assertFalse(reportSaveableMismatch<Boolean>("bool", null))
        }
    }

    @Nested
    inner class MismatchLineEmission {

        private val captured = mutableListOf<String>()

        private val captureTree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                captured += message
            }
        }

        @BeforeEach
        fun setUp() {
            captured.clear()
            Timber.plant(captureTree)
        }

        @AfterEach
        fun tearDown() {
            Timber.uproot(captureTree)
        }

        private fun mismatchLines() = captured.filter { it.startsWith("SAVEABLE_MISMATCH") }

        @Test
        fun `a foreign payload on a boolean slot emits one line naming the slot`() {
            TolerantBoolStateSaver("main.showPlayer").restore(crashPayload)

            val lines = mismatchLines()
            assertEquals(1, lines.size)
            assertTrue(lines[0].contains("slot=main.showPlayer"))
        }

        @Test
        fun `valid payloads emit no mismatch line`() {
            TolerantBoolStateSaver("main.showPlayer").restore(true)
            TolerantStringStateSaver("lyrics.editedTitle").restore("hello")

            assertTrue(mismatchLines().isEmpty())
        }

        @Test
        fun `an out-of-domain int payload is reported as a domain violation`() {
            TolerantIntStateSaver(default = 0, slot = "playerSheet.previousAnchor") {
                it in setOf(0, 1, 2)
            }.restore(7)

            val lines = mismatchLines()
            assertEquals(1, lines.size)
            assertTrue(lines[0].contains("outside the allowed domain"))
        }

        @Test
        fun `a truncated appearance list is reported`() {
            Appearance.Companion.restore(listOf<Any>(1, 2, 3, 4))

            assertTrue(mismatchLines().any { it.contains("slot=appearance.truncated") })
        }

        @Test
        fun `a truncated color palette list is reported`() {
            ColorPalette.Companion.restore(listOf(2))

            assertTrue(mismatchLines().any { it.contains("slot=colorPalette.truncated") })
        }

        @Test
        fun `a truncated typography list is reported`() {
            Typography.Companion.restore(listOf<Any>(DefaultDarkColorPalette.text.value.toLong(), true, true))

            assertTrue(mismatchLines().any { it.contains("slot=typography.truncated") })
        }
    }

    @Nested
    inner class AppearanceSaver {

        private val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(
                DefaultDarkColorPalette.text,
                useSystemFont = false,
                applyFontPadding = false,
                FontType.Rubik,
            ),
            thumbnailShape = RoundedCornerShape(BoundedCornerSize(12f.dp, 0.25f)),
            uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(20f.dp, 0.4f)),
            artistThumbnailShape = RoundedCornerShape(BoundedCornerSize(48f.dp, 0.25f)),
        )

        @Test
        fun `round trip preserves palette typography and radii`() {
            val saver = TolerantAppearanceStateSaver(fallback = { appearance })
            val saved = requireNotNull(saver.saveIn(scope, mutableStateOf(appearance)))
            val restored = saver.restore(saved).value

            assertEquals(appearance.colorPalette, restored.colorPalette)
            assertEquals(appearance.typography, restored.typography)
            assertEquals(12f, (restored.thumbnailShape as RoundedCornerShape).topStart
                .let { (it as BoundedCornerSize).dp.value })
            assertEquals(20f, (restored.uiRoundnessShape as RoundedCornerShape).topStart
                .let { (it as BoundedCornerSize).dp.value })
            // 48f artist radius normalizes to CircleShape on restore (pre-existing behavior —
            // CircleShape itself is a percent-based RoundedCornerShape in Compose 1.12).
            assertEquals(CircleShape, restored.artistThumbnailShape)
        }

        @Test
        fun `round trip below the circle threshold keeps the bounded radius`() {
            val saver = TolerantAppearanceStateSaver(fallback = { appearance })
            val variant = appearance.copy(
                artistThumbnailShape = RoundedCornerShape(BoundedCornerSize(24f.dp, 0.25f)),
            )
            val saved = requireNotNull(saver.saveIn(scope, mutableStateOf(variant)))
            val restored = saver.restore(saved).value

            assertEquals(24f, (restored.artistThumbnailShape as RoundedCornerShape).topStart
                .let { (it as BoundedCornerSize).dp.value })
        }

        @Test
        fun `a truncated list payload falls back per element without exception`() {
            val saver = TolerantAppearanceStateSaver(fallback = { appearance })
            val restored = saver.restore(crashPayload).value

            // crashPayload is an empty List: every element falls back to its default.
            assertEquals(appearance.colorPalette, restored.colorPalette)
            assertEquals(appearance.typography, restored.typography)
            assertEquals(12f, (restored.thumbnailShape as RoundedCornerShape).topStart
                .let { (it as BoundedCornerSize).dp.value })
            assertEquals(20f, (restored.uiRoundnessShape as RoundedCornerShape).topStart
                .let { (it as BoundedCornerSize).dp.value })
            assertEquals(CircleShape, restored.artistThumbnailShape)
        }

        @Test
        fun `a stale string payload falls back to the site default without exception`() {
            val saver = TolerantAppearanceStateSaver(fallback = { appearance })
            val restored = saver.restore("stale").value
            assertEquals(appearance, restored)
        }
    }

    @Nested
    inner class StylingSavers {

        @Test
        fun `Appearance restore of an empty saved list yields the process default without exception`() {
            // Spec M6 optional case: the exact crash payload through the companion itself.
            val restored = Appearance.Companion.restore(crashPayload)
            assertNotNull(restored)
            assertEquals(DefaultDarkColorPalette, restored.colorPalette)
        }

        @Test
        fun `ColorPalette restore is tolerant of foreign and truncated payloads`() {
            assertEquals(DefaultDarkColorPalette, ColorPalette.Companion.restore("stale"))
            assertEquals(DefaultDarkColorPalette, ColorPalette.Companion.restore(ArrayList<Any>()))
            // Truncated list (missing isDark) still restores the static palette by sentinel.
            assertEquals(PureBlackColorPalette, ColorPalette.Companion.restore(listOf(2)))
        }

        @Test
        fun `Typography restore is tolerant of foreign and truncated payloads`() {
            val fallback = Typography.Companion.fallback
            assertEquals(fallback, Typography.Companion.restore("stale"))
            // Truncated list (missing booleans/fontType) restores with the element defaults —
            // the color value is the fallback's own, so the result equals the fallback.
            val restored = Typography.Companion.restore(
                listOf<Any>(DefaultDarkColorPalette.text.value.toLong())
            )
            assertEquals(fallback, restored)
        }
    }
}
