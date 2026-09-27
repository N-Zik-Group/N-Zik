package app.n_zik.android.components.maintenance

import android.app.Application
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.components.ui.screens.rewind.RewindYearlyReminderWorker
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.core.rewind.RewindMonthlyPlaylistWorker
import app.n_zik.android.core.rewind.RewindPostImportRegenerationWorker
import app.n_zik.android.core.rewind.RewindYearlyPlaylistWorker
import app.n_zik.android.extensions.musicbrainz.workers.MbBackfillWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [MAINTENANCE_JOB_NAMES] (the sheet's scheduled-jobs row) to the ACTUAL
 * `WORK_NAME` constant of each of the seven workers, read by reflection (precedent:
 * [app.n_zik.android.core.rewind.RewindPlaylistWorkersTest]). If a worker renames its
 * unique-work name — or a new worker is added to the boot/refresh set — this test
 * fails until [MAINTENANCE_JOB_NAMES] is updated, so the sheet can never silently stop
 * reporting a job.
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine, with
 * the plain [Application] so the app's heavy init is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MaintenanceJobNamesTest {

    /** The seven workers the sheet reports, each next to the class declaring its WORK_NAME. */
    private val expectedJobNames = linkedMapOf(
        "AutoBackupWorker" to BackupManager::class.java,
        "MbBackfillWorker" to MbBackfillWorker::class.java,
        "RewindMonthlyPlaylistWorker" to RewindMonthlyPlaylistWorker::class.java,
        "RewindYearlyPlaylistWorker" to RewindYearlyPlaylistWorker::class.java,
        "RewindPostImportRegenerationWorker" to RewindPostImportRegenerationWorker::class.java,
        "RewindReminderWorker" to RewindReminderWorker::class.java,
        "RewindYearlyReminderWorker" to RewindYearlyReminderWorker::class.java,
    )

    /**
     * Reads the `WORK_NAME` constant of [clazz] by reflection. A `private const val` in a
     * companion object compiles to a `private static final` field on the OUTER class
     * (an object's const val lives on the object class itself); the companion is only
     * probed as a fallback.
     */
    private fun workNameOf(clazz: Class<*>): String {
        val field = runCatching { clazz.getDeclaredField("WORK_NAME") }.getOrElse {
            val companion = clazz.declaredClasses.firstOrNull { it.simpleName == "Companion" }
                ?: error("No WORK_NAME constant found on ${clazz.name}")
            companion.getDeclaredField("WORK_NAME")
        }
        field.isAccessible = true
        return field.get(null) as String
    }

    @Test
    fun everyPinnedNameMatchesTheWorkersOwnWorkNameConstant() {
        for ((name, clazz) in expectedJobNames) {
            assertEquals(
                "${clazz.simpleName} WORK_NAME drifted from MAINTENANCE_JOB_NAMES",
                name,
                workNameOf(clazz),
            )
        }
    }

    @Test
    fun maintenanceJobNamesPinExactlyTheSevenWorkersInOrder() {
        assertEquals(expectedJobNames.keys.toList(), MAINTENANCE_JOB_NAMES)
    }
}
