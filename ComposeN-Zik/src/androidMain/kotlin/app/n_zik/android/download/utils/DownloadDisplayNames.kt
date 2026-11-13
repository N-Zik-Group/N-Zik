package app.n_zik.android.download.utils

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix

private const val LABEL_SEPARATOR = " - "
// A trailing NUL marks a display-ready label in the Media3 request's opaque data bytes.
private const val DISPLAY_LABEL_V1 = "\u0000"

internal fun encodeDownloadDisplayName(label: String): ByteArray =
    (label + DISPLAY_LABEL_V1).encodeToByteArray()

/** Cleans legacy request labels, including modified titles after an artist containing separators. */
internal fun downloadDisplayName(label: String): String {
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
