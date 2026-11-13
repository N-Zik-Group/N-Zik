package app.n_zik.android.playback.services

import android.app.Application
import android.content.ContextWrapper
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.profiles.mediaCacheDir
import app.n_zik.android.core.profiles.profileStorageIdentity
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ActiveStreamingCleanupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `cleanup excludes open and playing keys and rechecks keys opened after confirmation`() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
        }
        val owner = checkNotNull(profileStorageIdentity(context, DEFAULT_PROFILE_ID))
        val root = checkNotNull(mediaCacheDir(context, owner.id)).canonicalFile
        val database = StandaloneDatabaseProvider(context)
        val streaming = SimpleCache(root, NoOpCacheEvictor(), database)
        val downloads = SimpleCache(temporary.newFolder("downloads"), NoOpCacheEvictor(), database)
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val service = mockk<PlayerServiceModern>()
        every { service.cache } returns streaming
        every { service.downloadCache } returns downloads
        try {
            for (key in listOf("open", "playing", "old")) addSpan(streaming, key)
            val bound = ActiveStreamingCleanup.BoundCache(owner, root, streaming)
            StreamingCacheReaders.acquire(streaming, "open").use {
                val target = checkNotNull(ActiveStreamingCleanup.capture(context, owner, bound, "playing"))
                assertTrue(target.spans.keys == setOf("old"))
                val spec = DataSpec.Builder().setUri("https://example.test/watch?v=old").setKey("old").setLength(5).build()
                for (factory in listOf(service.createDataSourceFactory(), service.createBackgroundMusicDataSourceFactory())) {
                    val source = factory.createDataSource()
                    try {
                        assertTrue(source.open(spec) == 5L)
                        assertTrue(StreamingCacheReaders.isReading(downloads, "old"))
                        assertFalse(ActiveStreamingCleanup.execute(context, target, bound, "playing"))
                        assertTrue(streaming.isCached("old", 0, 5))
                    } finally {
                        source.close()
                    }
                    assertFalse(StreamingCacheReaders.isReading(downloads, "old"))
                }
                assertTrue(ActiveStreamingCleanup.execute(context, target, bound, "playing"))
                assertFalse(streaming.isCached("old", 0, 5))
                assertTrue(streaming.isCached("playing", 0, 5))
                assertTrue(streaming.isCached("open", 0, 5))
            }
        } finally {
            downloads.release()
            streaming.release()
            database.close()
        }
    }

    private fun addSpan(cache: SimpleCache, key: String) {
        val hole = cache.startReadWrite(key, 0, 5)
        try {
            val file = cache.startFile(key, 0, 5)
            file.writeText("audio")
            cache.commitFile(file, 5)
        } finally {
            cache.releaseHoleSpan(hole)
        }
    }
}
