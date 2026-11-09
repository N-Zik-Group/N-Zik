package app.n_zik.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.components.player.ArtworkColorSnapshot
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers

object PlaylistWidgetManager {

    data class QuickPick(
        val title: String,
        val artworkBitmap: Bitmap?,
        val targetIntent: PendingIntent,
        val isArtist: Boolean = false,
        val artworkSource: String? = null,
    )

    private data class QuickPickCache(val selection: List<PlaylistQuickPickSource.Entry>, val picks: List<QuickPick>)
    @Volatile private var quickPickCache: QuickPickCache? = null

    internal suspend fun getQuickPicks(context: Context, accentArgb: Int): List<QuickPick> = withContext(NzikDispatchers.DATA) {
        val selection = PlaylistQuickPickSource.shared.load(context)
        val cached = quickPickCache
        if (cached != null && cached.selection === selection) {
            return@withContext cached.picks.mapIndexed { index, pick ->
                if (selection[index].target == PlaylistQuickPickSource.Target.Favorites)
                    pick.copy(artworkBitmap = getLikedBitmap(context, accentArgb)) else pick
            }
        }
        val picks = selection.map { entry ->
            val (destination, requestCode) = when (val target = entry.target) {
                PlaylistQuickPickSource.Target.Favorites -> "nzik://app/playFavorites" to "Favorites".hashCode()
                is PlaylistQuickPickSource.Target.Playlist -> "nzik://app/localPlaylist/${target.id}" to target.id.hashCode()
                is PlaylistQuickPickSource.Target.Artist -> "nzik://app/channel/${target.id}" to target.id.hashCode()
                is PlaylistQuickPickSource.Target.Album -> "nzik://app/album/${target.id}" to target.id.hashCode()
                is PlaylistQuickPickSource.Target.Track -> "nzik://app/watch?v=${target.song.id}" to target.song.id.hashCode()
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(destination)
            }
            val pending = PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val artwork = if (entry.target == PlaylistQuickPickSource.Target.Favorites)
                getLikedBitmap(context, accentArgb) else loadBitmap(context, entry.artworkSource)
            QuickPick(entry.title, artwork, pending, entry.target is PlaylistQuickPickSource.Target.Artist, entry.artworkSource)
        }
        quickPickCache = QuickPickCache(selection, picks)
        picks
    }

    private suspend fun loadBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = getImageLoader().execute(request)
            result.image?.toBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("PlaylistWidgetManager").w(e, "Unable to load widget artwork")
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
            drawable.setTint(Color.WHITE)
            val iconSize = (size * 0.5f).toInt()
            val offset = (size - iconSize) / 2
            drawable.setBounds(offset, offset, offset + iconSize, offset + iconSize)
            drawable.draw(canvas)
        }
        return bitmap
    }

    private fun getImageLoader(): ImageLoader = ImageCacheFactory.LOADER

    @Volatile
    private var lastWidgetState: WidgetState? = null

    /** Refreshes the requested widgets using their current sizes and playback snapshot. */
    internal suspend fun refreshIdleWidgets(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        if (ids.isEmpty()) return
        if (lastWidgetState == null) {
            updateIdleWidgets(context)
            return
        }
        ids.forEach { id -> updateIdleWidget(context, id, manager.getAppWidgetOptions(id)) }
    }

    suspend fun updateIdleWidgets(context: Context) {
        updateWidgets(
            context = context,
            title = context.getString(R.string.widget_not_playing_status),
            artist = context.getString(R.string.widget_configuration_choose_item_hint),
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
        withContext(NzikDispatchers.MEDIA) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val albumArt = state.artworkBitmap
            val snapshot = if (state.profileId == getActiveProfile(context)) state.artworkColors
                else loadWidgetArtworkColors(context, state.artworkUri, null)
            val palette = widgetArtworkPalette(context, snapshot)
            val views = WidgetAppearance.responsive(options) { sizedOptions -> createRemoteViews(
                context = context,
                options = sizedOptions,
                title = state.title,
                artist = state.artist,
                albumArt = albumArt,
                isPlaying = state.isPlaying,
                isLiked = state.isLiked,
                duration = state.duration,
                currentPosition = state.currentPosition,
                palette = palette,
                artworkUri = state.artworkUri,
            ) }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
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
        artworkUri: String? = null,
        artworkColors: ArtworkColorSnapshot? = null,
        artworkColorsResolved: Boolean = false,
    ) {
        val (snapshot, profileId) = withContext(NzikDispatchers.MEDIA) {
            val profile = getActiveProfile(context)
            val source = if (artworkColorsResolved) artworkColors
                else artworkColors ?: loadWidgetArtworkColors(context, artworkUri, artworkBitmap)
            source to profile
        }
        lastWidgetState = WidgetState(
            title = title,
            artist = artist,
            artworkBitmap = artworkBitmap,
            isPlaying = isPlaying,
            isLiked = isLiked,
            duration = duration,
            currentPosition = currentPosition,
            artworkColors = snapshot,
            artworkUri = artworkUri,
            profileId = profileId,
        )

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, PlaylistWidgetReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isEmpty()) return

        withContext(NzikDispatchers.MEDIA) {
            val resolvedPalette = palette ?: widgetArtworkPalette(context, snapshot)

            widgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val views = WidgetAppearance.responsive(options) { sizedOptions -> createRemoteViews(
                    context = context,
                    options = sizedOptions,
                    title = title,
                    artist = artist,
                    albumArt = artworkBitmap,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    duration = duration,
                    currentPosition = currentPosition,
                    palette = resolvedPalette,
                    artworkUri = artworkUri,
                ) }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    internal suspend fun extractPalette(context: Context, bitmap: Bitmap?): ColorPalette =
        widgetArtworkPalette(context, loadWidgetArtworkColors(context, null, bitmap))

    internal suspend fun createRemoteViews(
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
        val views = RemoteViews(context.packageName, R.layout.widget_playlist)

        views.setTextViewText(R.id.widget_playlist_song_title, widgetDisplayTitle(title))
        views.setTextViewText(R.id.widget_playlist_artist_name, cleanPrefix(artist))

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
        val widgetMaxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

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
        val widgetMaxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
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
                views.setTextViewText(titleIds[i], widgetDisplayTitle(pick.title))
                views.setOnClickPendingIntent(cardIds[i], pick.targetIntent)
                views.setViewVisibility(playIds[i], View.GONE)
            } else {
                views.setViewVisibility(cardIds[i], View.INVISIBLE)
            }
        }

        applyWidgetTheme(views, palette, titleIds)
        val artwork = mutableMapOf(R.id.widget_playlist_album_art to WidgetArtwork(albumArt, source = artworkUri))
        quickPicks.take(artIds.size).forEachIndexed { index, pick ->
            artwork[artIds[index]] = WidgetArtwork(pick.artworkBitmap, pick.isArtist, pick.artworkSource)
        }
        WidgetAppearance.apply(context, views, options, duration > 0, artwork)
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
        val artworkColors: ArtworkColorSnapshot? = null,
        val artworkUri: String? = null,
        val profileId: String? = null,
    )

    private fun applyWidgetTheme(views: RemoteViews, palette: ColorPalette, titleIds: List<Int>) {
        val bgArgb = palette.background1.toArgb()
        // Keyed on the effective palette tone, not isSystemInDarkMode (spec-achromatic-ramp-luminance-cap)
        val textPrimaryArgb = widgetTextArgb(palette)
        val textSecondaryArgb = widgetSecondaryTextArgb(palette)
        val iconTintArgb = widgetIconTintArgb(palette)

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
