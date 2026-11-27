package app.n_zik.android.components.settings.accounts

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.resetPlaylistRefreshDefaults
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.settings.useLoginForSearchKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.network.client.Store
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.extensions.discord.discordAdvancedSettingKeys
import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.playback.services.clearStreamCaches
import app.n_zik.android.utils.autosyncAlbumsKey
import app.n_zik.android.utils.autosyncArtistsKey
import app.n_zik.android.utils.autosyncKey
import app.n_zik.android.utils.autosyncLikesKey
import app.n_zik.android.utils.autosyncPlaylistsKey
import app.n_zik.android.utils.cancelAllSyncs
import app.n_zik.android.utils.enableYouTubeLoginKey
import app.n_zik.android.utils.enableYouTubeSyncKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.encryptedPreferencesUpdateTrigger
import app.n_zik.android.utils.isDiscordPresenceEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.restartActivityKey
import app.n_zik.android.utils.syncBackgroundGuardKey
import app.n_zik.android.utils.syncCooldownKey
import app.n_zik.android.utils.syncDirectionKey
import app.n_zik.android.utils.syncImportEpisodesKey
import app.n_zik.android.utils.syncImportHistoryKey
import app.n_zik.android.utils.syncImportLibrarySongsKey
import app.n_zik.android.utils.syncImportUploadedAlbumsKey
import app.n_zik.android.utils.syncImportUploadedSongsKey
import app.n_zik.android.utils.syncPushAlbumBookmarkKey
import app.n_zik.android.utils.syncPushArtistFollowKey
import app.n_zik.android.utils.syncPushEpisodeKey
import app.n_zik.android.utils.syncPushHistoryKey
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.android.utils.syncPushSongLikeKey
import app.n_zik.android.utils.syncShowDetailsKey
import app.n_zik.android.utils.useLoginForBrowseKey
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YouTubeSession

private val accountProfileSettingKeys = setOf(
    autosyncKey, autosyncArtistsKey, autosyncAlbumsKey, autosyncPlaylistsKey, autosyncLikesKey,
    syncDirectionKey, syncCooldownKey, syncShowDetailsKey, syncBackgroundGuardKey,
    syncPushSongLikeKey, syncPushAlbumBookmarkKey, syncPushArtistFollowKey,
    syncPushPlaylistKey, syncPushHistoryKey, syncPushEpisodeKey,
    syncImportHistoryKey, syncImportLibrarySongsKey, syncImportUploadedSongsKey,
    syncImportUploadedAlbumsKey, syncImportEpisodesKey,
)

private val accountSecureSettingKeys = discordAdvancedSettingKeys + setOf(
    isLastfmScrobblingEnabledKey, isLastfmNowPlayingEnabledKey, isLastfmScrobbleEnabledKey,
    lastfmMinTrackDurationSecondsKey, lastfmScrobbleThresholdPercentKey, lastfmMaxScrobbleDelaySecondsKey,
)

/** Restore account controls while retaining saved credentials, identity and sync cursors. */
internal fun applyAccountsDefaults(context: Context) {
    resetPlaylistRefreshDefaults(context.preferences)
    context.preferences.edit {
        accountProfileSettingKeys.forEach(::remove)
        remove(GITHUB_BACKGROUND_CHECKS_KEY)
        putBoolean(useLoginForBrowseKey, false)
        remove(useLoginForSearchKey)
        putBoolean(restartActivityKey, false)
    }
    context.encryptedPreferences.edit {
        putBoolean(enableYouTubeLoginKey, false)
        putBoolean(enableYouTubeSyncKey, false)
        putBoolean(isDiscordPresenceEnabledKey, false)
        accountSecureSettingKeys.forEach(::remove)
    }
    cancelAllSyncs()
    GitHubAccount.disableUsage(context)
    Innertube.useLoginForBrowse = false
    Innertube.useLoginForSearch = false
    if (Innertube.cookie != null) {
        // Drop the runtime session while keeping the saved account available for re-enabling.
        Store.setSession(YouTubeSession(null, null, null, null))
        clearStreamCaches()
    }
    MainApplication.cookieStatus = MainApplication.CookieStatus.NOT_LOGGED_IN
    encryptedPreferencesUpdateTrigger++
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
                        modifier = Modifier.settingsSearchTarget("accounts.settings_reset.settings_reset"),
                        title = stringResource(R.string.settings_reset),
                        text = stringResource(R.string.settings_reset_account_usage_notice),
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
