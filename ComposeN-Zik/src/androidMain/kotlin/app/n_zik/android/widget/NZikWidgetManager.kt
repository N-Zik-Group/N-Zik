package app.n_zik.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.widget.RemoteViews
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.components.player.m3eDynamicColorPaletteOf
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.ui.styling.colorPaletteOf
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import androidx.compose.ui.graphics.toArgb
import android.content.res.Configuration
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.cleanPrefix
import android.view.View
import android.os.SystemClock
import android.os.Build
import android.util.TypedValue

object NZikWidgetManager {

    private fun getImageLoader(context: Context): ImageLoader = ImageCacheFactory.LOADER

    // Cache for album art to avoid reloading
    private var cachedArtworkUri: String? = null
    private var cachedAlbumArt: Bitmap? = null
    private var cachedCircularAlbumArt: Bitmap? = null

    suspend fun updateIdleWidgets(context: Context) {
        // Clear saved palette so widget resets to default in idle
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .edit().remove("widget_palette_timestamp").apply()
        updateWidgets(
            context = context,
            title = context.getString(R.string.not_playing),
            artist = context.getString(R.string.tap_to_play),
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
        currentPosition: Long = 0
    ) {
        val (albumArt, palette) = withContext(NzikDispatchers.MEDIA) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Extract palette from ORIGINAL bitmap (before scaling) to match in-app colors
            val palette = extractPalette(context, artworkBitmap)

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

            val circularAlbumArt: Bitmap? = albumArt?.let { getCircularBitmap(it) }

            // Update main music player widgets
            val componentName = ComponentName(context, MusicWidgetReceiver::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isNotEmpty()) {
                widgetIds.forEach { widgetId ->
                    val options = appWidgetManager.getAppWidgetOptions(widgetId)
                    val views = createRemoteViewsForSize(
                        context,
                        options,
                        title,
                        artist,
                        albumArt,
                        isPlaying,
                        isLiked,
                        duration,
                        currentPosition,
                        palette
                    )
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }

            // Update turntable widgets
            val turntableComponentName = ComponentName(context, TurntableWidgetReceiver::class.java)
            val turntableWidgetIds = appWidgetManager.getAppWidgetIds(turntableComponentName)
            if (turntableWidgetIds.isNotEmpty()) {
                val turntableViews = createTurntableRemoteViews(
                    context,
                    circularAlbumArt,
                    isPlaying,
                    isLiked,
                    duration,
                    currentPosition,
                    palette
                )
                turntableWidgetIds.forEach { widgetId ->
                    appWidgetManager.updateAppWidget(widgetId, turntableViews)
                }
            }

            albumArt to palette
        }

        PlaylistWidgetManager.updateWidgets(
            context = context,
            title = title,
            artist = if (artworkBitmap == null) context.getString(R.string.choose_something_below) else artist,
            artworkBitmap = albumArt,
            isPlaying = isPlaying,
            isLiked = isLiked,
            duration = duration,
            currentPosition = currentPosition,
            palette = palette,
        )
    }

    private fun createRemoteViewsForSize(
        context: Context,
        options: Bundle,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long,
        palette: ColorPalette
    ): RemoteViews {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        val views = when {
            minWidth < 180 && minHeight < 100 -> {
                createCompactSquareRemoteViews(context, albumArt, isPlaying, palette)
            }
            minWidth >= 180 && minHeight < 100 -> {
                createCompactWideRemoteViews(context, title, artist, albumArt, isPlaying, isLiked, palette)
            }
            else -> {
                createRemoteViews(context, title, artist, albumArt, isPlaying, isLiked, duration, currentPosition, palette)
            }
        }

        // Responsive scaling (Android 12+)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val isNarrow = minWidth < 340
        val isCompactWide = minWidth >= 180 && minHeight < 100
        val isFullWidget = minHeight >= 100

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
                // Compact wide scaling
                val artSize = if (isNarrow) 32f else 48f
                views.setViewLayoutWidth(R.id.widget_wide_album_art, artSize, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_wide_album_art, artSize, TypedValue.COMPLEX_UNIT_DIP)
                
                val btnSize = if (isNarrow) 24f else 32f
                val playSize = if (isNarrow) 28f else 36f
                
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

        return views
    }

    private fun createRemoteViews(
        context: Context,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
        palette: ColorPalette
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)

        val cleaned = cleanPrefix(title)
        val finalTitle = if (title.startsWith("e:", true) || title.startsWith("\uD83C\uDD74")) {
            "\uD83C\uDD74 $cleaned"
        } else {
            cleaned
        }
        views.setTextViewText(R.id.widget_song_title, finalTitle)
        views.setTextViewText(R.id.widget_artist_name, artist)

        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_album_art, roundedAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_album_art, getRoundedDefaultIcon(context, 48f))
        }

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
        
        applyWidgetTheme(context, views, palette)

        return views
    }

    private suspend fun loadAlbumArt(context: Context, artworkUri: String, size: Int = 200): Bitmap? {
        return withContext(NzikDispatchers.DATA) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(size, size)
                    .allowHardware(false)
                    .crossfade(300)
                    .build()
                val result = getImageLoader(context).execute(request)
                result.image?.toBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }
        
        return output
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }
        return output
    }

    private fun createCompactSquareRemoteViews(
        context: Context,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        palette: ColorPalette
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_square)

        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_compact_album_art, roundedAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_compact_album_art, getRoundedDefaultIcon(context, 48f))
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        views.setImageViewResource(R.id.widget_compact_play_pause, playPauseIcon)

        views.setOnClickPendingIntent(R.id.widget_compact_album_art, getOpenAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compact_play_container, getPlayPauseIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compact_like_button, getLikeIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compact_prev_container, getPreviousIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compact_next_container, getNextIntent(context))

        applyWidgetTheme(context, views, palette)

        return views
    }

    private fun createCompactWideRemoteViews(
        context: Context,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        palette: ColorPalette
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)

        views.setTextViewText(R.id.widget_wide_song_title, title)
        views.setTextViewText(R.id.widget_wide_artist_name, artist)

        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_wide_album_art, roundedAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_wide_album_art, getRoundedDefaultIcon(context, 48f))
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        views.setImageViewResource(R.id.widget_wide_play_pause, playPauseIcon)

        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_wide_like_button, likeIcon)

        views.setOnClickPendingIntent(R.id.widget_wide_album_art, getOpenAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_wide_play_container, getPlayPauseIntent(context))
        views.setOnClickPendingIntent(R.id.widget_wide_like_button, getLikeIntent(context))
        views.setOnClickPendingIntent(R.id.widget_wide_prev_container, getPreviousIntent(context))
        views.setOnClickPendingIntent(R.id.widget_wide_next_container, getNextIntent(context))

        applyWidgetTheme(context, views, palette)

        return views
    }

    private fun createTurntableRemoteViews(
        context: Context,
        circularAlbumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
        palette: ColorPalette
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)

        if (circularAlbumArt != null) {
            views.setImageViewBitmap(R.id.widget_turntable_album_art, circularAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_turntable_album_art, getCircularDefaultIcon(context))
        }

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

        applyWidgetTheme(context, views, palette)

        return views
    }

    private fun isBitmapLight(bitmap: Bitmap): Boolean {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val scaled = Bitmap.createScaledBitmap(bitmap, 50, 50, true)
        var totalLuminance = 0L
        val pixelCount = scaled.width * scaled.height
        val pixels = IntArray(pixelCount)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        for (pixel in pixels) {
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
        }
        if (scaled != bitmap) scaled.recycle()
        val averageLuminance = totalLuminance.toDouble() / pixelCount
        return averageLuminance > 128
    }
    
    private fun getCircularDefaultIcon(context: Context): Bitmap {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return getCircularBitmap(bitmap)
    }
    
    private fun getRoundedDefaultIcon(context: Context, cornerRadius: Float): Bitmap {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return getRoundedCornerBitmap(bitmap, cornerRadius)
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

    /**
     * Resolves the palette the widget renders for [albumArt]: the palette saved by the app
     * (same bitmap = same colors) when the system theme matches it, otherwise the shared M3E
     * extraction `m3eDynamicColorPaletteOf` — the app's single source of cover-color extraction
     * (RiPlay-based: capped 8 palette, dominant swatch, achromatic neutralization) — so the
     * widget stays in full parity with what the app displays, including the `NEUTRAL_COVER`
     * case. Suspend: the extraction is CPU-bound, and every call site already runs inside
     * `withContext(NzikDispatchers.MEDIA)`.
     */
    internal suspend fun extractPalette(context: Context, albumArt: Bitmap?): ColorPalette {
        val isSystemInDarkMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val defaultPalette = colorPaletteOf(
            ColorPaletteName.Dynamic,
            if (isSystemInDarkMode) ColorPaletteMode.Dark else ColorPaletteMode.Light,
            isSystemInDarkMode
        )

        // Try to read palette saved by the app (same bitmap = same colors)
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("widget_palette_timestamp", 0)
        if (timestamp > 0) {
            val savedIsDark = prefs.getBoolean("widget_palette_isDark", isSystemInDarkMode)
            // If system theme matches app theme, use saved palette directly
            if (savedIsDark == isSystemInDarkMode) {
                return ColorPalette(
                    background0 = defaultPalette.background0,
                    background1 = androidx.compose.ui.graphics.Color(prefs.getInt("widget_palette_background1", defaultPalette.background1.toArgb())),
                    background2 = androidx.compose.ui.graphics.Color(prefs.getInt("widget_palette_background2", defaultPalette.background2.toArgb())),
                    background3 = defaultPalette.background3,
                    background4 = defaultPalette.background4,
                    accent = androidx.compose.ui.graphics.Color(prefs.getInt("widget_palette_accent", defaultPalette.accent.toArgb())),
                    onAccent = defaultPalette.onAccent,
                    text = androidx.compose.ui.graphics.Color(prefs.getInt("widget_palette_text", defaultPalette.text.toArgb())),
                    textSecondary = androidx.compose.ui.graphics.Color(prefs.getInt("widget_palette_textSecondary", defaultPalette.textSecondary.toArgb())),
                    textDisabled = defaultPalette.textDisabled,
                    isDark = savedIsDark,
                    iconButtonPlayer = defaultPalette.iconButtonPlayer,
                )
            }
            // Themes differ: re-extract from bitmap with system's isDark
        }

        // Fallback: extract from bitmap with the app's shared M3E extraction (RiPlay-based,
        // achromatic neutralization included) — same result as the app for the same cover.
        return if (albumArt != null) {
            m3eDynamicColorPaletteOf(albumArt, isSystemInDarkMode)
                ?: defaultPalette
        } else {
            defaultPalette
        }
    }

    private fun applyWidgetTheme(context: Context, views: RemoteViews, palette: ColorPalette) {
        val isSystemInDarkMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val bgArgb = palette.background1.toArgb()
        val accentArgb = palette.accent.toArgb()
        val black = android.graphics.Color.BLACK
        val white = android.graphics.Color.WHITE

        val iconTintArgb = if (isSystemInDarkMode) white else black
        val textArgb = if (isSystemInDarkMode) white else black

        // Text colors - full-size widget
        views.setTextColor(R.id.widget_song_title, textArgb)
        views.setTextColor(R.id.widget_artist_name, textArgb)
        views.setTextColor(R.id.widget_chronometer, textArgb)
        views.setTextColor(R.id.widget_total_duration, textArgb)
        // Compact wide widget
        views.setTextColor(R.id.widget_wide_song_title, textArgb)
        views.setTextColor(R.id.widget_wide_artist_name, textArgb)
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
