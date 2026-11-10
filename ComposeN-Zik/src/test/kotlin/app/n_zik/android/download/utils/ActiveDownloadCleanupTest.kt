package app.n_zik.android.download.utils

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.download.services.ProfileDownloadCommands
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActiveDownloadCleanupTest {
    @Test
    fun `replacement ids and files changed by a stopping transfer are excluded`() {
        val replaced = entry("replaced")
        val growing = entry("growing")
        val untouched = entry("untouched")
        val current = listOf(replaced.copy(startTime = 2L), growing.copy(spans = listOf(
            ActiveDownloadCleanup.Span(File("new-span"), "new"),
        )), untouched)

        assertEquals(setOf(untouched), ActiveDownloadCleanup.unchangedEntries(
            listOf(replaced, growing, untouched), current, null,
        ))
    }

    @Test
    fun `a new alias and the now playing entry preserve their physical cache keys`() {
        val aliased = entry("aliased", key = "shared")
        val playing = entry("playing", key = "current")
        val untouched = entry("untouched")
        val confirmed = listOf(aliased, playing, untouched)

        assertEquals(setOf(untouched), ActiveDownloadCleanup.unchangedEntries(
            confirmed, confirmed + entry("new", key = "shared"), "current",
        ))
    }

    @Test
    fun `playing a request ID protects other IDs sharing its physical key before a reader opens`() {
        val playing = entry("playing", key = "shared")
        val alias = entry("alias", key = "shared")
        val untouched = entry("untouched")
        val entries = listOf(playing, alias, untouched)
        val owner = ProfileStorageIdentity("default", "legacy")
        val target = ActiveDownloadCleanup.Target(
            ProfileDownloadCommands(owner, owner), File("downloads"), null, entries, "playing",
        )

        assertEquals(listOf(untouched), target.eligibleEntries)
        assertEquals(setOf(untouched), ActiveDownloadCleanup.unchangedEntries(entries, entries, "playing"))
    }

    private fun entry(id: String, key: String = id) = ActiveDownloadCleanup.Entry(
        DownloadRequest.Builder(id, Uri.parse("https://example.invalid/$id")).setCustomCacheKey(key).build(),
        1L,
        emptyList(),
    )
}
