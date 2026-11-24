package app.n_zik.android.download.utils

import android.content.SharedPreferences
import androidx.media3.exoplayer.offline.Download
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import app.n_zik.android.utils.autoDownloadSongKey
import app.n_zik.android.utils.autoDownloadSongWhenAlbumBookmarkedKey
import app.n_zik.android.utils.autoDownloadSongWhenLikedKey
import app.n_zik.android.utils.autoDownloadSongWhenPlaylistBookmarkedKey

class FavoriteAutoDownloadTest {
    @Test
    fun `each favorite category uses its own setting under the profile master switch`() {
        val values = mutableMapOf(autoDownloadSongWhenLikedKey to true)
        val preferences = mockk<SharedPreferences>()
        every { preferences.getBoolean(any(), false) } answers { values[firstArg<String>()] ?: false }

        assertFalse(isFavoriteAutoDownloadEnabled(preferences, autoDownloadSongWhenLikedKey))
        values[autoDownloadSongKey] = true
        assertTrue(isFavoriteAutoDownloadEnabled(preferences, autoDownloadSongWhenLikedKey))
        assertFalse(isFavoriteAutoDownloadEnabled(preferences, autoDownloadSongWhenAlbumBookmarkedKey))
        assertFalse(isFavoriteAutoDownloadEnabled(preferences, autoDownloadSongWhenPlaylistBookmarkedKey))

        values[autoDownloadSongWhenLikedKey] = false
        values[autoDownloadSongWhenPlaylistBookmarkedKey] = true
        assertFalse(isFavoriteAutoDownloadEnabled(preferences, autoDownloadSongWhenLikedKey))
        assertTrue(isFavoriteAutoDownloadEnabled(preferences, autoDownloadSongWhenPlaylistBookmarkedKey))
    }

    @Test
    fun `existing unfinished and complete downloads are skipped while failures can retry`() {
        listOf(
            Download.STATE_QUEUED,
            Download.STATE_STOPPED,
            Download.STATE_DOWNLOADING,
            Download.STATE_COMPLETED,
            Download.STATE_REMOVING,
            Download.STATE_RESTARTING,
        ).forEach { state -> assertFalse(canAutoDownloadInState(state), "Unexpected eligibility for state $state") }
        assertTrue(canAutoDownloadInState(null))
        assertTrue(canAutoDownloadInState(Download.STATE_FAILED))
    }
}
