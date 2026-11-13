package app.n_zik.android.core.profiles

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.ExoPlayerCacheLocation
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.exoPlayerCacheLocationKey
import app.n_zik.android.utils.getEnum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ProfileCacheLocationRecoveryTest {
    @Test
    fun `retry replaces only its own earlier publication after the source evolves`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val identity = checkNotNull(profileStorageIdentity(context, DEFAULT_PROFILE_ID))
        val names = resolveProfileDataDirNames(identity.id, emptySet())
        val source = context.cacheDir.resolve(names.downloadsDir).apply { mkdirs() }
        val sourceSpan = source.resolve("track.exo").apply { writeText("first") }
        val conflicting = context.filesDir.resolve(names.mediaCacheDir).apply { mkdirs() }
        conflicting.resolve("other.exo").writeText("unrelated")
        val destination = context.filesDir.resolve(names.downloadsDir)

        assertTrue(ProfileCacheLocationMigration.request(context, identity, ExoPlayerCacheLocation.Private))
        ProfileCacheLocationMigration.resume(context, identity.id)
        val prefs = profileDataPrefs(context, identity.id)
        assertEquals(ExoPlayerCacheLocation.System, prefs.getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System))
        assertEquals("first", destination.resolve(sourceSpan.name).readText())
        assertEquals("unrelated", conflicting.resolve("other.exo").readText())

        sourceSpan.writeText("updated")
        assertTrue(conflicting.deleteRecursively())
        ProfileCacheLocationMigration.resume(context, identity.id)

        assertEquals(ExoPlayerCacheLocation.Private, prefs.getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System))
        assertEquals("updated", destination.resolve(sourceSpan.name).readText())
        assertFalse(source.exists())
    }
}
