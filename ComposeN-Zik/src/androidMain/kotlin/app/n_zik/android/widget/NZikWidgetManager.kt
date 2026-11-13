package app.n_zik.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.withContext

import app.n_zik.android.cleanPrefix
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Keeps the explicit badge while hiding stored metadata markers on every widget layout. */
internal fun widgetDisplayTitle(title: String): String {
    val explicit = title.hasExplicitPrefix() || title.startsWith("e:", true) || title.startsWith("\uD83C\uDD74")
    return (if (explicit) "\uD83C\uDD74 " else "") + cleanPrefix(title)
}

object NZikWidgetManager {

    suspend fun updateIdleWidgets(context: Context) {
        updateWidgets(
            context = context,
            title = ContextCompat.getString(context, R.string.widget_not_playing_status),
            artist = ContextCompat.getString(context, R.string.widget_action_tap_to_play),
            artworkBitmap = null,
            isPlaying = false,
            isLiked = false,
            duration = 0,
            currentPosition = 0
        )
    }

    suspend fun updateWidgets(
        context: Context,
        title: String,
        artist: String,
        artworkBitmap: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
        artworkUri: String? = null,
    ) {
        val (albumArt, snapshot) = withContext(NzikDispatchers.MEDIA) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Extract palette from ORIGINAL bitmap (before scaling) to match in-app colors
            val snapshot = loadWidgetArtworkColors(context, artworkUri, artworkBitmap)
            val palette = widgetArtworkPalette(context, snapshot)

            // Scale down the bitmap to prevent RemoteViews memory limit exception (TransactionTooLarge)
            val albumArt: Bitmap? = artworkBitmap?.let {
                if (it.width > 300 || it.height > 300) {
                    val size = minOf(it.width, it.height)
                    val scale = 300f / size
                    Bitmap.createScaledBitmap(it, (it.width * scale).toInt(), (it.height * scale).toInt(), true)
                } else {
                    it
                }
            }

            // Update main music player widgets
            val componentName = ComponentName(context, MusicWidgetReceiver::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isNotEmpty()) {
                widgetIds.forEach { widgetId ->
                    val options = appWidgetManager.getAppWidgetOptions(widgetId)
                    val views = WidgetAppearance.responsive(options) { sizedOptions -> createRemoteViewsForSize(
                        context,
                        sizedOptions,
                        title,
                        artist,
                        albumArt,
                        isPlaying,
                        isLiked,
                        duration,
                        currentPosition,
                        palette,
                        artworkUri
                    ) }
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }

            // Update turntable widgets
            val turntableComponentName = ComponentName(context, TurntableWidgetReceiver::class.java)
            val turntableWidgetIds = appWidgetManager.getAppWidgetIds(turntableComponentName)
            if (turntableWidgetIds.isNotEmpty()) {
                turntableWidgetIds.forEach { widgetId ->
                    val options = appWidgetManager.getAppWidgetOptions(widgetId)
                    val turntableViews = WidgetAppearance.responsive(options) { sizedOptions ->
                        createTurntableRemoteViewsForSize(context, sizedOptions, albumArt, isPlaying,
                            isLiked, duration, currentPosition, palette, artworkUri)
                    }
                    appWidgetManager.updateAppWidget(widgetId, turntableViews)
                }
            }

            albumArt to snapshot
        }

        PlaylistWidgetManager.updateWidgets(
            context = context,
            title = title,
            artist = if (artworkBitmap == null) ContextCompat.getString(context, R.string.widget_configuration_choose_item_hint) else artist,
            artworkBitmap = albumArt,
            isPlaying = isPlaying,
            isLiked = isLiked,
            duration = duration,
            currentPosition = currentPosition,
            artworkUri = artworkUri,
            artworkColors = snapshot,
            artworkColorsResolved = true,
        )
    }

    internal suspend fun createRemoteViewsForSize(
        context: Context,
        options: Bundle,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long,
        palette: ColorPalette,
        artworkUri: String?,
    ): RemoteViews {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val fullHeight = maxOf(160f, 112f + 44f * context.resources.configuration.fontScale)
        val isShort = minHeight < fullHeight

        val views = when {
            minWidth < 180 && isShort -> {
                createCompactSquareRemoteViews(context, isPlaying, palette)
            }
            minWidth >= 180 && isShort -> {
                createCompactWideRemoteViews(context, title, artist, isPlaying, isLiked, palette, duration, currentPosition)
            }
            else -> {
                createRemoteViews(context, title, artist, isPlaying, isLiked, duration, currentPosition, palette)
            }
        }

        // Responsive scaling (Android 12+)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val isNarrow = minWidth < 340
        val isCompactWide = minWidth >= 180 && isShort
        val isFullWidget = !isShort

        if (isCompactWide) {
            val density = context.resources.displayMetrics.density
            val verticalPadding = if (minHeight < 56) 0 else 4
            views.setViewPadding(android.R.id.background, (8 * density).toInt(),
                (verticalPadding * density).toInt(), (8 * density).toInt(), (verticalPadding * density).toInt())
            views.setViewVisibility(R.id.widget_wide_artist_name,
                if (minHeight >= 64 * context.resources.configuration.fontScale) View.VISIBLE else View.GONE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isFullWidget) {
                val artSize = when {
                    isNarrow -> 56f // Must be checked FIRST so narrow tall widgets don't break
                    maxHeight >= 280 -> 110f
                    maxHeight >= 200 -> 96f
                    else -> 80f // Reduced default from 88f
                }
                views.setViewLayoutWidth(R.id.widget_album_art_container, artSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_album_art, artSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_album_art, artSize, TypedValue.COMPLEX_UNIT_DIP)

                // Controls scaling for narrow vs wide
                val smallBtn = if (isNarrow) 30f else 38f
                val playBtn = if (isNarrow) 38f else 48f

                views.setViewLayoutWidth(R.id.widget_prev_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_prev_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_play_pause_container, playBtn, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_play_pause_container, playBtn, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_next_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_next_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
            } else if (isCompactWide) {
                // Reserve a separate time row before sizing artwork and controls.
                val timeHeight = if (duration > 0) 14f * context.resources.configuration.fontScale else 0f
                val verticalPadding = if (minHeight < 56) 0f else 8f
                val rowHeight = (minHeight - verticalPadding - timeHeight).coerceAtLeast(16f)
                val artSize = minOf(if (isNarrow) 24f else 48f, rowHeight)
                views.setViewLayoutWidth(R.id.widget_wide_album_art, artSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_wide_album_art, artSize, TypedValue.COMPLEX_UNIT_DIP)

                val btnSize = minOf(if (isNarrow) 24f else 32f, rowHeight)
                val playSize = minOf(if (isNarrow) 24f else 36f, rowHeight)

                views.setViewLayoutWidth(R.id.widget_wide_like_container, btnSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_wide_like_container, btnSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_wide_prev_container, btnSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_wide_prev_container, btnSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_wide_play_container, playSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_wide_play_container, playSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_wide_next_container, btnSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_wide_next_container, btnSize, TypedValue.COMPLEX_UNIT_DIP)
            }
        }

        WidgetAppearance.apply(context, views, options, duration > 0, mapOf(
            R.id.widget_album_art to WidgetArtwork(albumArt, source = artworkUri),
            R.id.widget_compact_album_art to WidgetArtwork(albumArt, source = artworkUri),
            R.id.widget_wide_album_art to WidgetArtwork(albumArt, source = artworkUri),
        ))
        return views
    }

    private fun createRemoteViews(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
        palette: ColorPalette
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)
        views.setPlaybackDescriptions(
            context,
            R.id.widget_album_art,
            R.id.widget_prev_icon,
            R.id.widget_play_pause,
            R.id.widget_next_icon,
            R.id.widget_like_button,
        )

        views.setTextViewText(R.id.widget_song_title, widgetDisplayTitle(title))
        views.setTextViewText(R.id.widget_artist_name, cleanPrefix(artist))

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_like_button, likeIcon)

        if (duration > 0) {
            val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt()
            views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
        } else {
            views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
        }

        if (duration > 0) {
            views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_total_duration, View.VISIBLE)
            views.setViewVisibility(R.id.widget_progress_track, View.VISIBLE)
            views.setViewVisibility(R.id.widget_progress_fill, View.VISIBLE)
            val baseTime = SystemClock.elapsedRealtime() - currentPosition
            views.setChronometer(R.id.widget_chronometer, baseTime, null, isPlaying)
            views.setTextViewText(R.id.widget_total_duration, " / ${formatDuration(duration)}")
        } else {
            views.setViewVisibility(R.id.widget_chronometer, View.GONE)
            views.setViewVisibility(R.id.widget_total_duration, View.GONE)
            views.setViewVisibility(R.id.widget_progress_track, View.GONE)
            views.setViewVisibility(R.id.widget_progress_fill, View.GONE)
        }

        views.setOnClickPendingIntent(R.id.widget_album_art, getOpenAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent(context))
        views.setOnClickPendingIntent(R.id.widget_like_button, getLikeIntent(context))
        views.setOnClickPendingIntent(R.id.widget_prev_container, getPreviousIntent(context))
        views.setOnClickPendingIntent(R.id.widget_next_container, getNextIntent(context))

        applyWidgetTheme(views, palette)

        return views
    }

    private fun createCompactSquareRemoteViews(
        context: Context,
        isPlaying: Boolean,
        palette: ColorPalette
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_square)
        views.setPlaybackDescriptions(
            context,
            R.id.widget_compact_album_art,
            R.id.widget_compact_prev_icon,
            R.id.widget_compact_play_pause,
            R.id.widget_compact_next_icon,
            R.id.widget_compact_like_button,
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        views.setImageViewResource(R.id.widget_compact_play_pause, playPauseIcon)

        views.setOnClickPendingIntent(R.id.widget_compact_album_art, getOpenAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compact_play_container, getPlayPauseIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compact_like_button, getLikeIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compact_prev_container, getPreviousIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compact_next_container, getNextIntent(context))

        applyWidgetTheme(views, palette)

        return views
    }

    private fun createCompactWideRemoteViews(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        isLiked: Boolean,
        palette: ColorPalette,
        duration: Long,
        currentPosition: Long,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)
        views.setPlaybackDescriptions(
            context,
            R.id.widget_wide_album_art,
            R.id.widget_wide_prev_icon,
            R.id.widget_wide_play_pause,
            R.id.widget_wide_next_icon,
            R.id.widget_wide_like_button,
        )

        views.setTextViewText(R.id.widget_wide_song_title, widgetDisplayTitle(title))
        views.setTextViewText(R.id.widget_wide_artist_name, cleanPrefix(artist))
        views.setViewVisibility(R.id.widget_wide_time, if (duration > 0) View.VISIBLE else View.GONE)
        views.setChronometer(R.id.widget_wide_chronometer,
            SystemClock.elapsedRealtime() - currentPosition.coerceAtLeast(0), "%s", isPlaying && duration > 0)
        views.setTextViewText(R.id.widget_wide_total_duration, " / ${formatDuration(duration.coerceAtLeast(0))}")

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        views.setImageViewResource(R.id.widget_wide_play_pause, playPauseIcon)

        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_wide_like_button, likeIcon)

        views.setOnClickPendingIntent(R.id.widget_wide_album_art, getOpenAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_wide_play_container, getPlayPauseIntent(context))
        views.setOnClickPendingIntent(R.id.widget_wide_like_button, getLikeIntent(context))
        views.setOnClickPendingIntent(R.id.widget_wide_prev_container, getPreviousIntent(context))
        views.setOnClickPendingIntent(R.id.widget_wide_next_container, getNextIntent(context))

        applyWidgetTheme(views, palette)

        return views
    }

    internal suspend fun createTurntableRemoteViewsForSize(
        context: Context,
        options: Bundle,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long,
        palette: ColorPalette,
        artworkUri: String?,
    ): RemoteViews {
        val views = createTurntableRemoteViews(context, isPlaying, isLiked, duration, currentPosition, palette)
        val density = context.resources.displayMetrics.density
        val width = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110) * density).roundToInt()
        val height = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110) * density).roundToInt()
        val horizontalExcess = (width - height).coerceAtLeast(0)
        val verticalExcess = (height - width).coerceAtLeast(0)
        val padding = (4 * density).roundToInt()
        // Keep the existing controls anchored to the record's square, even in rectangular launcher cells.
        views.setViewPadding(android.R.id.background,
            padding + horizontalExcess / 2, padding + verticalExcess / 2,
            padding + horizontalExcess - horizontalExcess / 2,
            padding + verticalExcess - verticalExcess / 2)
        WidgetAppearance.apply(context, views, options, duration > 0,
            mapOf(R.id.widget_turntable_album_art to WidgetArtwork(albumArt, source = artworkUri)))
        return views
    }

    private fun createTurntableRemoteViews(
        context: Context,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
        palette: ColorPalette
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)
        views.setPlaybackDescriptions(
            context,
            R.id.widget_turntable_album_art,
            R.id.widget_turntable_prev_button,
            R.id.widget_turntable_play_pause,
            R.id.widget_turntable_next_button,
            R.id.widget_turntable_like_button,
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_secondary else R.drawable.ic_widget_play_secondary
        views.setImageViewResource(R.id.widget_turntable_play_pause, playPauseIcon)

        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_turntable_like_button, likeIcon)

        if (duration > 0) {
            views.setViewVisibility(R.id.widget_turntable_chronometer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_turntable_total_duration, View.VISIBLE)
            val baseTime = SystemClock.elapsedRealtime() - currentPosition
            views.setChronometer(R.id.widget_turntable_chronometer, baseTime, null, isPlaying)
            views.setTextViewText(R.id.widget_turntable_total_duration, formatDuration(duration))
        } else {
            views.setViewVisibility(R.id.widget_turntable_chronometer, View.GONE)
            views.setViewVisibility(R.id.widget_turntable_total_duration, View.GONE)
        }

        views.setOnClickPendingIntent(R.id.widget_turntable_album_art, getOpenAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_turntable_play_container, getTurntablePlayPauseIntent(context))
        views.setOnClickPendingIntent(R.id.widget_turntable_prev_button, getTurntablePreviousIntent(context))
        views.setOnClickPendingIntent(R.id.widget_turntable_next_button, getTurntableNextIntent(context))
        views.setOnClickPendingIntent(R.id.widget_turntable_like_button, getLikeIntent(context))

        applyWidgetTheme(views, palette)

        return views
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun getOpenAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPlayPauseIntent(context: Context): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getLikeIntent(context: Context): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_LIKE
        }
        return PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPreviousIntent(context: Context): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PREVIOUS
        }
        return PendingIntent.getBroadcast(
            context,
            10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNextIntent(context: Context): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_NEXT
        }
        return PendingIntent.getBroadcast(
            context,
            11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePlayPauseIntent(context: Context): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntableNextIntent(context: Context): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_NEXT
        }
        return PendingIntent.getBroadcast(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePreviousIntent(context: Context): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PREVIOUS
        }
        return PendingIntent.getBroadcast(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal suspend fun extractPalette(context: Context, albumArt: Bitmap?): ColorPalette =
        widgetArtworkPalette(context, loadWidgetArtworkColors(context, null, albumArt))

    private fun applyWidgetTheme(views: RemoteViews, palette: ColorPalette) {
        val bgArgb = palette.background1.toArgb()
        val accentArgb = palette.accent.toArgb()

        // Keyed on the effective palette tone, not isSystemInDarkMode (spec-achromatic-ramp-luminance-cap)
        val iconTintArgb = widgetIconTintArgb(palette)
        val textArgb = widgetTextArgb(palette)

        // Text colors - full-size widget
        views.setTextColor(R.id.widget_song_title, textArgb)
        views.setTextColor(R.id.widget_artist_name, textArgb)
        views.setTextColor(R.id.widget_chronometer, textArgb)
        views.setTextColor(R.id.widget_total_duration, textArgb)
        // Compact wide widget
        views.setTextColor(R.id.widget_wide_song_title, textArgb)
        views.setTextColor(R.id.widget_wide_artist_name, textArgb)
        views.setTextColor(R.id.widget_wide_chronometer, textArgb)
        views.setTextColor(R.id.widget_wide_total_duration, textArgb)
        // Turntable widget
        views.setTextColor(R.id.widget_turntable_chronometer, textArgb)
        views.setTextColor(R.id.widget_turntable_total_duration, textArgb)

        // Full-size widget (widget_music_player)
        views.setInt(R.id.widget_bg_image, "setColorFilter", bgArgb)
        views.setInt(R.id.widget_play_pause_bg_image, "setColorFilter", accentArgb)
        views.setInt(R.id.widget_like_button_bg_image, "setColorFilter", accentArgb)
        views.setInt(R.id.widget_progress_track, "setColorFilter", palette.background2.toArgb())
        views.setInt(R.id.widget_progress_fill, "setColorFilter", accentArgb)

        // Compact Square Widget
        views.setInt(R.id.widget_compact_bg_image, "setColorFilter", bgArgb)
        views.setInt(R.id.widget_compact_play_pill_bg, "setColorFilter", accentArgb)
        views.setInt(R.id.widget_compact_like_button_bg, "setColorFilter", accentArgb)

        // Compact Wide Widget
        views.setInt(R.id.widget_wide_bg_image, "setColorFilter", bgArgb)
        views.setInt(R.id.widget_wide_play_pause_bg, "setColorFilter", accentArgb)
        views.setInt(R.id.widget_wide_like_button_bg, "setColorFilter", accentArgb)

        // Turntable Widget
        views.setInt(R.id.widget_turntable_nav_bg_image, "setColorFilter", bgArgb)
        views.setInt(R.id.widget_turntable_play_bg_image, "setColorFilter", accentArgb)
        views.setInt(R.id.widget_turntable_like_bg_image, "setColorFilter", accentArgb)

        // Icon tints - black on light album art, white on dark
        views.setInt(R.id.widget_play_pause, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_like_button, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_prev_icon, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_next_icon, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_compact_play_pause, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_compact_prev_icon, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_compact_next_icon, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_wide_play_pause, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_wide_like_button, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_wide_prev_icon, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_wide_next_icon, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_turntable_play_pause, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_turntable_like_button, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_turntable_prev_button, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_turntable_next_button, "setColorFilter", iconTintArgb)
    }
}
