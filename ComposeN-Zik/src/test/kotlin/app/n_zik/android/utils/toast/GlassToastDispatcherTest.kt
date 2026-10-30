package app.n_zik.android.utils.toast

import android.graphics.drawable.ColorDrawable
import android.widget.Toast
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class GlassToastDispatcherTest {
    @Test fun `no host and rejected host allow one native delivery`() {
        val owner = Any()
        var nativeDeliveries = 0
        fun deliver() {
            if (!GlassToastDispatcher.tryShow("text", Toast.LENGTH_SHORT, null, 1, 2)) nativeDeliveries++
        }
        deliver()
        GlassToastDispatcher.register(owner) { false }
        try { deliver() } finally { GlassToastDispatcher.unregister(owner) }
        assertEquals(2, nativeDeliveries)
    }

    @Test fun `top live host consumes once preserving payload and stops further dispatch`() {
        val older = Any()
        val newer = Any()
        var olderDeliveries = 0
        val delivered = mutableListOf<GlassToastMessage>()
        val icon = ColorDrawable(0xff123456.toInt())
        GlassToastDispatcher.register(older) { olderDeliveries++; true }
        GlassToastDispatcher.register(newer) { delivered.add(it); true }
        try {
            assertTrue(GlassToastDispatcher.tryShow("Exact text", Toast.LENGTH_LONG, icon, 3, 4))
            assertEquals(0, olderDeliveries)
            assertEquals(1, delivered.size)
            assertEquals("Exact text", delivered.single().text)
            assertSame(icon, delivered.single().icon)
            assertEquals(Toast.LENGTH_LONG, delivered.single().duration)
            assertEquals(3, delivered.single().background)
            assertEquals(4, delivered.single().foreground)
        } finally {
            GlassToastDispatcher.unregister(newer)
            GlassToastDispatcher.unregister(older)
        }
    }

    @Test fun `durations match Android short and long timeouts and repeats have distinct identity`() {
        assertEquals(2000L, toastDurationMillis(Toast.LENGTH_SHORT))
        assertEquals(3500L, toastDurationMillis(Toast.LENGTH_LONG))
        assertNotEquals(GlassToastMessage("same", 0, null, 1, 2), GlassToastMessage("same", 0, null, 1, 2))
    }

    @Test fun `bursts preserve active and four newest pending without duplicate backlog`() {
        val queue = mutableListOf<GlassToastMessage>()
        val active = GlassToastMessage("active", Toast.LENGTH_LONG, null, 1, 2)
        enqueueGlassToast(queue, active)
        repeat(100) { enqueueGlassToast(queue, GlassToastMessage("message $it", Toast.LENGTH_SHORT, null, 1, 2)) }
        assertSame(active, queue.first())
        assertEquals(listOf("active", "message 96", "message 97", "message 98", "message 99"), queue.map { it.text })
        enqueueGlassToast(queue, GlassToastMessage("message 99", Toast.LENGTH_SHORT, null, 1, 2))
        enqueueGlassToast(queue, GlassToastMessage("active", Toast.LENGTH_LONG, null, 1, 2))
        assertEquals(5, queue.size)
        assertSame(active, queue.first())
    }
}
