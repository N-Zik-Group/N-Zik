package app.n_zik.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.n_zik.android.R
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import app.it.fast4x.rimusic.ui.styling.colorPaletteOf
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import androidx.compose.ui.graphics.toArgb
import android.content.res.Configuration
import app.n_zik.android.MainActivity
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.cleanPrefix
import android.net.Uri
import android.os.SystemClock
import android.util.TypedValue
import android.os.Build

object PlaylistWidgetManager {

    data class QuickPick(
        val title: String,
        val artworkBitmap: Bitmap?,
        val targetIntent: PendingIntent
    )

    private var cachedQuickPicks: List<QuickPick>? = null
    private var lastQuickPicksUpdateTime: Long = 0L


    private suspend fun getQuickPicks(context: Context, accentArgb: Int): List<QuickPick> {
        val now = System.currentTimeMillis()
        if (cachedQuickPicks != null && now - lastQuickPicksUpdateTime < 60_000) {
            val picks = cachedQuickPicks?.toMutableList() ?: return emptyList()
            if (picks.isNotEmpty() && picks[0].title == context.getString(R.string.favorites)) {
                picks[0] = picks[0].copy(artworkBitmap = getLikedBitmap(context, accentArgb))
            }
            return picks
        }
        val picks = buildQuickPicks(context, accentArgb)
        cachedQuickPicks = picks
        lastQuickPicksUpdateTime = now
        return picks
    }

    private suspend fun loadBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = getImageLoader(context).execute(request)
            result.image?.toBitmap()
        } catch (e: Exception) {
            null
        }
    }

    private fun getLikedBitmap(context: Context, accentColor: Int): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = accentColor
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val drawable = context.getDrawable(R.drawable.ic_widget_heart_nav)
        if (drawable != null) {
            drawable.setTint(android.graphics.Color.WHITE)
            val iconSize = (size * 0.5f).toInt()
            val offset = (size - iconSize) / 2
            drawable.setBounds(offset, offset, offset + iconSize, offset + iconSize)
            drawable.draw(canvas)
        }
        return bitmap
    }

    private suspend fun buildQuickPicks(context: Context, accentArgb: Int): List<QuickPick> = withContext(Dispatchers.IO) {
        val items = mutableListOf<QuickPick>()

        val likedIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("nzik://app/playFavorites")
        }
        val likedPendingIntent = PendingIntent.getActivity(context, "Favorites".hashCode(), likedIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        items.add(QuickPick(context.getString(R.string.favorites), getLikedBitmap(context, accentArgb), likedPendingIntent))

        val playlists = Database.playlistTable.sortPreviewsByPlayCount(8).firstOrNull() ?: emptyList()
        playlists.forEach { p ->
            val firstSong = Database.songPlaylistMapTable.sortSongsByPosition(p.playlist.id, 1).firstOrNull()?.firstOrNull()
            val artworkUri = firstSong?.thumbnailUrl
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("nzik://app/localPlaylist/${p.playlist.id}")
            }
            val pendingIntent = PendingIntent.getActivity(context, p.playlist.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            items.add(QuickPick(p.playlist.name, loadBitmap(context, artworkUri), pendingIntent))
        }

        val followingArtists = Database.artistTable.sortFollowingByPlayCount(8).firstOrNull() ?: emptyList()
        val libraryArtists = Database.artistTable.sortInLibraryByPlayCount(8).firstOrNull() ?: emptyList()
        (followingArtists + libraryArtists).distinctBy { it.id }.forEach { a ->
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("nzik://app/channel/${a.id}")
            }
            val pendingIntent = PendingIntent.getActivity(context, a.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            items.add(QuickPick(a.name ?: "Unknown", loadBitmap(context, a.thumbnailUrl), pendingIntent))
        }

        val bookmarkedAlbums = Database.albumTable.sortBookmarkedByPlayCount(8).firstOrNull() ?: emptyList()
        val libraryAlbums = Database.albumTable.sortInLibraryByPlayCount(8).firstOrNull() ?: emptyList()
        (bookmarkedAlbums + libraryAlbums).distinctBy { it.id }.forEach { a ->
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("nzik://app/album/${a.id}")
            }
            val pendingIntent = PendingIntent.getActivity(context, a.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            items.add(QuickPick(a.title ?: "Unknown", loadBitmap(context, a.thumbnailUrl), pendingIntent))
        }
        
        val randomSongs = Database.songTable.all().firstOrNull()?.shuffled()?.take(8) ?: emptyList()
        randomSongs.forEach { s ->
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("nzik://app/watch?v=${s.id}")
            }
            val pendingIntent = PendingIntent.getActivity(context, s.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            items.add(QuickPick(s.title, loadBitmap(context, s.thumbnailUrl), pendingIntent))
        }

        val firstItem = items.first()
        val restItems = items.drop(1).shuffled()
        val picks = listOf(firstItem) + restItems.take(7)

        val now = SystemClock.elapsedRealtime()
        cachedQuickPicks = picks
        lastQuickPicksUpdateTime = now
        picks
    }

    private var imageLoader: ImageLoader? = null

    private fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: ImageLoader.Builder(context)
            .crossfade(false)
            .build().also { imageLoader = it }
    }


    
    private val roundedAppIconCache = ConcurrentHashMap<Int, Bitmap>()

    @Volatile
    private var lastWidgetState: WidgetState? = null

    suspend fun updateIdleWidgets(context: Context) {
        updateWidgets(
            context = context,
            title = context.getString(R.string.not_playing),
            artist = context.getString(R.string.choose_something_below),
            artworkBitmap = null,
            isPlaying = false,
            isLiked = false,
            duration = 0,
            currentPosition = 0,
        )
    }

    suspend fun updateIdleWidget(
        context: Context,
        appWidgetId: Int,
        options: Bundle,
    ) {
        val state = lastWidgetState
        if (state == null) {
            updateIdleWidgets(context)
            return
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val albumArt = state.artworkBitmap
        val palette = extractPalette(context, albumArt)
        val views = createRemoteViews(
            context = context,
            options = options,
            title = state.title,
            artist = state.artist,
            albumArt = albumArt,
            isPlaying = state.isPlaying,
            isLiked = state.isLiked,
            duration = state.duration,
            currentPosition = state.currentPosition,
            palette = palette,
        )
        appWidgetManager.updateAppWidget(appWidgetId, views)
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
        palette: ColorPalette? = null,
    ) {
        lastWidgetState = WidgetState(
            title = title,
            artist = artist,
            artworkBitmap = artworkBitmap,
            isPlaying = isPlaying,
            isLiked = isLiked,
            duration = duration,
            currentPosition = currentPosition,
        )

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, PlaylistWidgetReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isEmpty()) return

        val resolvedPalette = palette ?: extractPalette(context, artworkBitmap)

        widgetIds.forEach { widgetId ->
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val views = createRemoteViews(
                context = context,
                options = options,
                title = title,
                artist = artist,
                albumArt = artworkBitmap,
                isPlaying = isPlaying,
                isLiked = isLiked,
                duration = duration,
                currentPosition = currentPosition,
                palette = resolvedPalette,
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun extractPalette(context: Context, bitmap: Bitmap?): ColorPalette {
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
        }

        // Fallback: extract from bitmap
        return if (bitmap != null) {
            dynamicColorPaletteOf(bitmap, isSystemInDarkMode)
                ?: defaultPalette
        } else {
            defaultPalette
        }
    }

    private suspend fun createRemoteViews(
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
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playlist)

        val cleaned = cleanPrefix(title)
        val finalTitle = if (title.startsWith("e:", true) || title.startsWith("\uD83C\uDD74")) {
            "\uD83C\uDD74 $cleaned"
        } else {
            cleaned
        }
        views.setTextViewText(R.id.widget_playlist_song_title, finalTitle)
        views.setTextViewText(R.id.widget_playlist_artist_name, artist)

        if (albumArt != null) {
            views.setImageViewBitmap(R.id.widget_playlist_album_art, getRoundedAlbumArt(albumArt))
        } else {
            views.setImageViewBitmap(R.id.widget_playlist_album_art, getRoundedAppIcon(context, 36f))
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_playlist_play_pause, playPauseIcon)
        views.setImageViewResource(
            R.id.widget_playlist_like_button,
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav,
        )

        val progressLevel = if (duration > 0) {
            ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt().coerceIn(0, 10000)
        } else {
            0
        }
        views.setInt(R.id.widget_playlist_progress_fill, "setImageLevel", progressLevel)

        if (duration > 0) {
            views.setViewVisibility(R.id.widget_playlist_chronometer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_playlist_total_duration, View.VISIBLE)
            views.setViewVisibility(R.id.widget_progress_track, View.VISIBLE)
            views.setViewVisibility(R.id.widget_playlist_progress_fill, View.VISIBLE)
            val baseTime = SystemClock.elapsedRealtime() - currentPosition
            views.setChronometer(R.id.widget_playlist_chronometer, baseTime, null, isPlaying)
            views.setTextViewText(R.id.widget_playlist_total_duration, " / ${formatDuration(duration)}")
        } else {
            views.setViewVisibility(R.id.widget_playlist_chronometer, View.GONE)
            views.setViewVisibility(R.id.widget_playlist_total_duration, View.GONE)
            views.setViewVisibility(R.id.widget_progress_track, View.GONE)
            views.setViewVisibility(R.id.widget_playlist_progress_fill, View.GONE)
        }

        views.setOnClickPendingIntent(R.id.widget_playlist_album_art, getOpenAppIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_playlist_prev_container,
            getMusicWidgetIntent(context, MusicWidgetReceiver.ACTION_PREVIOUS, 501),
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_play_pause_container,
            getMusicWidgetIntent(context, MusicWidgetReceiver.ACTION_PLAY_PAUSE, 502),
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_next_container,
            getMusicWidgetIntent(context, MusicWidgetReceiver.ACTION_NEXT, 503),
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_like_button,
            getMusicWidgetIntent(context, MusicWidgetReceiver.ACTION_LIKE, 504),
        )

        // Unhide quick picks (let them be visible by default)
        views.setViewVisibility(R.id.widget_playlist_quick_picks_section, View.VISIBLE)
        
        // Responsive layout based on available height:
        // ~320dp needed for player + 2 rows, ~220dp for player + 1 row, ~120dp for player only
        // Real device values: Pixel large=345, Xiaomi large=291, Pixel small=226, Xiaomi small=189
        val widgetMaxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        
        when {
            widgetMaxHeight >= 320 -> {
                // Large widget (Pixel 4x3 = 345dp): show both rows
                views.setViewVisibility(R.id.widget_playlist_row_2, View.VISIBLE)
            }
            else -> {
                // Medium/small widget: show row 1 only
                views.setViewVisibility(R.id.widget_playlist_row_2, View.GONE)
            }
        }
        
        // Responsive width: shrink player controls when widget is narrow
        // Xiaomi narrow = maxW 255dp, controls need ~170dp but only ~227dp available with padding
        val widgetMaxWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        if (widgetMaxWidth in 1..299 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val smallBtn = 28f
            val playBtn = 36f
            views.setViewLayoutWidth(R.id.widget_playlist_prev_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_playlist_prev_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutWidth(R.id.widget_playlist_play_pause_container, playBtn, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_playlist_play_pause_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutWidth(R.id.widget_playlist_next_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_playlist_next_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutWidth(R.id.widget_playlist_like_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_playlist_like_container, smallBtn, TypedValue.COMPLEX_UNIT_DIP)
        }
        
        // Use pre-extracted accent color for Favorites icon
        val accentArgb = palette.accent.toArgb()

        val quickPicks = getQuickPicks(context, accentArgb)        
        val cardIds = listOf(
            R.id.widget_playlist_card_1, R.id.widget_playlist_card_2, R.id.widget_playlist_card_3, R.id.widget_playlist_card_4,
            R.id.widget_playlist_card_5, R.id.widget_playlist_card_6, R.id.widget_playlist_card_7, R.id.widget_playlist_card_8
        )
        val titleIds = listOf(
            R.id.widget_playlist_card_1_title, R.id.widget_playlist_card_2_title, R.id.widget_playlist_card_3_title, R.id.widget_playlist_card_4_title,
            R.id.widget_playlist_card_5_title, R.id.widget_playlist_card_6_title, R.id.widget_playlist_card_7_title, R.id.widget_playlist_card_8_title
        )
        val artIds = listOf(
            R.id.widget_playlist_card_1_art, R.id.widget_playlist_card_2_art, R.id.widget_playlist_card_3_art, R.id.widget_playlist_card_4_art,
            R.id.widget_playlist_card_5_art, R.id.widget_playlist_card_6_art, R.id.widget_playlist_card_7_art, R.id.widget_playlist_card_8_art
        )
        val playIds = listOf(
            R.id.widget_playlist_card_1_play_container, R.id.widget_playlist_card_2_play_container, R.id.widget_playlist_card_3_play_container, R.id.widget_playlist_card_4_play_container,
            R.id.widget_playlist_card_5_play_container, R.id.widget_playlist_card_6_play_container, R.id.widget_playlist_card_7_play_container, R.id.widget_playlist_card_8_play_container
        )
        
        for (i in 0 until 8) {
            if (i < quickPicks.size) {
                val pick = quickPicks[i]
                views.setViewVisibility(cardIds[i], View.VISIBLE)
                val cleanedPick = cleanPrefix(pick.title)
            val finalPickTitle = if (pick.title.startsWith("e:", true) || pick.title.startsWith("\uD83C\uDD74")) {
                "\uD83C\uDD74 $cleanedPick"
            } else {
                cleanedPick
            }
                views.setTextViewText(titleIds[i], finalPickTitle)
                if (pick.artworkBitmap != null) {
                    views.setImageViewBitmap(artIds[i], getRoundedCornerBitmap(pick.artworkBitmap, 16f))
                } else {
                    views.setImageViewBitmap(artIds[i], getRoundedAppIcon(context, 16f))
                }
                views.setOnClickPendingIntent(cardIds[i], pick.targetIntent)
                views.setViewVisibility(playIds[i], View.GONE)
            } else {
                views.setViewVisibility(cardIds[i], View.INVISIBLE)
            }
        }
        
        applyWidgetTheme(context, views, palette, titleIds, cardIds)
        return views
    }


    private fun getRoundedAlbumArt(albumArt: Bitmap): Bitmap {
        return getRoundedCornerBitmap(albumArt, 36f)
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
        val scaledRadius = size * 0.12f // 12% of the image size perfectly matches the main album art (36f / 300px)
        canvas.drawRoundRect(rect, scaledRadius, scaledRadius, paint)
        if (squareBitmap != bitmap) squareBitmap.recycle()
        return output
    }

    private fun getRoundedAppIcon(context: Context, cornerRadius: Float): Bitmap {
        val cacheKey = cornerRadius.toInt()
        roundedAppIconCache[cacheKey]?.let { return it }

        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)

        return getRoundedCornerBitmap(bitmap, cornerRadius).also {
            roundedAppIconCache[cacheKey] = it
        }
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
            500,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getMusicWidgetIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    data class WidgetState(
        val title: String,
        val artist: String,
        val artworkBitmap: Bitmap?,
        val isPlaying: Boolean,
        val isLiked: Boolean,
        val duration: Long,
        val currentPosition: Long,
    )

    private fun applyWidgetTheme(context: Context, views: RemoteViews, palette: ColorPalette, titleIds: List<Int>, cardIds: List<Int>) {
        val isSystemInDarkMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val black = android.graphics.Color.BLACK
        val textPrimaryArgb = if (isSystemInDarkMode) palette.text.toArgb() else black
        val textSecondaryArgb = if (isSystemInDarkMode) palette.textSecondary.toArgb() else black
        val bgArgb = palette.background1.toArgb()
        val iconTintArgb = if (isSystemInDarkMode) android.graphics.Color.WHITE else black
        
        // Background
        views.setInt(R.id.widget_bg_image, "setColorFilter", bgArgb)

        // Text colors
        views.setTextColor(R.id.widget_playlist_song_title, textPrimaryArgb)
        views.setTextColor(R.id.widget_playlist_artist_name, textSecondaryArgb)
        views.setTextColor(R.id.widget_playlist_total_duration, textSecondaryArgb)
        views.setTextColor(R.id.widget_playlist_chronometer, textSecondaryArgb)

        // Button backgrounds & accent tint
        views.setInt(R.id.widget_playlist_play_pause_bg_image, "setColorFilter", palette.accent.toArgb())
        views.setInt(R.id.widget_playlist_like_button_bg_image, "setColorFilter", palette.accent.toArgb())
        
        // Progress bar
        views.setInt(R.id.widget_progress_track, "setColorFilter", palette.background2.toArgb())
        views.setInt(R.id.widget_playlist_progress_fill, "setColorFilter", palette.accent.toArgb())

        // Icon tints - always apply
        views.setInt(R.id.widget_playlist_play_pause, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_playlist_like_button, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_playlist_prev_icon, "setColorFilter", iconTintArgb)
        views.setInt(R.id.widget_playlist_next_icon, "setColorFilter", iconTintArgb)

        // Quick Picks Titles
        for (titleId in titleIds) {
            views.setTextColor(titleId, textPrimaryArgb)
        }
    }
}
