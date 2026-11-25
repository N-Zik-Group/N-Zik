package app.n_zik.android.components.dialog.settings

import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.ToolbarSurface

internal object QueueToolbarSettingsDialog : CustomToolbarSettingsDialog(
    entryId = "ui.toolbar.queue",
    label = R.string.settings_toolbar_queue_action_bar,
    icon = R.drawable.reorder,
    surfaces = listOf(ToolbarSurface.QUEUE),
    showToolbarTitleSuffix = false,
)

internal val appearanceToolbarSettingsDialogs = listOf(QueueToolbarSettingsDialog)

internal val interfaceToolbarSettingsDialogs
    get() = customToolbarSettingsDialogs.filterNot { it in appearanceToolbarSettingsDialogs }
