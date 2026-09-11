package app.n_zik.android.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import app.it.fast4x.rimusic.enums.NavRoutes
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerQueueInterceptorTest {

    private val queueDestination = mockk<NavDestination> {
        every { route } returns NavRoutes.queue.name
    }

    private val homeDestination = mockk<NavDestination> {
        every { route } returns NavRoutes.home.name
    }

    private fun attachedListener(
        navController: NavController,
        onQueueRequested: () -> Unit = {},
    ): NavController.OnDestinationChangedListener {
        val listenerSlot = slot<NavController.OnDestinationChangedListener>()
        every { navController.addOnDestinationChangedListener(capture(listenerSlot)) } just Runs
        MiniPlayerQueueInterceptor(navController, onQueueRequested).attach()
        return listenerSlot.captured
    }

    @Test
    fun `queue destination while current triggers callback and pop`() {
        val navController = mockk<NavController>()
        every { navController.currentDestination } returns queueDestination
        every { navController.popBackStack() } returns true
        var queueRequested = false

        val listener = attachedListener(navController) { queueRequested = true }

        listener.onDestinationChanged(navController, queueDestination, null)

        assertTrue(queueRequested)
        verify(exactly = 1) { navController.popBackStack() }
    }

    @Test
    fun `other route is not intercepted`() {
        val navController = mockk<NavController>()
        var queueRequested = false

        val listener = attachedListener(navController) { queueRequested = true }

        listener.onDestinationChanged(navController, homeDestination, null)

        assertFalse(queueRequested)
        verify(exactly = 0) { navController.popBackStack() }
    }

    @Test
    fun `queue destination no longer current is ignored`() {
        val navController = mockk<NavController>()
        every { navController.currentDestination } returns homeDestination
        var queueRequested = false

        val listener = attachedListener(navController) { queueRequested = true }

        listener.onDestinationChanged(navController, queueDestination, null)

        assertFalse(queueRequested)
        verify(exactly = 0) { navController.popBackStack() }
    }

    @Test
    fun `attach registers and detach unregisters the same listener`() {
        val navController = mockk<NavController>()
        val listenerSlot = slot<NavController.OnDestinationChangedListener>()
        every { navController.addOnDestinationChangedListener(capture(listenerSlot)) } just Runs
        every { navController.removeOnDestinationChangedListener(any()) } just Runs

        val interceptor = MiniPlayerQueueInterceptor(navController) {}

        interceptor.attach()
        verify(exactly = 1) { navController.addOnDestinationChangedListener(any()) }

        interceptor.detach()
        verify(exactly = 1) { navController.removeOnDestinationChangedListener(listenerSlot.captured) }
    }
}
