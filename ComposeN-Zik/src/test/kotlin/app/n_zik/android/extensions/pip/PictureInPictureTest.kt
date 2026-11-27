package app.n_zik.android.extensions.pip

import android.app.Activity
import android.app.Application
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PictureInPictureTest {
    @Test
    fun `modern PiP entry reports the result returned by Android`() {
        val packageManager = mockk<PackageManager>()
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) } returns true
        val activity = mockk<Activity>()
        every { activity.packageManager } returns packageManager
        every { activity.enterPictureInPictureMode(any<PictureInPictureParams>()) } returnsMany listOf(false, true)

        assertFalse(activity.maybeEnterPip())
        assertTrue(activity.maybeEnterPip())
    }
}
