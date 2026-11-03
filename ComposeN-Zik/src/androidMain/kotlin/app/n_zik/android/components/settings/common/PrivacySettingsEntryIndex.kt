package app.n_zik.android.components.settings.common

import app.n_zik.android.R

val PrivacySettingsEntryTargets = listOf(
    SettingsEntryTarget("privacy.screen.block_capture", 9, R.string.privacy_screen_protection, R.string.privacy_block_screenshots, listOf(R.string.privacy_block_screenshots_description)),
    SettingsEntryTarget("privacy.permissions.pause", 9, R.string.permissions_usage_journal, R.string.permissions_journal_pause, listOf(R.string.permissions_journal_pause_description)),
    SettingsEntryTarget("privacy.permissions.open", 9, R.string.permissions_usage_journal, R.string.permissions_journal_entries,
        listOf(R.string.permissions_journal_description, R.string.permissions_journal_entries_description,
            R.string.permissions_journal_clear, R.string.permissions_journal_clear_description)),
)
