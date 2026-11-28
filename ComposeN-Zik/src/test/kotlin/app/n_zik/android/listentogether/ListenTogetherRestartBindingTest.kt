package app.n_zik.android.listentogether

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListenTogetherRestartBindingTest {
    @After
    fun releaseBridge() {
        ListenTogetherPlayerBridge.release()
    }

    @Test
    fun reconnectsAfterTheRestartEvenWhenTheStopFails() = runTest {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        ListenTogetherPlayerBridge.ensureBound(context)
        val failure = IllegalStateException("Stop failed")

        val thrown = runCatching {
            ListenTogetherPlayerBridge.withoutPlayerServiceBinding {
                assertEquals(listOf("bind", "unbind"), context.events)
                throw failure
            }
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(listOf("bind", "unbind", "bind"), context.events)
    }

    private class BindingContext(base: Context) : ContextWrapper(base) {
        val events = mutableListOf<String>()

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            events += "bind"
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            events += "unbind"
        }
    }
}
