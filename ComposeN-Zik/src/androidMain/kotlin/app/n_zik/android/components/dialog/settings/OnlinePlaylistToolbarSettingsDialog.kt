package app.n_zik.android.components.dialog.settings

import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.ToolbarSurface

internal object OnlinePlaylistToolbarSettingsDialog : CustomToolbarSettingsDialog(
    entryId = "ui.toolbar.online_playlist",
    label = R.string.settings_toolbar_online_playlist,
    icon = R.drawable.playlist,
    surfaces = listOf(ToolbarSurface.ONLINE_PLAYLIST),
)
