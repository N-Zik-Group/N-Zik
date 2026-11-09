package app.n_zik.android.core.backup

import android.content.Context
import app.n_zik.android.MainApplication
import app.n_zik.android.awaitLibraryReady

/** Lets synchronous Android entry points decline access while the library is unavailable. */
internal fun Context.isLibraryReady(): Boolean =
    (applicationContext as? MainApplication)?.let { it.libraryStartup.state.value == LibraryStartupState.Ready } ?: true

/** Waits without blocking the worker and lets WorkManager retry a failed recovery. */
internal suspend fun Context.awaitLibraryReadyForWork(): Boolean =
    try {
        val startup = (applicationContext as? MainApplication)?.libraryStartup
        if (startup?.state?.value is LibraryStartupState.Failed) startup.start()
        awaitLibraryReady()
        true
    } catch (_: LibraryStartupException) {
        false
    }
