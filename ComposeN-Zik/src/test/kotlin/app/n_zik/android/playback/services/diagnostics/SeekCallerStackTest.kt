package app.n_zik.android.playback.services.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Issue #881 (gh-881): caller-stack extraction for the SEEK_CALL line (spec S1) — framework
 * frames are dropped, the first N app frames are kept in `class.method` form, joined with
 * ` <- `, and the result is capped.
 */
class SeekCallerStackTest {

    private fun frame(className: String, methodName: String) =
        StackTraceElement(className, methodName, "File.java", 42)

    @Test
    fun `framework frames are filtered out and app frames kept in order`() {
        val stack = seekCallerStack(
            arrayOf(
                frame("app.n_zik.android.playback.services.PlayerServiceModern", "actionPlay"),
                frame("androidx.compose.runtime.snapshots.Snapshot", "apply"),
                frame("android.os.Handler", "dispatchMessage"),
                frame("kotlinx.coroutines.EventLoop", "processEvent"),
                frame("com.android.internal.os.ZygoteInit", "main"),
            ),
        )
        assertEquals("app.n_zik.android.playback.services.PlayerServiceModern.actionPlay", stack)
    }

    @Test
    fun `media3 and compose frames are filtered out`() {
        val stack = seekCallerStack(
            arrayOf(
                frame("androidx.media3.session.MediaSessionImpl", "seekTo"),
                frame("androidx.compose.runtime.Composer", "invoke"),
                frame("app.n_zik.android.components.ui.screens.player.PlayerKt", "Player"),
                frame("android.app.Activity", "onResume"),
            ),
        )
        assertEquals("app.n_zik.android.components.ui.screens.player.PlayerKt.Player", stack)
    }

    @Test
    fun `at most maxFrames frames are kept`() {
        val trace = (1..20).map { frame("app.app.Frame$it", "m$it") }.toTypedArray()
        val stack = seekCallerStack(trace, maxFrames = 4)
        assertEquals(
            "app.app.Frame1.m1 <- app.app.Frame2.m2 <- app.app.Frame3.m3 <- app.app.Frame4.m4",
            stack,
        )
    }

    @Test
    fun `the joined result is capped at maxChars`() {
        val trace = (1..10).map { frame("app.app.AVeryLongClassNameForTesting$it", "aVeryLongMethodNameForTesting$it") }
            .toTypedArray()
        val stack = seekCallerStack(trace, maxFrames = 10, maxChars = 50)
        assertEquals(50 + 1, stack.length) // capped + the ellipsis
        assertTrue(stack.endsWith("…"))
    }

    @Test
    fun `all-framework traces yield an empty result`() {
        val stack = seekCallerStack(
            arrayOf(
                frame("android.os.Looper", "loopOnce"),
                frame("dalvik.system.VMStack", "getThreadStackTrace"),
                frame("java.lang.Thread", "getStackTrace"),
            ),
        )
        assertEquals("", stack)
    }

    @Test
    fun `an empty trace yields an empty result`() {
        assertEquals("", seekCallerStack(emptyArray()))
    }

    @Test
    fun `frame names never carry method arguments`() {
        // Whatever the caller is, only class + method names may reach the log line.
        val stack = seekCallerStack(arrayOf(frame("app.app.Caller", "seekTo")))
        assertEquals("app.app.Caller.seekTo", stack)
    }
}
