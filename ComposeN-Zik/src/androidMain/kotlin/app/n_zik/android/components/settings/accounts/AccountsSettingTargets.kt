package app.n_zik.android.components.settings.accounts

import app.n_zik.android.utils.autosyncAlbumsKey
import app.n_zik.android.utils.autosyncArtistsKey
import app.n_zik.android.utils.autosyncLikesKey
import app.n_zik.android.utils.autosyncPlaylistsKey
import app.n_zik.android.utils.discordAvatarKey
import app.n_zik.android.utils.discordPersonalAccessTokenKey
import app.n_zik.android.utils.discordUsernameKey
import app.n_zik.android.utils.enableYouTubeLoginKey
import app.n_zik.android.utils.enableYouTubeSyncKey
import app.n_zik.android.utils.isDiscordPresenceEnabledKey
import app.n_zik.android.utils.streamClientRestartNeededKey
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
import app.n_zik.android.utils.ytAccountChannelHandleKey
import app.n_zik.android.utils.ytAccountEmailKey
import app.n_zik.android.utils.ytAccountNameKey
import app.n_zik.android.utils.ytAccountThumbnailKey
import app.n_zik.android.utils.ytCookieKey
import app.n_zik.android.utils.ytDataSyncIdKey
import app.n_zik.android.utils.ytVisitorDataKey
import app.n_zik.android.components.settings.useLoginForSearchKey
import app.n_zik.android.extensions.discord.discordAdvancedActivityTypeKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedDetailsTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedIdleCloseEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedLargeImageTextKey
import app.n_zik.android.extensions.discord.discordAdvancedNameKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseClearEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPausePresenceEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedRefreshIntervalMsKey
import app.n_zik.android.extensions.discord.discordAdvancedShowArtworkKey
import app.n_zik.android.extensions.discord.discordAdvancedShowDetailsKey
import app.n_zik.android.extensions.discord.discordAdvancedShowSmallImageKey
import app.n_zik.android.extensions.discord.discordAdvancedShowStateKey
import app.n_zik.android.extensions.discord.discordAdvancedShowTimestampsKey
import app.n_zik.android.extensions.discord.discordAdvancedSmallImageTextKey
import app.n_zik.android.extensions.discord.discordAdvancedStateTemplateKey
import app.n_zik.android.extensions.discord.isDiscordAdvancedModeKey
import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey

/** Semantic inventory for later exact search, independent of translated labels and order. */
internal object AccountsSettingTargets {
    val cards = listOf(
        AccountsCardTargets("accounts.card.face",
            preferenceKeys = listOf(),
            actions = listOf("editFace", "openProfiles"),
        ),
        AccountsCardTargets("accounts.card.youtube",
            preferenceKeys = listOf(ytVisitorDataKey, ytDataSyncIdKey, ytCookieKey, ytAccountNameKey, ytAccountEmailKey, ytAccountChannelHandleKey, ytAccountThumbnailKey, enableYouTubeLoginKey, enableYouTubeSyncKey, streamClientRestartNeededKey, useLoginForBrowseKey, useLoginForSearchKey),
            actions = listOf("connect", "disconnect", "clearSyncedData"),
        ),
        AccountsCardTargets("accounts.card.sync",
            preferenceKeys = listOf(enableYouTubeSyncKey, syncDirectionKey, syncCooldownKey, syncShowDetailsKey, syncBackgroundGuardKey),
            actions = listOf("syncNow", "syncStatus"),
        ),
        AccountsCardTargets("accounts.card.autosync",
            preferenceKeys = listOf(autosyncArtistsKey, autosyncAlbumsKey, autosyncPlaylistsKey, autosyncLikesKey),
            actions = listOf(),
        ),
        AccountsCardTargets("accounts.card.sync_import",
            preferenceKeys = listOf(syncImportHistoryKey, syncImportLibrarySongsKey, syncImportUploadedSongsKey, syncImportUploadedAlbumsKey, syncImportEpisodesKey),
            actions = listOf(),
        ),
        AccountsCardTargets("accounts.card.sync_push",
            preferenceKeys = listOf(syncPushSongLikeKey, syncPushAlbumBookmarkKey, syncPushArtistFollowKey, syncPushPlaylistKey, syncPushHistoryKey, syncPushEpisodeKey),
            actions = listOf(),
        ),
        AccountsCardTargets("accounts.card.lastfm",
            preferenceKeys = listOf(isLastfmScrobblingEnabledKey, isLastfmNowPlayingEnabledKey, isLastfmScrobbleEnabledKey, lastfmMinTrackDurationSecondsKey, lastfmScrobbleThresholdPercentKey, lastfmMaxScrobbleDelaySecondsKey, lastfmSessionKey, lastfmUsernameKey, lastfmAvatarUrlKey),
            actions = listOf("connect", "disconnect"),
        ),
        AccountsCardTargets("accounts.card.listentogether",
            preferenceKeys = listOf(),
            actions = listOf("openScreen"),
        ),
        AccountsCardTargets("accounts.card.discord",
            preferenceKeys = listOf(isDiscordPresenceEnabledKey, discordPersonalAccessTokenKey, discordAvatarKey, discordUsernameKey, isDiscordAdvancedModeKey, discordAdvancedActivityTypeKey, discordAdvancedPausePresenceEnabledKey, discordAdvancedNameKey, discordAdvancedStateTemplateKey, discordAdvancedDetailsTemplateKey, discordAdvancedPauseTemplateKey, discordAdvancedButton1EnabledKey, discordAdvancedButton1LabelKey, discordAdvancedButton1UrlKey, discordAdvancedButton2EnabledKey, discordAdvancedButton2LabelKey, discordAdvancedButton2UrlKey, discordAdvancedShowStateKey, discordAdvancedShowDetailsKey, discordAdvancedShowArtworkKey, discordAdvancedShowSmallImageKey, discordAdvancedShowTimestampsKey, discordAdvancedLargeImageTextKey, discordAdvancedSmallImageTextKey, discordAdvancedPauseClearEnabledKey, discordAdvancedIdleCloseEnabledKey, discordAdvancedRefreshIntervalMsKey),
            actions = listOf("connect", "disconnect", "preview", "editTemplate"),
        ),
        AccountsCardTargets("accounts.card.reset",
            preferenceKeys = listOf(),
            actions = listOf("applyAccountsDefaults"),
        ),
    )
    val ids: List<String> = cards.flatMap { card ->
        listOf(card.id) + card.preferenceKeys.map { "${card.id}.setting.$it" } +
            card.actions.map { "${card.id}.action.$it" }
    }
}

internal data class AccountsCardTargets(
    val id: String,
    val preferenceKeys: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
)
