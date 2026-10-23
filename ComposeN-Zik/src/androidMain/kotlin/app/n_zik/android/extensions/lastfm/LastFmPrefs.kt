package app.n_zik.android.extensions.lastfm

// Master "Last.fm" switch (legacy 574 key, name kept for zero-migration
// compatibility; it gates the whole card, not only scrobbling)
const val isLastfmScrobblingEnabledKey = "isLastfmScrobblingEnabled"
const val lastfmSessionKey = "lastfmSessionKey"
const val lastfmUsernameKey = "lastfmUsername"
const val lastfmAvatarUrlKey = "lastfmAvatarUrl"

// Sub-options (defaults = previous hard-coded behavior)
const val isLastfmNowPlayingEnabledKey = "isLastfmNowPlayingEnabled"
const val isLastfmScrobbleEnabledKey = "isLastfmScrobbleEnabled"
const val lastfmMinTrackDurationSecondsKey = "lastfmMinTrackDurationSeconds"
const val lastfmScrobbleThresholdPercentKey = "lastfmScrobbleThresholdPercent"
const val lastfmMaxScrobbleDelaySecondsKey = "lastfmMaxScrobbleDelaySeconds"

/**
 * Keys whose change (re)creates or destroys the scrobble manager.
 */
internal fun isLastFmSetupKey(key: String?): Boolean =
    key == isLastfmScrobblingEnabledKey || key == lastfmSessionKey

/** Sub-option keys applied at runtime via onConfigChanged without recreating the manager. */
internal fun isLastFmConfigKey(key: String?): Boolean =
    key == isLastfmNowPlayingEnabledKey ||
        key == isLastfmScrobbleEnabledKey ||
        key == lastfmMinTrackDurationSecondsKey ||
        key == lastfmScrobbleThresholdPercentKey ||
        key == lastfmMaxScrobbleDelaySecondsKey
