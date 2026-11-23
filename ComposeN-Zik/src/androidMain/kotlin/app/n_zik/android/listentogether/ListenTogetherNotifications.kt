// Metrolist Project (C) 2026
// Licensed under GPL-3.0 | See git history for contributors
package app.n_zik.android.listentogether

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import app.n_zik.android.R
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.utils.getActiveProfile

/** Owns actionable notifications and their IDs for one room client. */
internal class ListenTogetherNotifications(private val context: Context) {
    private val joinRequests = mutableMapOf<String, Int>()
    private val suggestions = mutableMapOf<String, Int>()

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showJoinRequest(payload: JoinRequestPayload) {
        post(
            id = payload.userId,
            notifications = joinRequests,
            idExtra = ListenTogetherClient.EXTRA_USER_ID,
            approve = ListenTogetherClient.ACTION_APPROVE_JOIN,
            reject = ListenTogetherClient.ACTION_REJECT_JOIN,
        ) { resources ->
            resources.getString(R.string.listen_together_join_request_notification, payload.username)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showSuggestion(payload: SuggestionReceivedPayload) {
        post(
            id = payload.suggestionId,
            notifications = suggestions,
            idExtra = ListenTogetherClient.EXTRA_SUGGESTION_ID,
            approve = ListenTogetherClient.ACTION_APPROVE_SUGGESTION,
            reject = ListenTogetherClient.ACTION_REJECT_SUGGESTION,
        ) { resources ->
            val title = payload.trackInfo.title.ifBlank { resources.getString(R.string.metadata_unknown_title) }
            resources.getString(R.string.listen_together_suggestion_received, payload.fromUsername, title)
        }
    }

    fun cancelJoinRequest(userId: String) = cancel(joinRequests, userId)

    fun cancelSuggestion(suggestionId: String) = cancel(suggestions, suggestionId)

    private fun cancel(notifications: MutableMap<String, Int>, id: String) {
        val notificationId = notifications.remove(id) ?: return
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun post(
        id: String,
        notifications: MutableMap<String, Int>,
        idExtra: String,
        approve: String,
        reject: String,
        content: (Resources) -> String,
    ) {
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notifications[id] = notificationId

        val approveIntent = actionIntent(approve, idExtra, id, notificationId, id.hashCode())
        val rejectIntent = actionIntent(reject, idExtra, id, notificationId, id.hashCode().inv())
        val resources = ContextCompat.getContextForLanguage(context).resources
        val channel = channelId(ListenTogetherClient.NOTIFICATION_CHANNEL_ID, getActiveProfile(context))
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.people)
            .setContentTitle(resources.getString(R.string.listen_together_title))
            .setContentText(content(resources))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, resources.getString(R.string.listen_together_action_approve), approveIntent)
            .addAction(0, resources.getString(R.string.listen_together_action_reject), rejectIntent)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            PermissionUsageJournal.record(
                context, PermissionFeature.LISTEN_TOGETHER, JournalPermission.NOTIFICATIONS, PermissionEventKind.USED,
            )
        }
    }

    private fun actionIntent(actionName: String, idExtra: String, id: String, notificationId: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, ListenTogetherActionReceiver::class.java).apply {
            action = actionName
            putExtra(idExtra, id)
            putExtra(ListenTogetherClient.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
