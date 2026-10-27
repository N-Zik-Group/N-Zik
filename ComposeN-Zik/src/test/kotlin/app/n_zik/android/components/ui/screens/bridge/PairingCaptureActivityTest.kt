package app.n_zik.android.components.ui.screens.bridge

import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PairingCaptureActivityTest {
    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `camera usage begins at preview even without decoded QR`() {
        mockkObject(PermissionUsageJournal)
        every { PermissionUsageJournal.record(any(), any(), any(), any()) } returns true
        val activity = Robolectric.buildActivity(PairingCaptureActivity::class.java).get()
        val listener = activity.cameraStateListener

        listener.previewSized()
        listener.cameraError(IllegalStateException("Camera unavailable"))
        listener.previewStopped()
        listener.cameraClosed()
        verify(exactly = 0) { PermissionUsageJournal.record(any(), any(), any(), any()) }

        listener.previewStarted()
        listener.previewStopped()
        listener.cameraClosed()
        verify(exactly = 1) {
            PermissionUsageJournal.record(
                activity,
                PermissionFeature.PC_PAIRING,
                JournalPermission.CAMERA,
                PermissionEventKind.USED,
            )
        }
    }
}
