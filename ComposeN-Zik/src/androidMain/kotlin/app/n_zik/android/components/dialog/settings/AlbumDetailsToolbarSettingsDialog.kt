package app.n_zik.android.components.dialog.settings

import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.ToolbarSurface

internal object AlbumDetailsToolbarSettingsDialog : CustomToolbarSettingsDialog(
    entryId = "ui.toolbar.album_details",
    label = R.string.settings_toolbar_album_details,
    icon = R.drawable.album,
    surfaces = listOf(ToolbarSurface.ALBUM_ONLINE, ToolbarSurface.ALBUM_DEVICE),
)
