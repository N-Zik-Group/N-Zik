package app.n_zik.android.components.settings.common

import app.n_zik.android.R

val PrivacySettingsEntryTargets = listOf(
    SettingsEntryTarget("privacy.screen.block_capture", 9, R.string.privacy_screen_protection, R.string.privacy_block_screenshots, listOf(R.string.privacy_block_screenshots_description)),
    SettingsEntryTarget("privacy.permissions.pause", 9, R.string.permission_usage_journal, R.string.permission_journal_pause, listOf(R.string.permission_journal_pause_description)),
    SettingsEntryTarget("privacy.permissions.open", 9, R.string.permission_usage_journal, R.string.permission_journal_entries,
        listOf(R.string.permission_journal_description, R.string.permission_journal_entries_description,
            R.string.permission_journal_clear, R.string.permission_journal_clear_description)),
)
