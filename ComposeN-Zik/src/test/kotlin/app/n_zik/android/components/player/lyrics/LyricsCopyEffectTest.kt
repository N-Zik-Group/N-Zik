package app.n_zik.android.components.player.lyrics

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one-shot lyrics copy (spec-lyrics-copy-loop-and-updater-miniplayer): the clipboard is
 * written exactly once per tap, the trigger is consumed after the write, and forced
 * recompositions afterwards never rewrite it (the old composition-body side effect re-wrote
 * on every recomposition).
 *
 * JUnit 4 runner (the `createComposeRule()` rule only works with JUnit 4) on the JUnit 5
 * platform; `sdk = 33` keeps the legacy "Copied" toast branch out of the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class LyricsCopyEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clipboard: ClipboardManager
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipboardText(): String? = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

    @Test
    fun `a false trigger never writes the clipboard`() {
        var trigger by mutableStateOf(false)
        var consumed = 0

        composeRule.setContent {
            LyricsCopyEffect(trigger, "lyrics") {
                trigger = false
                consumed++
            }
        }
        composeRule.waitForIdle()

        assertNull(clipboardText())
        assertEquals(0, consumed)
    }

    @Test
    fun `a true trigger copies the text exactly once and consumes itself`() {
        var trigger by mutableStateOf(false)
        var consumed = 0

        composeRule.setContent {
            LyricsCopyEffect(trigger, "lyrics") {
                trigger = false
                consumed++
            }
        }
        composeRule.waitForIdle()
        trigger = true
        composeRule.waitForIdle()

        assertEquals("lyrics", clipboardText())
        assertTrue(consumed >= 1)
        // The consumed trigger stays false: the LaunchedEffect restart is a no-op.
        assertEquals(1, consumed)
    }

    @Test
    fun `recomposition with new text does not rewrite the clipboard`() {
        var trigger by mutableStateOf(false)
        var lyricsText by mutableStateOf("first lyrics")

        composeRule.setContent {
            LyricsCopyEffect(trigger, lyricsText) { trigger = false }
        }
        composeRule.waitForIdle()
        trigger = true
        composeRule.waitForIdle()
        assertEquals("first lyrics", clipboardText())

        // Forced recomposition (the lyrics screen recomposes constantly): the trigger is
        // already consumed, so the copy must not run again with the new text.
        lyricsText = "second lyrics"
        trigger = false
        composeRule.waitForIdle()
        assertEquals("first lyrics", clipboardText())
    }
}
