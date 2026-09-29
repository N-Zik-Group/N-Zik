package app.n_zik.android.components.ui.screens.rewind

import android.app.Application
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.profiles.loadActiveProfileFace
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.ProfileFace
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests [RewindDeckViewModel]: a load always ends in non-null data (a failed fetch becomes
 * [emptyRewindData], never a blank screen), the face name and avatar come from the active
 * profile (a failing resolution falls back to the app default name without an avatar —
 * spec-profiles-page-face), and a new load cancels the previous one. The DATA dispatcher is
 * injected, so the whole flow runs deterministically on the test scheduler.
 *
 * `loadActiveProfileFace` is a static facade over the profile prefs, so it is stubbed per
 * test via [mockkStatic] — without the stub every read would fall into the runCatching
 * fallback and collapse to the app default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RewindDeckViewModelTest {

    private lateinit var application: Application
    private lateinit var fetcher: RewindDataFetcher

    private fun withViewModel(
        dataDispatcher: CoroutineDispatcher,
        body: TestScope.(RewindDeckViewModel) -> Unit
    ) {
        runBlocking {
            runTest {
                Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
                try {
                    body(RewindDeckViewModel(fetcher, application, dataDispatcher = dataDispatcher))
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }

    @BeforeEach
    fun setup() {
        application = mockk(relaxed = true)
        every { application.getString(R.string.profile_base_name) } returns "NzikFan"
        fetcher = mockk()
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileCardKt")
        every { loadActiveProfileFace(any(), any()) } returns
            ProfileFace("Test Fan", FaceAvatar.Initials("Test Fan"))
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun loadPublishesTheFetcherDataAndTheFaceName() {
        val data = emptyRewindData(RewindPeriod.Year(2026))
        coEvery { fetcher.getRewindData(RewindPeriod.Year(2026)) } returns data

        withViewModel(UnconfinedTestDispatcher()) { viewModel ->
            viewModel.load(RewindPeriod.Year(2026))
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertSame(data, state.data)
            assertEquals("Test Fan", state.username)
            assertEquals(FaceAvatar.Initials("Test Fan"), state.faceAvatar)
        }
    }

    @Test
    fun fetchFailurePublishesAnEmptyPayloadInsteadOfThrowing() {
        coEvery { fetcher.getRewindData(any()) } throws IllegalStateException("boom")

        withViewModel(UnconfinedTestDispatcher()) { viewModel ->
            viewModel.load(RewindPeriod.Month(2026, 3))
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertTrue(state.data != null, "a failed fetch must still yield readable data")
            assertEquals(emptyRewindData(RewindPeriod.Month(2026, 3)), state.data)
        }
    }

    @Test
    fun faceFailureFallsBackToTheAppDefaultWithoutAnAvatar() {
        every { loadActiveProfileFace(any(), any()) } throws RuntimeException("prefs gone")
        coEvery { fetcher.getRewindData(any()) } returns emptyRewindData(RewindPeriod.Global)

        withViewModel(UnconfinedTestDispatcher()) { viewModel ->
            viewModel.load(RewindPeriod.Global)
            assertEquals("NzikFan", viewModel.state.value.username)
            assertEquals(null, viewModel.state.value.faceAvatar)
            assertFalse(viewModel.state.value.isLoading)
        }
    }

    @Test
    fun faceResolutionPublishesTheAccountAvatarWhenTheProfileHasNoPhoto() {
        every { loadActiveProfileFace(any(), any()) } returns
            ProfileFace("Danie", FaceAvatar.Photo("https://example.com/a.jpg"))
        coEvery { fetcher.getRewindData(any()) } returns emptyRewindData(RewindPeriod.Global)

        withViewModel(UnconfinedTestDispatcher()) { viewModel ->
            viewModel.load(RewindPeriod.Global)
            assertEquals("Danie", viewModel.state.value.username)
            assertEquals(FaceAvatar.Photo("https://example.com/a.jpg"), viewModel.state.value.faceAvatar)
            assertFalse(viewModel.state.value.isLoading)
        }
    }

    @Test
    fun aNewLoadReplacesThePreviousOne() {
        val first = emptyRewindData(RewindPeriod.Year(2025))
        val second = emptyRewindData(RewindPeriod.Year(2026))
        coEvery { fetcher.getRewindData(RewindPeriod.Year(2025)) } returns first
        coEvery { fetcher.getRewindData(RewindPeriod.Year(2026)) } returns second

        withViewModel(UnconfinedTestDispatcher()) { viewModel ->
            viewModel.load(RewindPeriod.Year(2025))
            assertSame(first, viewModel.state.value.data)
            viewModel.load(RewindPeriod.Year(2026))
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertSame(second, state.data)
        }
    }

    @Test
    fun aNewLoadCancelsAnInFlightOne() {
        val second = emptyRewindData(RewindPeriod.Year(2026))
        // The first load is still in flight when the second one starts
        coEvery { fetcher.getRewindData(RewindPeriod.Year(2025)) } coAnswers {
            delay(60_000L)
            emptyRewindData(RewindPeriod.Year(2025))
        }
        coEvery { fetcher.getRewindData(RewindPeriod.Year(2026)) } returns second

        withViewModel(UnconfinedTestDispatcher()) { viewModel ->
            viewModel.load(RewindPeriod.Year(2025))
            assertTrue(viewModel.state.value.isLoading)
            viewModel.load(RewindPeriod.Year(2026))
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertSame(second, state.data)
        }
    }
}
