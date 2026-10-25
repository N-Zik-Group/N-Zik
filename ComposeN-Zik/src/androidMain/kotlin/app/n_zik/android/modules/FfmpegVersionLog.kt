package app.n_zik.android.modules

/**
 * Reduces FFmpeg's `-version` banner to the user-relevant lines: the version
 * line, the build configuration and the per-library versions (the rest of
 * the banner is build metadata noise). Pure function, unit-tested on the JVM.
 */
object FfmpegVersionLog {
    fun parse(log: String): String =
        log.lineSequence()
            .filter { line ->
                line.startsWith("ffmpeg version") ||
                    line.startsWith("configuration:") ||
                    line.startsWith("libav") ||
                    line.startsWith("libsw")
            }
            .joinToString("\n")
}
