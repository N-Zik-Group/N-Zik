package app.n_zik.android.components.settings.appearance.preview

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.settings.accounts.GitHubAccount
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.core.network.OfflineMode
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@Composable
internal fun CreatorSubmissionsCard(refreshVersion: Int) {
    val context = LocalContext.current
    val id = signedInCreatorId() ?: return
    val account by GitHubAccount.login.collectAsStateWithLifecycle()
    val login = account ?: return
    val offline by OfflineMode.rememberEnabled()
    var showHistory by rememberSaveable(id) { mutableStateOf<Boolean?>(null) }
    var retry by remember(id) { mutableIntStateOf(0) }
    var loading by remember(id, login, refreshVersion, offline, retry) { mutableStateOf(!offline) }
    val submissions by produceState<List<CreatorSubmission>?>(null, id, login, refreshVersion, offline, retry) {
        if (!offline) {
            try {
                value = creatorSubmissions(id, login, GitHubAccount.tokenFor(context, id))
                notifyCreatorDecisions(context, id, value.orEmpty())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not load creator submissions")
                value = null
            } finally {
                loading = false
            }
        }
    }
    val latestOpen = submissions?.filter { it.state == "open" }?.maxWithOrNull(compareBy(CreatorSubmission::stateDate, CreatorSubmission::number))
    val latestDecision = submissions?.filter { it.state != "open" }?.maxWithOrNull(compareBy(CreatorSubmission::stateDate, CreatorSubmission::number))
    @Composable
    fun latestStatus(submission: CreatorSubmission?, fallback: Int): String =
        submission?.let { "${it.title} · ${stringResource(submissionStatus(it))}" } ?: stringResource(fallback)
    SettingsSectionCard(title = stringResource(R.string.theme_store_creator_submissions), icon = R.drawable.github_icon,
        horizontalPadding = 0.dp, content = {
            OtherSettingsEntry(stringResource(R.string.theme_store_submissions_in_progress),
                latestStatus(latestOpen, R.string.theme_store_submissions_in_progress_description), R.drawable.github_icon,
                onClick = { showHistory = false })
            OtherSettingsEntry(stringResource(R.string.theme_store_submissions_history),
                latestStatus(latestDecision, R.string.theme_store_submissions_history_description), R.drawable.github_icon,
                onClick = { showHistory = true })
        })
    showHistory?.let { history ->
        val dismiss = { showHistory = null }
        DefaultDialog(
            title = stringResource(if (history) R.string.theme_store_submissions_history else R.string.theme_store_submissions_in_progress),
            onDismiss = dismiss,
            footer = {
                ButtonRow(buildList {
                    add(DialogButton(stringResource(R.string.common_action_close), dismiss, danger = true))
                    if (!offline && !loading && submissions == null) {
                        add(DialogButton(stringResource(R.string.common_action_retry), { retry++ }, primary = true))
                    }
                })
            },
        ) {
            val entries = submissions?.filter { (it.state != "open") == history }
            if (loading && entries == null) {
                app.n_zik.android.components.ui.FullPageLoader()
            } else if (entries.isNullOrEmpty()) {
                SettingsDescription(stringResource(if (entries == null) R.string.theme_store_creator_submissions_unavailable else R.string.theme_store_creator_submissions_empty))
            } else {
                entries.forEach { submission ->
                    val status = stringResource(submissionStatus(submission))
                    OtherSettingsEntry(submission.title, listOfNotNull(status, submission.reason).joinToString("\n"), R.drawable.github_icon,
                        onClick = { runCatching { context.startActivity(openSubmission(submission.number)) } })
                }
            }
        }
    }
}

private fun submissionStatus(submission: CreatorSubmission) = when (submission.state) {
    "open" -> R.string.theme_store_creator_submission_pending
    "accepted" -> R.string.theme_store_creator_submission_accepted
    "refused" -> R.string.theme_store_creator_submission_refused
    "banned" -> R.string.theme_store_creator_submission_banned
    else -> R.string.theme_store_creator_submission_closed
}

private fun openSubmission(number: Int) = Intent(Intent.ACTION_VIEW, Uri.parse(creatorPullUrl(number)))

internal fun notifyCreatorSubmissionSent(context: Context, id: Long, number: Int, titleRes: Int = R.string.theme_store_creator_pr_submitted) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = "theme_store_creator_submissions"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(NotificationChannel(
            channel, context.getString(R.string.theme_store_creator_submissions), NotificationManager.IMPORTANCE_DEFAULT,
        ))
    }
    val open = PendingIntent.getActivity(context, number, openSubmission(number), PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.notification2)
        .setContentTitle(context.getString(titleRes))
        .setContentText(context.getString(R.string.theme_store_creator_submission_number, number))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    try {
        manager.notify("creator-submission:$id", number, notification)
    } catch (_: SecurityException) {
        // Notification permission may change between the check and the post.
    }
}

private val creatorDecisionNotificationLock = Any()

internal fun hasPendingCreatorDecisions(context: Context, id: Long): Boolean =
    context.getSharedPreferences("theme_creator_submissions", Context.MODE_PRIVATE).all.any { (key, state) ->
        key.startsWith("$id:") && state == "open"
    }

internal fun notifyCreatorDecisions(context: Context, id: Long, submissions: List<CreatorSubmission>) {
    synchronized(creatorDecisionNotificationLock) {
        if (GitHubAccount.userId(context) == id) postCreatorDecisions(context, id, submissions)
    }
}

private fun postCreatorDecisions(context: Context, id: Long, submissions: List<CreatorSubmission>) {
    val preferences = context.getSharedPreferences("theme_creator_submissions", Context.MODE_PRIVATE)
    val notifications = context.getSystemService(NotificationManager::class.java) ?: return
    var available = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val channel = "theme_store_creator_submissions"
    if (available && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notifications.createNotificationChannel(NotificationChannel(channel,
            context.getString(R.string.theme_store_creator_submissions), NotificationManager.IMPORTANCE_DEFAULT))
        available = notifications.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    for (submission in submissions) {
        val key = "$id:${submission.number}"
        val old = preferences.getString(key, null)
        val timestampKey = "date:$key"
        val recordedDate = preferences.getString(timestampKey, null).orEmpty()
        if (submission.stateDate.isNotEmpty() && submission.stateDate < recordedDate) continue
        if (old in terminalSubmissionStates && submission.state == "open") continue
        val terminalDecision = submission.state in terminalSubmissionStates
        if (old != submission.state && ((old == "open" && submission.state != "open") || (old == "closed" && terminalDecision))) {
            if (!available) continue
            val open = PendingIntent.getActivity(context, submission.number, openSubmission(submission.number), PendingIntent.FLAG_IMMUTABLE)
            val description = listOfNotNull(submission.title, submission.reason).joinToString("\n")
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.notification2)
                .setContentTitle(context.getString(submissionStatus(submission)))
                .setContentText(description)
                .setStyle(NotificationCompat.BigTextStyle().bigText(description))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                notifications.notify("creator-submission:$id", submission.number, notification)
            } catch (_: SecurityException) {
                continue
            }
        }
        if (old != submission.state || submission.stateDate > recordedDate) {
            preferences.edit().putString(key, submission.state).apply {
                if (submission.stateDate.isNotEmpty()) putString(timestampKey, submission.stateDate)
            }.apply()
        }
    }
}

private val terminalSubmissionStates = setOf("accepted", "refused", "banned")
