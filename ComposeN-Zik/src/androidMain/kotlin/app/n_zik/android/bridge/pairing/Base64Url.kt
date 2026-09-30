package app.n_zik.android.bridge.pairing

/**
 * Base64url without padding (RFC 4648 §5), as used by every bridge identifier (contract §2).
 * `java.util.Base64` needs API 26 and `android.util.Base64` is unavailable in JVM tests.
 */
internal object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(bytes: ByteArray): String = buildString((bytes.size * 4 + 2) / 3) {
        var index = 0
        while (index < bytes.size) {
            val remaining = bytes.size - index
            val b0 = bytes[index].toInt() and 0xFF
            val b1 = if (remaining > 1) bytes[index + 1].toInt() and 0xFF else 0
            val b2 = if (remaining > 2) bytes[index + 2].toInt() and 0xFF else 0
            append(ALPHABET[b0 ushr 2])
            append(ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            if (remaining > 1) append(ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
            if (remaining > 2) append(ALPHABET[b2 and 0x3F])
            index += 3
        }
    }
}
