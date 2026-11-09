package app.n_zik.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import app.n_zik.android.core.backup.isLibraryReady
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaylistWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        refresh(context) {
            PlaylistWidgetManager.refreshIdleWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        refresh(context) {
            PlaylistWidgetManager.updateIdleWidget(context, appWidgetId, newOptions)
        }
    }

    private fun refresh(context: Context, update: suspend () -> Unit) {
        if (!context.isLibraryReady()) {
            // Persist one refresh without extending the widget broadcast's lifetime.
            PlaylistWidgetRefreshWorker.schedule(context)
            return
        }

        val pendingResult = goAsync()
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            try {
                update()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("PlaylistWidgetReceiver").w(error, "Could not refresh playlist widgets")
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val TARGET_TYPE_LOCAL = "local"
        const val TARGET_TYPE_ONLINE = "online"
        const val TARGET_TYPE_LIKED = "liked"
        const val TARGET_TYPE_DOWNLOADED = "downloaded"
        const val TARGET_TYPE_TOP = "top"
    }
}
