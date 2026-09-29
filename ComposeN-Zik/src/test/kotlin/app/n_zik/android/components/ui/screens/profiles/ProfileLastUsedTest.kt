package app.n_zik.android.components.ui.screens.profiles

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.profileLastUsed
import app.it.fast4x.rimusic.utils.saveProfileLastUsed
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests the `lastUsed_<id>` storage contract of the profile store
 * (spec-profiles-page-face, I/O matrix row SWITCH): [saveProfileLastUsed] writes
 * epoch millis under `lastUsed_<id>` in the `profile_preferences` store and
 * [profileLastUsed] reads them back; a never-recorded profile reads null (the
 * zero default is not a timestamp).
 */
class ProfileLastUsedTest {

    // The real profile_preferences store, backed by an in-memory map.
    private val store = mutableMapOf<String, Any?>()

    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    init {
        // The fluent put/remove chain hands the editor back; inside `answers { }` the
        // receiver is the MockKAnswerScope, so the mock is referenced by name, not `this`.
        every { editor.putLong(any(), any()) } answers { store[firstArg<String>()] = secondArg(); editor }
        every { editor.remove(any()) } answers { store.remove(firstArg<String>()); editor }
    }

    private val prefs = mockk<SharedPreferences> {
        every { edit() } answers { editor }
        every { getLong(any(), any()) } answers { (store[firstArg<String>()] as? Long) ?: secondArg() }
    }

    private val context = mockk<Context> {
        every { getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns prefs
    }

    @Test
    fun aSavedLastUseIsReadBack() {
        context.saveProfileLastUsed("work", 123456789L)
        assertEquals(123456789L, context.profileLastUsed("work"))
    }

    @Test
    fun aLaterUseReplacesThePreviousOne() {
        context.saveProfileLastUsed("work", 1000L)
        context.saveProfileLastUsed("work", 2000L)
        assertEquals(2000L, context.profileLastUsed("work"))
    }

    @Test
    fun aNeverRecordedProfileReadsNull() {
        assertEquals(null, context.profileLastUsed("ghost"))
    }
}
