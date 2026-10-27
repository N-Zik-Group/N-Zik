package app.n_zik.android.components.ui.screens.bridge

import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class PairingCaptureActivity : CaptureActivity() {
    internal val cameraStateListener = object : CameraPreview.StateListener {
        override fun previewStarted() {
            PermissionUsageJournal.record(
                this@PairingCaptureActivity,
                PermissionFeature.PC_PAIRING,
                JournalPermission.CAMERA,
                PermissionEventKind.USED,
            )
        }

        override fun previewSized() = Unit
        override fun previewStopped() = Unit
        override fun cameraError(error: Exception) = Unit
        override fun cameraClosed() = Unit
    }

    override fun initializeContent(): DecoratedBarcodeView = super.initializeContent().also {
        it.barcodeView.addStateListener(cameraStateListener)
    }
}
