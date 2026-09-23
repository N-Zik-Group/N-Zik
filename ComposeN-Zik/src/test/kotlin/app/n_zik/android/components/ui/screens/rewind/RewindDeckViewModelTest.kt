package app.n_zik.android.components.ui.screens.rewind

import android.app.Application
import app.n_zik.android.R
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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
 * [emptyRewindData], never a blank screen), the username is resolved from DataStore with the
 * app default, and a new load cancels the previous one. The DATA dispatcher is injected, so
 * the whole flow runs deterministically on the test scheduler.
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
        every { application.getString(R.string.rw_default_username) } returns "Music Fan"
        fetcher = mockk()
        mockkObject(DataStoreUtils)
        every { DataStoreUtils.getString(any(), any(), any()) } returns "Test Fan"
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun loadPublishesTheFetcherDataAndTheDataStoreUsername() {
        val data = emptyRewindData(RewindPeriod.Year(2026))
        coEvery { fetcher.getRewindData(RewindPeriod.Year(2026)) } returns data

        withViewModel(UnconfinedTestDispatcher()) { viewModel ->
            viewModel.load(RewindPeriod.Year(2026))
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertSame(data, state.data)
            assertEquals("Test Fan", state.username)
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
    fun usernameFallbackUsesTheAppDefaultWhenDataStoreFails() {
        every { DataStoreUtils.getString(any(), any(), any()) } throws RuntimeException("datastore gone")
        coEvery { fetcher.getRewindData(any()) } returns emptyRewindData(RewindPeriod.Global)

        withViewModel(UnconfinedTestDispatcher()) { viewModel ->
            viewModel.load(RewindPeriod.Global)
            assertEquals("Music Fan", viewModel.state.value.username)
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
