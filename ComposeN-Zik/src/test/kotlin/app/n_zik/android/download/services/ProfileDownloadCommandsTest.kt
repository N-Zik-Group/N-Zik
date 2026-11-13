package app.n_zik.android.download.services

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadService
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.core.profiles.invalidateProfileStorageIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileDownloadCommandsTest {
    @Test
    fun `a stamped legacy item command keeps its original generation on redispatch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setActiveProfile(DEFAULT_PROFILE_ID, context)
        val original = DownloadService.buildRemoveDownloadIntent(
            context, MyDownloadService::class.java, "track", false,
        )
        val stamped = context.withProfileDownloadCommandOwner(original)
        assertNull(original.data)
        assertTrue(checkNotNull(ProfileDownloadCommands.capture(context)).matches(stamped))

        invalidateProfileStorageIdentity(context, DEFAULT_PROFILE_ID)

        assertSame(stamped, context.withProfileDownloadCommandOwner(stamped))
        assertFalse(ProfileDownloadCommands.capture(context)?.matches(stamped) == true)
    }
}
