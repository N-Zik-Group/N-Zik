package app.n_zik.android.extensions.lastfm

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.appContext
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import it.fast4x.lastfm.LastFm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Tests [LastFmActions.performLoveStatus]: the API is called with the exact arguments, the success toast depends on `love`, and the call is skipped when the metadata is blank, the API keys are missing or the session key is empty (ShufflerTest pattern: prefs/LastFm/Toaster mocked). */
@OptIn(ExperimentalCoroutinesApi::class)
class LastFmActionsTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @BeforeEach
    fun setup() {
        context = mockk()
        prefs = mockk()

        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context

        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { context.encryptedPreferences } returns prefs

        mockkObject(LastFm)
        coEvery { LastFm.setLoveStatus(any(), any(), any()) } returns Result.success(Unit)

        mockkObject(Toaster)
        every { Toaster.s(any<Int>()) } just Runs
        every { Toaster.e(any<Int>()) } just Runs
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `love success calls the API with exact args and toasts loved`() = runTest {
        every { prefs.getString(lastfmSessionKey, null) } returns "session-abc"

        LastFmActions.performLoveStatus("Kraftwerk", "Computer Love", love = true, isConfigured = { true })

        coVerify(exactly = 1) { LastFm.setLoveStatus("Kraftwerk", "Computer Love", true) }
        verify { Toaster.s(R.string.lastfm_loved) }
    }

    @Test
    fun `unlove success toasts unloved`() = runTest {
        every { prefs.getString(lastfmSessionKey, null) } returns "session-abc"

        LastFmActions.performLoveStatus("Kraftwerk", "Computer Love", love = false, isConfigured = { true })

        coVerify(exactly = 1) { LastFm.setLoveStatus("Kraftwerk", "Computer Love", false) }
        verify { Toaster.s(R.string.lastfm_unloved) }
    }

    @Test
    fun `api failure toasts the generic error`() = runTest {
        every { prefs.getString(lastfmSessionKey, null) } returns "session-abc"
        coEvery { LastFm.setLoveStatus(any(), any(), any()) } returns Result.failure(Exception("boom"))

        LastFmActions.performLoveStatus("Kraftwerk", "Computer Love", love = true, isConfigured = { true })

        verify { Toaster.e(R.string.lastfm_action_failed) }
    }

    @Test
    fun `blank metadata is not sent`() = runTest {
        every { prefs.getString(lastfmSessionKey, null) } returns "session-abc"

        LastFmActions.performLoveStatus("Kraftwerk", "", love = true, isConfigured = { true })

        coVerify(exactly = 0) { LastFm.setLoveStatus(any(), any(), any()) }
    }

    @Test
    fun `missing api keys skip the call`() = runTest {
        every { prefs.getString(lastfmSessionKey, null) } returns "session-abc"

        LastFmActions.performLoveStatus("Kraftwerk", "Computer Love", love = true, isConfigured = { false })

        coVerify(exactly = 0) { LastFm.setLoveStatus(any(), any(), any()) }
    }

    @Test
    fun `empty session key skips the call`() = runTest {
        every { prefs.getString(lastfmSessionKey, null) } returns null

        LastFmActions.performLoveStatus("Kraftwerk", "Computer Love", love = true, isConfigured = { true })

        coVerify(exactly = 0) { LastFm.setLoveStatus(any(), any(), any()) }
    }
}
