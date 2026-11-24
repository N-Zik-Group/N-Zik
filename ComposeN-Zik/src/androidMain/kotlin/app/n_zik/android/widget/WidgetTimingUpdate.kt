package app.n_zik.android.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import app.n_zik.android.R

internal suspend fun AppWidgetManager.publishPlaybackWidget(
    widgetId: Int,
    options: Bundle,
    timingUpdates: MutableList<WidgetTimingUpdate>?,
    createViews: suspend (Bundle) -> RemoteViews,
) {
    val templates = mutableListOf<RemoteViews>()
    val views = WidgetAppearance.responsive(options) { size ->
        val template = createViews(size)
        if (timingUpdates == null) {
            template
        } else {
            templates.add(template)
            // Combining variants transfers the child views into the host's bitmap cache.
            template.copyForTiming()
        }
    }
    currentCoroutineContext().ensureActive()
    updateAppWidget(widgetId, views)
    if (templates.any { it.layoutId != R.layout.widget_compact_square }) {
        timingUpdates?.add(WidgetTimingUpdate(widgetId, Bundle(options), templates))
    }
}

internal class WidgetTimingUpdate(
    private val widgetId: Int,
    private val options: Bundle,
    private val templates: List<RemoteViews>,
) {
    suspend fun publish(manager: AppWidgetManager, packageName: String, playback: WidgetPlaybackSnapshot) {
        val updateChronometer = playback.chronometerBaseAt(SystemClock.elapsedRealtime()) == null
        if (!updateChronometer && templates.none { it.layoutId.hasProgressBar() }) return

        currentCoroutineContext().ensureActive()
        if (templates.size == 1) {
            val template = templates.single()
            val timing = RemoteViews(packageName, template.layoutId)
            timing.applyPlaybackTiming(playback, updateChronometer)
            manager.partiallyUpdateAppWidget(widgetId, timing)
        } else {
            // Android merges partial actions at the root, outside the selected size variant.
            val variants = templates.iterator()
            val views = WidgetAppearance.responsive(options) {
                variants.next().copyForTiming().apply {
                    applyPlaybackTiming(playback, updateChronometer)
                }
            }
            currentCoroutineContext().ensureActive()
            manager.updateAppWidget(widgetId, views)
        }
    }
}

@Suppress("DEPRECATION")
private fun RemoteViews.copyForTiming(): RemoteViews =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) RemoteViews(this) else clone()

private fun Int.hasProgressBar(): Boolean = this == R.layout.widget_music_player || this == R.layout.widget_playlist

private fun RemoteViews.applyPlaybackTiming(playback: WidgetPlaybackSnapshot, updateChronometer: Boolean) {
    when (layoutId) {
        R.layout.widget_music_player -> setWidgetPlaybackTiming(
            playback, R.id.widget_chronometer, R.id.widget_progress_fill, updateChronometer,
        )
        R.layout.widget_compact_wide -> setWidgetPlaybackTiming(
            playback, R.id.widget_wide_chronometer, updateChronometer = updateChronometer,
        )
        R.layout.widget_turntable -> setWidgetPlaybackTiming(
            playback, R.id.widget_turntable_chronometer, updateChronometer = updateChronometer,
        )
        R.layout.widget_playlist -> setWidgetPlaybackTiming(
            playback, R.id.widget_playlist_chronometer, R.id.widget_playlist_progress_fill, updateChronometer,
        )
    }
}
