package app.n_zik.android.bridge.audio

private val SINGLE_RANGE = Regex("""([0-9]*)-([0-9]*)""")

/** Which bytes of a file of known length one audio request gets (contract §8.2). */
internal sealed interface ByteRange {
    /** No `Range` header: `200` with the whole file. */
    data object Full : ByteRange

    /** `206 Partial Content` for bytes [start]..[endInclusive]. */
    data class Partial(val start: Long, val endInclusive: Long) : ByteRange {
        val length: Long get() = endInclusive - start + 1
    }

    /** `416 RANGE_NOT_SATISFIABLE`. */
    data object Unsatisfiable : ByteRange
}

/** `Range` header analysis of contract §8.2: `bytes=a-b`, `bytes=a-` and `bytes=-n`. */
internal object ByteRanges {

    /** The only range unit served (`Accept-Ranges`, `Content-Range`). */
    const val BYTES_UNIT = "bytes"

    /**
     * Resolves [header] against a file of [totalLength] bytes. The end of `a-b` is clamped to
     * the last byte; a suffix longer than the file is the whole file (RFC 9110 §14.1.2).
     * A malformed range, several ranges, a start past the end or an empty file are
     * unsatisfiable. Another unit than `bytes` is ignored, as RFC 9110 allows.
     */
    fun resolve(header: String?, totalLength: Long): ByteRange {
        if (header == null) return ByteRange.Full
        val trimmed = header.trim()
        val unitEnd = trimmed.indexOf('=')
        if (unitEnd < 0) return ByteRange.Unsatisfiable
        if (!trimmed.substring(0, unitEnd).trim().equals(BYTES_UNIT, ignoreCase = true)) return ByteRange.Full
        val match = SINGLE_RANGE.matchEntire(trimmed.substring(unitEnd + 1).trim()) ?: return ByteRange.Unsatisfiable
        val (startText, endText) = match.destructured
        if (totalLength <= 0) return ByteRange.Unsatisfiable
        val last = totalLength - 1
        return when {
            startText.isEmpty() && endText.isEmpty() -> ByteRange.Unsatisfiable
            // Suffix range: the last n bytes
            startText.isEmpty() -> {
                val suffix = endText.toLongOrNull() ?: Long.MAX_VALUE
                if (suffix == 0L) ByteRange.Unsatisfiable else ByteRange.Partial(maxOf(0L, totalLength - suffix), last)
            }
            else -> {
                val start = startText.toLongOrNull() ?: return ByteRange.Unsatisfiable
                if (start > last) return ByteRange.Unsatisfiable
                val end = if (endText.isEmpty()) last else (endText.toLongOrNull() ?: Long.MAX_VALUE)
                if (end < start) ByteRange.Unsatisfiable else ByteRange.Partial(start, minOf(end, last))
            }
        }
    }

    /** `Content-Range` of a `206`. */
    fun contentRange(range: ByteRange.Partial, totalLength: Long): String =
        "$BYTES_UNIT ${range.start}-${range.endInclusive}/$totalLength"

    /** `Content-Range` of a `416` (RFC 9110 §15.5.17). */
    fun unsatisfiedRange(totalLength: Long): String = "$BYTES_UNIT */$totalLength"
}
