package app.n_zik.android.components.dialog.logs

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Issue #606 (gh-606 G1): the log read behind the "Export logs" dialog moved off Main into a pure
 * function of (logs directory, option). These tests pin the exported text so the move stays a
 * no-behavior-change refactor, and [loadLogContent] pins that the read is dispatched off the
 * caller's thread. The Compose wiring (Button onClick / launcher callback) that calls it is not
 * JVM-testable.
 */
class ReadLogContentTest {

    private fun File.debugLog(text: String) = File(this, "N-Zik_log.txt").writeText(text)
    private fun File.crashLog(text: String) = File(this, "N-Zik_crash_log.txt").writeText(text)

    @Test
    fun `option 0 returns the debug log verbatim`(@TempDir dir: File) {
        dir.debugLog("debug line")
        dir.crashLog("crash line")

        assertEquals("debug line", readLogContent(dir, 0))
    }

    @Test
    fun `option 1 returns the crash log verbatim`(@TempDir dir: File) {
        dir.debugLog("debug line")
        dir.crashLog("crash line")

        assertEquals("crash line", readLogContent(dir, 1))
    }

    @Test
    fun `option 2 returns both logs with headers separated by a blank line`(@TempDir dir: File) {
        dir.debugLog("debug line")
        dir.crashLog("crash line")

        assertEquals(
            "=== DEBUG LOG ===\ndebug line\n\n=== CRASH LOG ===\ncrash line",
            readLogContent(dir, 2)
        )
    }

    @Test
    fun `option 2 with only the crash log present returns just that section`(@TempDir dir: File) {
        dir.crashLog("crash line")

        assertEquals("=== CRASH LOG ===\ncrash line", readLogContent(dir, 2))
    }

    @Test
    fun `option 2 with only the debug log present returns just that section`(@TempDir dir: File) {
        dir.debugLog("debug line")

        assertEquals("=== DEBUG LOG ===\ndebug line", readLogContent(dir, 2))
    }

    @Test
    fun `returns null when the requested log file is absent`(@TempDir dir: File) {
        dir.crashLog("crash line")

        assertNull(readLogContent(dir, 0))
        assertNull(readLogContent(File(dir, "missing"), 1))
        assertNull(readLogContent(File(dir, "missing"), 2))
    }

    @Test
    fun `returns null for an unknown option`(@TempDir dir: File) {
        dir.debugLog("debug line")

        assertNull(readLogContent(dir, 3))
    }

    @Test
    fun `debug and both options grey out with the debug log except the crash option`() {
        // Debug on: every option is selectable
        assertFalse(isLogExportOptionDisabled(0, true))
        assertFalse(isLogExportOptionDisabled(1, true))
        assertFalse(isLogExportOptionDisabled(2, true))
        // Debug off: the debug log and the combined export are meaningless, the crash
        // option stays available
        assertTrue(isLogExportOptionDisabled(0, false))
        assertFalse(isLogExportOptionDisabled(1, false))
        assertTrue(isLogExportOptionDisabled(2, false))
    }

    @Test
    fun `logExportFileName maps each option to its export file name`() {
        assertEquals("N-Zik_debug_log.txt", logExportFileName(0))
        assertEquals("N-Zik_crash_log.txt", logExportFileName(1))
        // "both" (2) and any unknown option fall back to the combined name
        assertEquals("N-Zik_logs.txt", logExportFileName(2))
        assertEquals("N-Zik_logs.txt", logExportFileName(7))
    }

    @Test
    fun `loadLogContent runs the read off the caller thread and returns its result`(@TempDir dir: File) = runBlocking {
        val callerThread = Thread.currentThread().name
        var readThread: String? = null

        val content = loadLogContent(dir, 2) { logsDir, option ->
            readThread = Thread.currentThread().name
            assertEquals(dir, logsDir)
            assertEquals(2, option)
            "read result"
        }

        assertEquals("read result", content)
        assertNotEquals(callerThread, readThread)
    }
}
