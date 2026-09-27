package app.n_zik.android.components.maintenance

import androidx.work.WorkInfo
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.extensions.discord.DiscordRpcError
import app.n_zik.android.updater.services.UpdateDownloadManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The settings card's one-line health status (spec "Maintenance — état de l'app en un
 * regard"): the broken-system count follows the sheet's RED-row rules exactly —
 * abnormal jobs (FAILED/CANCELLED), a last crash block, an INVALID/EXPIRED YouTube
 * session, a Discord RPC error, a Listen Together error state, a failed update
 * download, a rate-limited MusicBrainz circuit, a failed download. Neutral states
 * are NOT counted. Plain unit test — no Android context is needed (R is a constant
 * class, WorkInfo is plain Java).
 */
class MaintenanceHealthTest {

    @Test
    fun allOkSnapshotIsOperational() {
        val snapshot = MaintenanceSnapshot(
            jobs = listOf(JobSnapshot("W", state = WorkInfo.State.SUCCEEDED, runAtStartTime = 0L)),
            ytSession = MainApplication.CookieStatus.VALID,
        )

        val health = maintenanceHealth(snapshot, null, UpdateDownloadManager.DownloadState.Idle, 0)

        assertEquals(MaintenanceHealth.Operational, health)
    }

    @Test
    fun notConnectedServicesAreNotCounted() {
        val snapshot = MaintenanceSnapshot(
            // All neutral "not connected" states: none of them must count as broken
            ytSession = MainApplication.CookieStatus.NOT_LOGGED_IN,
            listenTogether = null,
            discordTokenConfigured = false,
            lastfmSessionAvailable = false,
        )

        val health = maintenanceHealth(snapshot, null, UpdateDownloadManager.DownloadState.Idle, 0)

        assertEquals(MaintenanceHealth.Operational, health)
    }

    @Test
    fun runningOrEnqueuedJobsAreNotBroken() {
        val snapshot = MaintenanceSnapshot(
            // In-flight states are not alarm states (same rule as the sheet's jobs row)
            jobs = listOf(
                JobSnapshot("A", state = WorkInfo.State.RUNNING, runAtStartTime = 0L),
                JobSnapshot("B", state = WorkInfo.State.ENQUEUED, runAtStartTime = 0L),
            ),
        )

        val health = maintenanceHealth(snapshot, null, UpdateDownloadManager.DownloadState.Idle, 0)

        assertEquals(MaintenanceHealth.Operational, health)
    }

    @Test
    fun nullSnapshotIsNoData() {
        val health = maintenanceHealth(null, null, UpdateDownloadManager.DownloadState.Idle, 0)

        assertEquals(MaintenanceHealth.NoData, health)
    }

    @Test
    fun musicBrainzRateLimitIsCounted() {
        val snapshot = MaintenanceSnapshot(mbRateLimitedSeconds = 120L)

        val health = maintenanceHealth(snapshot, null, UpdateDownloadManager.DownloadState.Idle, 0)

        assertEquals(MaintenanceHealth.NeedsAttention(1), health)
    }

    @Test
    fun failedDownloadsAreCounted() {
        val snapshot = MaintenanceSnapshot()

        val health = maintenanceHealth(snapshot, null, UpdateDownloadManager.DownloadState.Idle, 2)

        assertEquals(MaintenanceHealth.NeedsAttention(1), health)
    }

    @Test
    fun everyBrokenSystemIsCounted() {
        val snapshot = MaintenanceSnapshot(
            jobs = listOf(JobSnapshot("W", state = WorkInfo.State.FAILED, runAtStartTime = 0L)),
            ytSession = MainApplication.CookieStatus.INVALID,
            listenTogether = ListenTogetherSnapshot(
                stateResId = R.string.maintenance_lt_error,
                roleResId = null,
                roomCode = null,
            ),
            lastCrash = "2026-01-01 00:00:00" to "java.lang.Exception: boom",
            mbRateLimitedSeconds = 60L,
        )

        val health = maintenanceHealth(
            snapshot,
            DiscordRpcError.RECONNECT_FAILED,
            UpdateDownloadManager.DownloadState.Failed("network"),
            1,
        )

        assertEquals(MaintenanceHealth.NeedsAttention(8), health)
    }
}
