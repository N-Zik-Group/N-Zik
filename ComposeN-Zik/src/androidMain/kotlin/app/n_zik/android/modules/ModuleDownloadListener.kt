package app.n_zik.android.modules

/**
 * Observes a module download for side effects (the persistent download notification,
 * UI state, …). Injected into [ModuleManager] so the core state machine stays
 * unit-testable on the JVM; the default is a no-op.
 */
interface ModuleDownloadListener {
    /** A download started; [isUpdate] is true when an older module version stays on disk. */
    fun onDownloadStarted(def: ModuleDefinition, isUpdate: Boolean) {}

    /** Transfer progress, 0f..1f. */
    fun onDownloadProgress(def: ModuleDefinition, progress: Float) {}

    /** The module was downloaded, verified and installed. */
    fun onDownloadCompleted(def: ModuleDefinition) {}

    /** The download failed; [cause] is logged, not shown. */
    fun onDownloadFailed(def: ModuleDefinition, cause: Throwable) {}

    /** The download was cancelled by the user. */
    fun onDownloadCancelled(def: ModuleDefinition) {}

    /** The module was uninstalled from the device. */
    fun onUninstalled(def: ModuleDefinition) {}
}

/** Default [ModuleDownloadListener] - no side effects. */
object NoopModuleDownloadListener : ModuleDownloadListener
