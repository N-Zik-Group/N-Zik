package it.fast4x.innertube

import com.metrolist.innertubex.InnerTube
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The `useLoginForSearch` setting must steer the single `innerTubeX.search` choke
 * point (every app search — `searchPage`, `searchPageContinuation`, direct `search`
 * — goes through [Innertube.search]):
 *
 * - mapping: ON -> `null` (the library default, authenticated when a session is
 *   loaded — the pre-existing behavior), OFF -> `false` (explicit guest request);
 * - transit: the argument must actually reach the `InnerTube.search` call — removing
 *   it would leave every test green while the OFF toggle silently does nothing.
 */
class InnertubeSearchLoginTest {

    private val innerTubeXStub = mockk<InnerTube>()
    private val setLoginSlot = slot<Boolean?>()
    private lateinit var originalInnerTubeX: InnerTube

    @BeforeEach
    fun setUp() {
        val response = mockk<HttpResponse>()
        coEvery {
            innerTubeXStub.search(
                client = any(),
                query = any(),
                params = any(),
                continuation = any(),
                setLogin = captureNullable(setLoginSlot),
            )
        } returns response
        // Capture the process-wide singleton before swapping it for the stub, so
        // @AfterEach can hand it back to any later test class in the module.
        originalInnerTubeX = Innertube.innerTubeX
        Innertube.innerTubeX = innerTubeXStub
    }

    @AfterEach
    fun tearDown() {
        Innertube.useLoginForSearch = true
        Innertube.innerTubeX = originalInnerTubeX
    }

    @Test
    fun `setting ON maps to the library default setLogin`() {
        assertNull(
            searchSetLogin(true),
            "setting ON must keep the innertubex default (authenticated when a session is loaded)"
        )
    }

    @Test
    fun `setting OFF maps to an explicit guest setLogin`() {
        assertEquals(
            false,
            searchSetLogin(false),
            "setting OFF must force a guest request"
        )
    }

    // runBlocking: no coroutines-test dependency in this module — synchronous entry point
    // into the suspend call under test (AGENTS.md: every runBlocking needs a justification).
    @Test
    fun `search with OFF transits setLogin false to innertubex`() = runBlocking {
        Innertube.useLoginForSearch = false

        Innertube.search(query = "anything")

        assertTrue(setLoginSlot.isCaptured, "Innertube.search must forward the call to innerTubeX.search")
        assertEquals(
            false,
            setLoginSlot.captured,
            "the OFF setting must reach the library call as setLogin = false"
        )
    }

    // runBlocking: see the justification on the OFF transit test above.
    @Test
    fun `search with ON transits the library default setLogin to innertubex`() = runBlocking {
        Innertube.useLoginForSearch = true

        Innertube.search(query = "anything")

        assertTrue(setLoginSlot.isCaptured, "Innertube.search must forward the call to innerTubeX.search")
        assertNull(
            setLoginSlot.captured,
            "the ON setting must keep the library default (setLogin = null)"
        )
    }
}
