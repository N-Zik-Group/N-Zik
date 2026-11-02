package app.n_zik.android.components.artist

/** Uses only the audience statistics returned by the artist provider. */
internal fun artistStatisticsText(subscribers: String?, listeners: String?): String =
    listOfNotNull(subscribers?.takeIf { it.isNotBlank() }, listeners?.takeIf { it.isNotBlank() })
        .joinToString(" • ")
