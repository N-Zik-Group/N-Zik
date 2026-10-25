package app.n_zik.android.modules

/**
 * Lifecycle state of a downloadable extension module (spec-ffmpeg-extension-module):
 *
 * NOT_DOWNLOADED → ensureReady → DOWNLOADING(progress) → READY
 *                                        → failure → FAILED(message) → retry → DOWNLOADING
 * READY → uninstall → NOT_DOWNLOADED
 * READY → version mismatch → UPDATING → READY (new) / FAILED
 */
sealed class ModuleState {
    /** No module on disk; the first feature use triggers a download. */
    data object NOT_DOWNLOADED : ModuleState()

    /** First-time archive download in progress; [progress] is 0f..1f. */
    data class DOWNLOADING(val progress: Float) : ModuleState()

    /** Pinned version extracted, verified and ready to use. */
    data object READY : ModuleState()

    /** Last attempt failed; [message] is the user-facing reason. */
    data class FAILED(val message: String) : ModuleState()

    /** A new module version is being downloaded; the old module dir stays intact. */
    data object UPDATING : ModuleState()
}

/** Result of a launch-time module update check (detection only - no download). */
sealed class ModuleUpdateStatus {
    /** No module installed for this device. */
    data object NotInstalled : ModuleUpdateStatus()

    /** The pinned module version is already on disk. */
    data object UpToDate : ModuleUpdateStatus()

    /** An older module version is installed; [installedVersion] names it. */
    data class UpdateAvailable(val installedVersion: String) : ModuleUpdateStatus()
}
