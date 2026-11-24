package app.n_zik.android.download.utils

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix

private const val LABEL_SEPARATOR = " - "
// A trailing NUL marks a display-ready label in the Media3 request's opaque data bytes.
private const val DISPLAY_LABEL_V1 = "\u0000"

// The request data also carries an opaque ID to match manager events to the queued add.
private const val ADD_TOKEN_LENGTH = 36
private const val DOWNLOAD_ADD_MARKER = "\u0000nzik-add:"

internal fun encodeDownloadDisplayName(label: String, addToken: String? = null): ByteArray =
    (label + (addToken?.let { DOWNLOAD_ADD_MARKER + it } ?: DISPLAY_LABEL_V1)).encodeToByteArray()

internal fun downloadAddToken(data: ByteArray): String? =
    data.decodeToString().substringAfterLast(DOWNLOAD_ADD_MARKER, "").takeIf { it.length == ADD_TOKEN_LENGTH }

/** Cleans legacy request labels, including modified titles after an artist containing separators. */
internal fun downloadDisplayName(label: String): String {
    val addMarker = label.lastIndexOf(DOWNLOAD_ADD_MARKER)
    if (addMarker >= 0 && label.length - addMarker - DOWNLOAD_ADD_MARKER.length == ADD_TOKEN_LENGTH) {
        return label.substring(0, addMarker)
    }
    if (label.endsWith(DISPLAY_LABEL_V1)) return label.removeSuffix(DISPLAY_LABEL_V1)

    val artistEnd = label.indexOf(LABEL_SEPARATOR)
    if (artistEnd < 0) return cleanPrefix(label)

    var boundary = artistEnd
    while (boundary >= 0) {
        val title = label.substring(boundary + LABEL_SEPARATOR.length)
        val cleaned = cleanPrefix(title)
        if (cleaned != title && cleaned.firstOrNull()?.isWhitespace() == false) {
            val markers = title.removeSuffix(cleaned)
            // After the first separator, require evidence of the stored edit marker.
            if (boundary == artistEnd || MODIFIED_PREFIX in markers) {
                return cleanPrefix(label.substring(0, boundary)) + LABEL_SEPARATOR + cleaned
            }
        }
        boundary = label.indexOf(LABEL_SEPARATOR, boundary + LABEL_SEPARATOR.length)
    }
    return cleanPrefix(label)
}
