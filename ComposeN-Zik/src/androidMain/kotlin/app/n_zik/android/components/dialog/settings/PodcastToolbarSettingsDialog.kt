package app.n_zik.android.components.dialog.settings

import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.ToolbarSurface

internal object PodcastToolbarSettingsDialog : CustomToolbarSettingsDialog(
    entryId = "ui.toolbar.podcast",
    label = R.string.settings_toolbar_online_podcast,
    icon = R.drawable.musical_notes,
    surfaces = listOf(ToolbarSurface.PODCAST),
)
