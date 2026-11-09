package app.n_zik.android.download.utils

import app.it.fast4x.rimusic.cleanPrefix

/** Cleans the first artist/title boundary of existing labels without rewriting the request. */
internal fun downloadDisplayName(label: String): String =
    label.split(" - ", limit = 2).joinToString(" - ", transform = ::cleanPrefix)
