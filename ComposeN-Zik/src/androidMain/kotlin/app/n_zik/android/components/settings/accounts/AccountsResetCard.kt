package app.n_zik.android.components.settings.accounts

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.restartActivityKey
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.tab.Search
import app.n_zik.android.extensions.discord.discordAdvancedActivityTypeKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedDetailsTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedNameKey
import app.n_zik.android.extensions.discord.discordAdvancedPausePresenceEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedStateTemplateKey
import app.n_zik.android.extensions.discord.isDiscordAdvancedModeKey

/** Applies only the existing Accounts reset set; saved credentials remain intact. */
internal fun applyAccountsDefaults(context: Context) {
    context.preferences.edit {
        putBoolean(restartActivityKey, false)
    }
    context.encryptedPreferences.edit {
        putBoolean(enableYouTubeLoginKey, false)
        putBoolean(enableYouTubeSyncKey, false)
        putBoolean(isDiscordPresenceEnabledKey, false)
        putBoolean(isDiscordAdvancedModeKey, false)
        putInt(discordAdvancedActivityTypeKey, 2)
        putBoolean(discordAdvancedPausePresenceEnabledKey, true)
        putString(discordAdvancedNameKey, "")
        putString(discordAdvancedStateTemplateKey, "")
        putString(discordAdvancedDetailsTemplateKey, "")
        putString(discordAdvancedPauseTemplateKey, "")
        putBoolean(discordAdvancedButton1EnabledKey, true)
        putString(discordAdvancedButton1LabelKey, "")
        putString(discordAdvancedButton1UrlKey, "")
        putBoolean(discordAdvancedButton2EnabledKey, true)
        putString(discordAdvancedButton2LabelKey, "")
        putString(discordAdvancedButton2UrlKey, "")
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun AccountsResetCard(search: Search) {
    val context = LocalContext.current
    val searchCtx_Reset = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
    AnimatedVisibility(
        visible = searchCtx_Reset,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            title = stringResource(R.string.settings_reset),
            icon = R.drawable.refresh,
            content = {

                if (search.inputValue.isBlank() || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true) || stringResource(R.string.settings_reset).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.settings_reset),
                        text = stringResource(R.string.settings_restore_default_settings),
                        icon = R.drawable.refresh,
                        onClick = {
                            applyAccountsDefaults(context)
                            Toaster.done()
                        }
                    )
                }
            }
        )
    }
}
