package app.n_zik.android.components.dialog.settings

import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.ToolbarSurface

internal object HeaderToolbarSettingsDialog : CustomToolbarSettingsDialog(
    entryId = "ui.toolbar.header",
    label = R.string.settings_toolbar_global_header,
    icon = R.drawable.person,
    surfaces = listOf(ToolbarSurface.HEADER),
)
