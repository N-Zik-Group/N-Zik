package app.n_zik.android.components.dialog.settings

import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.ToolbarSurface

internal object ArtistDetailsToolbarSettingsDialog : CustomToolbarSettingsDialog(
    entryId = "ui.toolbar.artist_details",
    label = R.string.settings_toolbar_artist_details,
    icon = R.drawable.artist,
    surfaces = listOf(ToolbarSurface.ARTIST_OVERVIEW, ToolbarSurface.ARTIST_LIBRARY, ToolbarSurface.ARTIST_DEVICE),
)
