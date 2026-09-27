package app.n_zik.android.utils.debug

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DebugLogsTest {

    @TempDir
    lateinit var logsDir: File

    @Test
    fun `debugLogFiles returns the log and crash log inside the logs dir`() {
        val files = debugLogFiles(logsDir)

        assertEquals(2, files.size)
        assertEquals(File(logsDir, "N-Zik_log.txt"), files[0])
        assertEquals(File(logsDir, "N-Zik_crash_log.txt"), files[1])
    }

    @Test
    fun `purgeDebugLogs deletes only the debug log and leaves the crash log intact`() {
        File(logsDir, "N-Zik_log.txt").createNewFile()
        File(logsDir, "N-Zik_crash_log.txt").createNewFile()
        File(logsDir, "other.txt").createNewFile()

        val deleted = purgeDebugLogs(logsDir)

        assertEquals(1, deleted)
        assertFalse(File(logsDir, "N-Zik_log.txt").exists())
        // The crash log is captured independently of the debug switch and must survive
        assertTrue(File(logsDir, "N-Zik_crash_log.txt").exists())
        assertTrue(File(logsDir, "other.txt").exists())
    }

    @Test
    fun `purgeDebugLogs returns zero when no log files exist`() {
        assertEquals(0, purgeDebugLogs(logsDir))
    }
}
