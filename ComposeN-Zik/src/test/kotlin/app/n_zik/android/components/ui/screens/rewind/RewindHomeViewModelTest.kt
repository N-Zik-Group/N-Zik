package app.n_zik.android.components.ui.screens.rewind

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests [RewindHomeViewModel]: one fetch per ViewModel (re-entering the home does not
 * refetch — spec GH-275, patch "No ViewModel"), the year rows + all-time row are published
 * from the fetcher, and a failed fetch becomes an empty payload.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RewindHomeViewModelTest {

    private lateinit var application: Application
    private lateinit var fetcher: RewindDataFetcher

    private val years = listOf(
        RewindHomeYear(
            year = 2026,
            minutes = 1_200L,
            plays = 50,
            months = emptyList(),
            monthTopArtworks = emptyList(),
            topArtworks = TopArtworks(null, null, null, null)
        )
    )
    private val global = RewindHomeGlobal(minutes = 3_600L, plays = 150, topArtworks = TopArtworks(null, null, null, null))
    private val homeData = RewindHomeData(years = years, global = global)

    private fun withViewModel(body: TestScope.(RewindHomeViewModel) -> Unit) {
        runBlocking {
            runTest {
                Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
                try {
                    body(RewindHomeViewModel(fetcher, application))
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }

    @BeforeEach
    fun setup() {
        application = mockk(relaxed = true)
        fetcher = mockk()
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun loadPublishesTheYearsAndTheGlobalRow() {
        coEvery { fetcher.getRewindHomeData() } returns homeData

        withViewModel { viewModel ->
            assertTrue(viewModel.state.value.isLoading)
            viewModel.load()
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals(years, state.years)
            assertEquals(global, state.global)
        }
    }

    @Test
    fun loadIsGuardedToOneFetchPerViewModel() {
        coEvery { fetcher.getRewindHomeData() } returns homeData

        withViewModel { viewModel ->
            viewModel.load()
            viewModel.load()
            viewModel.load()
            coVerify(exactly = 1) { fetcher.getRewindHomeData() }
        }
    }

    @Test
    fun fetchFailurePublishesAnEmptyPayload() {
        coEvery { fetcher.getRewindHomeData() } throws IllegalStateException("boom")

        withViewModel { viewModel ->
            viewModel.load()
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertTrue(state.years.isEmpty(), "a failed fetch must still yield an empty home")
            assertNull(state.global)
        }
    }
}
