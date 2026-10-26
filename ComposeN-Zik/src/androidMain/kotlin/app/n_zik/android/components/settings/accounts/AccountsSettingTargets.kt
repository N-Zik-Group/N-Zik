package app.n_zik.android.components.settings.accounts

import app.it.fast4x.rimusic.utils.autosyncAlbumsKey
import app.it.fast4x.rimusic.utils.autosyncArtistsKey
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.autosyncPlaylistsKey
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.streamClientRestartNeededKey
import app.it.fast4x.rimusic.utils.syncBackgroundGuardKey
import app.it.fast4x.rimusic.utils.syncCooldownKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.syncImportEpisodesKey
import app.it.fast4x.rimusic.utils.syncImportHistoryKey
import app.it.fast4x.rimusic.utils.syncImportLibrarySongsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedAlbumsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedSongsKey
import app.it.fast4x.rimusic.utils.syncPushAlbumBookmarkKey
import app.it.fast4x.rimusic.utils.syncPushArtistFollowKey
import app.it.fast4x.rimusic.utils.syncPushEpisodeKey
import app.it.fast4x.rimusic.utils.syncPushHistoryKey
import app.it.fast4x.rimusic.utils.syncPushPlaylistKey
import app.it.fast4x.rimusic.utils.syncPushSongLikeKey
import app.it.fast4x.rimusic.utils.syncShowDetailsKey
import app.it.fast4x.rimusic.utils.useLoginForBrowseKey
import app.it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import app.it.fast4x.rimusic.utils.ytAccountEmailKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
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
