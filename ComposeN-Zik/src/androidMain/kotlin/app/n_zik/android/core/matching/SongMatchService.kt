package app.n_zik.android.core.matching

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.utils.coroutines.NzikDispatchers

internal const val SONG_MATCH_NOTIFICATION_CHANNEL_ID = "song_match_channel"
private const val NOTIFICATION_ID = 4210
private const val TAG = "SongMatchService"

/** Runs one song matching request in the foreground until its library writes are reconciled. */
internal class SongMatchService : Service() {
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private var activeId: String? = null
    private var notificationJob: Job? = null
    private var retryJob: Job? = null
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
        when (intent?.action) {
            ACTION_START -> {
                val run = SongMatch.claim(requestId)
                if (run != null) {
                    startMatching(run)
                    return START_NOT_STICKY
                }
            }
            ACTION_CANCEL -> requestId?.let(SongMatch::cancel)
            ACTION_RETRY -> if (requestId != null) retry(intent, requestId)
        }
        stopIfIdle()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val requestId = activeId
        if (requestId == null) {
            stopSelf()
            return
        }
        Timber.tag(TAG).w("Foreground matching exceeded the data sync time budget")
        SongMatch.interruption(requestId, failed = true)
        stopMatching(requestId)
    }

    override fun onDestroy() {
        activeId?.let(SongMatch::cancel)
        activeId = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startMatching(run: SongMatchRun) {
        val cancelAction = try {
            val action = cancelAction(run.id)
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                progressNotification(SongMatch.state.value?.takeIf { it.requestId == run.id } ?: run.initialState, action),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
            action
        } catch (error: RuntimeException) {
            Timber.tag(TAG).w(error, "Could not promote song matching to foreground")
            stopSelf()
            SongMatch.failToPromote(run)
            return
        }

        activeId = run.id
        SongMatch.promoted(run)
        notificationJob = scope.launch {
            SongMatch.state.filterNotNull().collect { state ->
                if (state.requestId == activeId) {
                    postNotification { progressNotification(state, cancelAction) }
                }
            }
        }
        SongMatch.execute(applicationContext, run) { result ->
            val keepNotification = result != null && NotificationManagerCompat.from(this).areNotificationsEnabled()
            stopMatching(run.id, keepNotification)
            if (keepNotification && result != null) {
                if (!postNotification { resultNotification(result, run.owner) }) {
                    getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
                }
            }
        }
    }

    private fun retry(intent: Intent?, requestId: String) {
        if (activeId != null || retryJob != null) return
        val owner = intent?.let(::retryOwner) ?: return
        val result = SongMatch.retryResult(this, requestId, owner) ?: return
        retryJob = scope.launch {
            try {
                // A notification tap permits promotion after retry validation. Stale taps
                // use an ordinary service intent, so they can return without going foreground.
                val songs = loadSongMatchFailures(result) {
                    withContext(NzikDispatchers.UI) {
                        SongMatch.retryResult(this@SongMatchService, requestId, owner) != null
                    }
                }
                val run = SongMatch.reserveRetry(this@SongMatchService, result, owner, songs) ?: return@launch
                startMatching(run)
            } finally {
                retryJob = null
                stopIfIdle()
            }
        }
    }

    private fun stopIfIdle() {
        if (activeId == null && retryJob == null && !SongMatch.hasPendingStart()) stopSelfResult(latestStartId)
    }

    private fun stopMatching(requestId: String, keepNotification: Boolean = false) {
        if (activeId != requestId) return
        activeId = null
        notificationJob?.cancel()
        notificationJob = null
        ServiceCompat.stopForeground(
            this,
            if (keepNotification) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE,
        )
        stopSelf()
    }

    private fun postNotification(create: () -> Notification): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        val manager = getSystemService(NotificationManager::class.java) ?: return false
        return try {
            manager.notify(NOTIFICATION_ID, create())
            true
        } catch (error: RuntimeException) {
            Timber.tag(TAG).w(error, "Could not post song matching notification")
            false
        }
    }

    private fun progressNotification(state: SongMatchState, cancelAction: PendingIntent): Notification {
        val strings = ContextCompat.getContextForLanguage(this).resources
        return matchingNotificationBuilder(strings.getString(R.string.song_matching_progress_title))
            .setContentText(strings.getString(R.string.song_matching_notification_progress, state.processed, state.total))
            .setProgress(state.total, state.processed, false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .apply {
                if (state.status == SongMatchStatus.Running) {
                    addAction(R.drawable.close, strings.getString(R.string.common_action_cancel), cancelAction)
                }
            }
            .build()
    }

    internal fun resultNotification(state: SongMatchState, owner: ProfileStorageIdentity): Notification {
        val strings = ContextCompat.getContextForLanguage(this).resources
        val summaryId = when (state.status) {
            SongMatchStatus.Finished -> if (state.failed == 0 && state.unprocessed == 0) {
                R.string.song_matching_notification_completed
            } else {
                R.string.song_matching_notification_partial
            }
            SongMatchStatus.Cancelled -> R.string.song_matching_notification_cancelled
            SongMatchStatus.Failed -> R.string.song_matching_notification_failed
            SongMatchStatus.Running, SongMatchStatus.Cancelling -> error("Expected terminal matching state")
        }
        val summary = if (summaryId == R.string.song_matching_notification_completed) {
            strings.getString(summaryId, state.matched, state.merged)
        } else {
            strings.getString(summaryId, state.matched, state.failed, state.unprocessed, state.merged)
        }
        return matchingNotificationBuilder(strings.getString(R.string.song_matching_progress_title))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setOnlyAlertOnce(true)
            .apply {
                if (state.failedIds.isNotEmpty()) {
                    addAction(0, strings.getString(R.string.common_action_retry), retryAction(state.requestId, owner))
                }
            }
            .build()
    }

    private fun matchingNotificationBuilder(title: String): NotificationCompat.Builder {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openApp = PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, SONG_MATCH_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentIntent(openApp)
    }

    internal fun cancelAction(requestId: String): PendingIntent {
        val intent = Intent(this, SongMatchService::class.java)
            .setAction(ACTION_CANCEL)
            // Extras are ignored by PendingIntent identity, so each request needs its own URI.
            .setData(actionUri("cancel", requestId))
            .putExtra(EXTRA_REQUEST_ID, requestId)
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun retryAction(requestId: String, owner: ProfileStorageIdentity): PendingIntent {
        val intent = Intent(this, SongMatchService::class.java)
            .setAction(ACTION_RETRY)
            .setData(actionUri("retry", requestId))
            .putExtra(EXTRA_REQUEST_ID, requestId)
            .putExtra(EXTRA_PROFILE_ID, owner.id)
            .putExtra(EXTRA_PROFILE_GENERATION, owner.generation)
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionUri(action: String, requestId: String): Uri =
        Uri.Builder().scheme("nzik-song-match").authority(action).appendPath(requestId).build()

    internal fun retryOwner(intent: Intent): ProfileStorageIdentity? {
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return null
        val generation = intent.getStringExtra(EXTRA_PROFILE_GENERATION) ?: return null
        return ProfileStorageIdentity(profileId, generation)
    }

    companion object {
        internal const val ACTION_START = "app.n_zik.android.matching.action.START"
        private const val ACTION_CANCEL = "app.n_zik.android.matching.action.CANCEL"
        private const val ACTION_RETRY = "app.n_zik.android.matching.action.RETRY"
        internal const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val EXTRA_PROFILE_GENERATION = "profile_generation"
    }
}
