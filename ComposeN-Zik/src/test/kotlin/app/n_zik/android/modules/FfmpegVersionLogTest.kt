package app.n_zik.android.modules

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for [FfmpegVersionLog]. */
class FfmpegVersionLogTest {

    private val sampleLog = """
        ffmpeg version 6.0.1-N-Zik Copyright (c) 2000-2024 the FFmpeg developers
        built with android 21 clang version 14.0.6
        configuration: --enable-gpl --enable-libopus
        libavutil      58. 29.100 / 58. 29.100
        libavcodec     60. 31.102 / 60. 31.102
        libavformat    60. 16.100 / 60. 16.100
        libavdevice    60.  3.100 / 60.  3.100
        libavfilter    9. 24.100 / 9. 24.100
        libswscale     7.  5.100 / 7.  5.100
        libswresample  4. 12.100 / 4. 12.100
        The program can only be run if a hardware accelerated backend is available.
        License notice line.
    """.trimIndent()

    @Test
    fun `parse should keep version configuration and library lines`() {
        val parsed = FfmpegVersionLog.parse(sampleLog)

        assertTrue(parsed.startsWith("ffmpeg version 6.0.1"))
        assertTrue(parsed.contains("configuration:"))
        assertTrue(parsed.contains("libavcodec     60. 31.102 / 60. 31.102"))
        assertTrue(parsed.contains("libswresample  4. 12.100 / 4. 12.100"))
    }

    @Test
    fun `parse should drop non-version lines`() {
        val parsed = FfmpegVersionLog.parse(sampleLog)

        assertFalse(parsed.contains("built with"))
        assertFalse(parsed.contains("The program can only be run"))
        assertFalse(parsed.contains("License notice"))
    }
}
