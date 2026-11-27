package app.n_zik.android.download.services

import android.content.Context
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.core.profiles.invalidateProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.download.utils.encodeDownloadDisplayName
import app.n_zik.android.enums.DownloadQualityFormat
import org.junit.Assert.assertEquals
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
    fun `a replacement reservation is bound to one owner and track and cannot be replayed`() {
        val profile = ProfileStorageIdentity("profile", "generation")
        val owner = ProfileDownloadCommands(profile, profile)
        val otherOwner = owner.copy(consumer = profile.copy(generation = "other-generation"))
        val uri = Uri.parse("https://example.test/track")
        val original = DownloadRequest.Builder("track", uri).build()
        val replacement = DownloadRequest.Builder("track", uri)
            .setData(encodeDownloadDisplayName("Track", "00000000-0000-4000-8000-000000000001")).build()
        val wrongTrack = DownloadRequest.Builder("another-track", uri).setData(replacement.data).build()
        val prior = MyDownloadHelper.downloads.value
        val priorSession = MyDownloadHelper.sessionDownloads.value
        try {
            val downloaded = Download(original, Download.STATE_COMPLETED, 0L, 0L, 0L,
                Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
            MyDownloadHelper.downloads.value = mapOf(original.id to downloaded)
            assertTrue(MyDownloadHelper.reserveQualityReplacement(owner, replacement))
            assertFalse(MyDownloadHelper.admitQualityReplacement(otherOwner, replacement))
            assertFalse(MyDownloadHelper.admitQualityReplacement(owner, wrongTrack))
            assertTrue(MyDownloadHelper.admitQualityReplacement(owner, replacement))
            assertFalse(MyDownloadHelper.admitQualityReplacement(owner, replacement))

            val finished = Download(replacement, Download.STATE_COMPLETED, 0L, 0L, 0L,
                Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
            MyDownloadHelper.syncDownloads(finished, owner)
            assertFalse(MyDownloadHelper.admitQualityReplacement(owner, replacement))
            assertFalse(MyDownloadHelper.releaseRejectedQualityReplacement(replacement))
        } finally {
            MyDownloadHelper.releaseRejectedQualityReplacement(replacement)
            MyDownloadHelper.releaseDownloadAdd(replacement)
            MyDownloadHelper.downloads.value = prior
            MyDownloadHelper.sessionDownloads.value = priorSession
        }
    }

    @Test
    fun `a rejected replacement sends no separate removal command`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setActiveProfile(DEFAULT_PROFILE_ID, context)
        val attempts = mutableListOf<Intent>()
        val rejectingContext = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName {
                attempts.add(service)
                throw SecurityException("Service launch rejected")
            }

            override fun startService(service: Intent): ComponentName {
                attempts.add(service)
                throw SecurityException("Service launch rejected")
            }
        }
        val owner = checkNotNull(ProfileDownloadCommands.capture(context))
        val request = DownloadRequest.Builder("track", Uri.parse("https://example.test/track")).build()

        assertTrue(owner.sendReplacement(rejectingContext, request, DownloadQualityFormat.High).isFailure)
        val attempt = attempts.single()
        assertEquals(DownloadService.ACTION_ADD_DOWNLOAD, attempt.action)
        assertEquals(DownloadQualityFormat.High.name, attempt.getStringExtra(DOWNLOAD_REPLACEMENT_QUALITY_EXTRA))
        assertTrue(owner.matches(attempt))
    }

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
