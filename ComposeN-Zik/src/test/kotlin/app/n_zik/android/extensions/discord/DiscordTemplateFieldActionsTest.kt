package app.n_zik.android.extensions.discord

import androidx.compose.runtime.mutableStateOf
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Toolbar settings dialogs' button contract for the Discord template field dialog:
 * Reset clears the draft to the built-in default WITHOUT saving (the dialog stays
 * open), Cancel discards the draft without saving, OK persists the draft, toasts
 * the "preference saved" confirmation and dismisses.
 */
class DiscordTemplateFieldActionsTest {

    /**
     * [mockkObject] is a spy: without a stub, the real body runs (Toaster.Type's
     * static init needs android.graphics.Color, unavailable on the JVM) — stub the
     * success toast like ShufflerTest does.
     */
    private fun mockToaster() {
        mockkObject(Toaster)
        every { Toaster.s(any<Int>(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Toaster)
    }

    @Test
    fun `reset clears the draft to the default value without saving`() {
        mockToaster()
        val state = mutableStateOf("custom {song.title}")
        val actions = DiscordTemplateFieldActions(state)

        actions.reset()

        assertEquals("", state.value, "reset empties the field so the default template applies again")
        verify(exactly = 0) { Toaster.s(R.string.toast_preference_saved) }
    }

    @Test
    fun `confirm saves the draft toasts preference saved and dismisses`() {
        mockToaster()
        val state = mutableStateOf("my template")
        val actions = DiscordTemplateFieldActions(state)
        val saved = mutableListOf<String>()
        var dismissed = false

        actions.confirm({ saved += it }, { dismissed = true })

        assertEquals(listOf("my template"), saved, "OK persists the current draft")
        assertTrue(dismissed, "OK closes the dialog")
        verify(exactly = 1) { Toaster.s(R.string.toast_preference_saved) }
    }

    @Test
    fun `confirm saves the emptied draft after a reset`() {
        mockToaster()
        val state = mutableStateOf("custom {song.title}")
        val actions = DiscordTemplateFieldActions(state)
        val saved = mutableListOf<String>()

        actions.reset()
        actions.confirm({ saved += it }, { })

        assertEquals(listOf(""), saved, "after reset, OK persists the empty value (the default template applies)")
    }

    @Test
    fun `cancel dismisses without saving or toasting`() {
        mockToaster()
        val state = mutableStateOf("my template")
        val actions = DiscordTemplateFieldActions(state)
        var dismissed = false

        actions.cancel { dismissed = true }

        assertTrue(dismissed, "Cancel closes the dialog")
        assertEquals("my template", state.value, "the draft is discarded with the dialog, nothing is saved")
        verify(exactly = 0) { Toaster.s(R.string.toast_preference_saved) }
    }
}
