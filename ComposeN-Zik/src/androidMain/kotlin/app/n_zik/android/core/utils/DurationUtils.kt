package app.n_zik.android.core.utils

object DurationUtils {

    /**
     * Human-readable format of "HH:mm:ss"
     */
    private val hhmmssRegex = Regex("^\\d{1,2}:\\d{2}:\\d{2}$")

    /**
     * Human-readable format of "mm:ss"
     */
    private val mmssRegex = Regex("^\\d{1,2}:\\d{2}$")

    fun isHumanReadable( duration: String ) =
        hhmmssRegex.matches( duration ) || mmssRegex.matches( duration )
}


