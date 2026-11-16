package app.n_zik.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MusicWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            try {
                NZikWidgetManager.updateIdleWidgets(context)
                if (PlayerServiceModern.isRunning) {
                    val intent = Intent(context, PlayerServiceModern::class.java).apply {
                        action = ACTION_UPDATE_WIDGET
                    }
                    try {
                        context.startService(intent)
                    } catch (error: Exception) {
                        Timber.tag("MusicWidgetReceiver").w("Unable to start widget playback service (%s)", error.javaClass.simpleName)
                    }
                }
            } catch (error: Exception) {
                Timber.tag("MusicWidgetReceiver").w("Unable to handle widget playback action (%s)", error.javaClass.simpleName)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            try {
                NZikWidgetManager.updateIdleWidgets(context)
                if (PlayerServiceModern.isRunning) {
                    val intent = Intent(context, PlayerServiceModern::class.java).apply {
                        action = ACTION_UPDATE_WIDGET
                    }
                    try {
                        context.startService(intent)
                    } catch (error: Exception) {
                        Timber.tag("MusicWidgetReceiver").w("Unable to start widget playback service (%s)", error.javaClass.simpleName)
                    }
                }
            } catch (error: Exception) {
                Timber.tag("MusicWidgetReceiver").w("Unable to handle widget playback action (%s)", error.javaClass.simpleName)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (PlayerServiceModern.isRunning) {
                    PlayerServiceModern.Action.playPause.pendingIntent.send()
                } else {
                    PlayerServiceModern.Action.play.pendingIntent.send()
                }
            }
            ACTION_LIKE -> {
                if (PlayerServiceModern.isRunning) {
                    PlayerServiceModern.Action.like.pendingIntent.send()
                }
            }
            ACTION_NEXT -> {
                if (PlayerServiceModern.isRunning) {
                    PlayerServiceModern.Action.next.pendingIntent.send()
                }
            }
            ACTION_PREVIOUS -> {
                if (PlayerServiceModern.isRunning) {
                    PlayerServiceModern.Action.previous.pendingIntent.send()
                }
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "app.n_zik.android.widget.PLAY_PAUSE"
        const val ACTION_LIKE = "app.n_zik.android.widget.LIKE"
        const val ACTION_NEXT = "app.n_zik.android.widget.NEXT"
        const val ACTION_PREVIOUS = "app.n_zik.android.widget.PREVIOUS"
        const val ACTION_UPDATE_WIDGET = "app.n_zik.android.widget.UPDATE_WIDGET"
    }
}
