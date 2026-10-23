package app.n_zik.android.extensions.lastfm

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests the setup/config key classification used by the player service prefs listener to decide between manager (re)creation and runtime reconfiguration. */
class LastFmPrefsTest {

    @Test
    fun `setup keys trigger manager recreation`() {
        assertTrue(isLastFmSetupKey(isLastfmScrobblingEnabledKey))
        assertTrue(isLastFmSetupKey(lastfmSessionKey))
        assertFalse(isLastFmSetupKey(isLastfmNowPlayingEnabledKey))
        assertFalse(isLastFmSetupKey(isLastfmScrobbleEnabledKey))
        assertFalse(isLastFmSetupKey(lastfmMinTrackDurationSecondsKey))
        assertFalse(isLastFmSetupKey(lastfmScrobbleThresholdPercentKey))
        assertFalse(isLastFmSetupKey(lastfmMaxScrobbleDelaySecondsKey))
        assertFalse(isLastFmSetupKey(lastfmUsernameKey))
        assertFalse(isLastFmSetupKey(lastfmAvatarUrlKey))
        assertFalse(isLastFmSetupKey(null))
        assertFalse(isLastFmSetupKey("other"))
    }

    @Test
    fun `sub-option keys trigger runtime reconfiguration only`() {
        assertTrue(isLastFmConfigKey(isLastfmNowPlayingEnabledKey))
        assertTrue(isLastFmConfigKey(isLastfmScrobbleEnabledKey))
        assertTrue(isLastFmConfigKey(lastfmMinTrackDurationSecondsKey))
        assertTrue(isLastFmConfigKey(lastfmScrobbleThresholdPercentKey))
        assertTrue(isLastFmConfigKey(lastfmMaxScrobbleDelaySecondsKey))
        assertFalse(isLastFmConfigKey(isLastfmScrobblingEnabledKey))
        assertFalse(isLastFmConfigKey(lastfmSessionKey))
        assertFalse(isLastFmConfigKey(lastfmUsernameKey))
        assertFalse(isLastFmConfigKey(null))
        assertFalse(isLastFmConfigKey("other"))
    }
}
