package app.n_zik.android.playback.services

/** Parses finite track lengths without accepting malformed, negative or overflowing values. */
internal object SessionDurationMetadata {
    fun fromDurationText(text: String?): Long? {
        val parts = text?.trim()?.split(':') ?: return null
        if (parts.size !in 2..3) return null
        var seconds = 0L
        val maxSeconds = Long.MAX_VALUE / 1000
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty() || part.any { it !in '0'..'9' }) return null
            val component = part.toLongOrNull() ?: return null
            if (index > 0 && component >= 60) return null
            if (component > maxSeconds || seconds > (maxSeconds - component) / 60) return null
            seconds = seconds * 60 + component
        }
        return (seconds * 1000).takeIf { it > 0 }
    }
}
