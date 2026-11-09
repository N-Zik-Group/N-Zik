package app.n_zik.android.widget

import kotlin.math.roundToInt

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.utils.artistThumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.thumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.uiRoundnessDpKey
import app.n_zik.android.R
import app.n_zik.android.core.coil.isVideoArtwork
import app.n_zik.android.core.coil.displayArtworkSource
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers

internal data class WidgetArtwork(val bitmap: Bitmap?, val artist: Boolean = false, val source: String? = null)

/** Uses the same preference defaults and bounded corner sizes as the in-app appearance. */
internal data class WidgetRoundness(val ui: Float, val thumbnail: Float, val artist: Float) {
    fun radius(width: Float, height: Float, density: Float, artwork: Boolean, isArtist: Boolean = false): Float {
        val value = if (!artwork) ui else if (isArtist) artist else thumbnail
        if (artwork && value >= 48f) return minOf(width, height) / 2f
        return BoundedCornerSize(value.dp, if (artwork) 0.25f else 0.4f)
            .toPx(Size(width, height), Density(density))
    }

    companion object {
        fun read(context: Context): WidgetRoundness = context.preferences.let {
            WidgetRoundness(it.getFloat(uiRoundnessDpKey, 25f),
                it.getFloat(thumbnailRoundnessDpKey, 12f), it.getFloat(artistThumbnailRoundnessDpKey, 48f))
        }
    }
}

/** Renders native RemoteViews images at their measured size without launching a UI. */
internal object WidgetAppearance {
    private data class MeasurementKey(
        val layout: Int, val width: Int, val height: Int, val density: Float,
        val fontScale: Float, val durationVisible: Boolean,
    )
    private data class Bounds(val width: Int, val height: Int, val square: Boolean)
    private val measurements = LruCache<MeasurementKey, Map<Int, Bounds>>(32)
    private val backgrounds = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val backgroundIds = setOf(
        R.id.widget_bg_image, R.id.widget_compact_bg_image, R.id.widget_wide_bg_image,
        R.id.widget_play_pause_bg_image, R.id.widget_like_button_bg_image,
        R.id.widget_compact_play_pill_bg, R.id.widget_compact_like_button_bg,
        R.id.widget_wide_play_pause_bg, R.id.widget_wide_like_button_bg,
        R.id.widget_turntable_nav_bg_image, R.id.widget_turntable_play_bg_image,
        R.id.widget_turntable_like_bg_image, R.id.widget_playlist_play_pause_bg_image,
        R.id.widget_playlist_like_button_bg_image,
    )
    private val artworkIds = setOf(
        R.id.widget_album_art, R.id.widget_compact_album_art, R.id.widget_wide_album_art,
        R.id.widget_turntable_album_art, R.id.widget_playlist_album_art,
        R.id.widget_playlist_card_1_art, R.id.widget_playlist_card_2_art,
        R.id.widget_playlist_card_3_art, R.id.widget_playlist_card_4_art,
        R.id.widget_playlist_card_5_art, R.id.widget_playlist_card_6_art,
        R.id.widget_playlist_card_7_art, R.id.widget_playlist_card_8_art,
    )
    private var observedPreferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Keeps idle widgets in sync even when the playback service is stopped. */
    @Synchronized
    fun observeIdlePreferences(context: Context) {
        val app = context.applicationContext
        val preferences = app.preferences
        if (observedPreferences === preferences) return
        preferenceListener?.let { observedPreferences?.unregisterOnSharedPreferenceChangeListener(it) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if ((key == null || key in setOf(uiRoundnessDpKey, thumbnailRoundnessDpKey, artistThumbnailRoundnessDpKey, cropVideoThumbnailsKey)) &&
                !PlayerServiceModern.isRunning && hasWidgets(app)) {
                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                    if (!PlayerServiceModern.isRunning) NZikWidgetManager.updateIdleWidgets(app)
                }
            }
        }
        observedPreferences = preferences
        preferenceListener = listener
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(MusicWidgetReceiver::class.java, PlaylistWidgetReceiver::class.java, TurntableWidgetReceiver::class.java)
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    suspend fun responsive(options: Bundle, create: suspend (Bundle) -> RemoteViews): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width > 0f && it.height > 0f }?.distinct()?.take(16).orEmpty()
            if (sizes.isNotEmpty()) {
                val variants = linkedMapOf<SizeF, RemoteViews>()
                for (size in sizes) variants[size] = create(sizedOptions(options, size.width.roundToInt(), size.height.roundToInt()))
                return RemoteViews(variants)
            }
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200).coerceAtLeast(1)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100).coerceAtLeast(1)
        val portrait = create(sizedOptions(options, width, height))
        val landscapeWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width).coerceAtLeast(1)
        val landscapeHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height).coerceAtLeast(1)
        return if (landscapeWidth == width && landscapeHeight == height) portrait
        else RemoteViews(create(sizedOptions(options, landscapeWidth, landscapeHeight)), portrait)
    }

    private fun sizedOptions(source: Bundle, width: Int, height: Int) = Bundle(source).apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    suspend fun apply(
        context: Context, views: RemoteViews, options: Bundle, durationVisible: Boolean,
        artwork: Map<Int, WidgetArtwork>,
    ) {
        observeIdlePreferences(context)
        val density = context.resources.displayMetrics.density
        val key = MeasurementKey(views.layoutId,
            (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200) * density).roundToInt().coerceAtLeast(1),
            (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100) * density).roundToInt().coerceAtLeast(1),
            density, context.resources.configuration.fontScale, durationVisible)
        val bounds = measurements.get(key) ?: withContext(NzikDispatchers.UI) {
            val root = views.apply(context, FrameLayout(context))
            val heightMode = if (root.layoutParams?.height == ViewGroup.LayoutParams.WRAP_CONTENT)
                View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY
            root.measure(View.MeasureSpec.makeMeasureSpec(key.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(key.height, heightMode))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            (backgroundIds + artworkIds).mapNotNull { id ->
                val view = root.findViewById<ImageView>(id) ?: return@mapNotNull null
                if (view.width <= 0 || view.height <= 0) return@mapNotNull null
                id to Bounds(view.width, view.height, view.scaleType == ImageView.ScaleType.FIT_CENTER)
            }.toMap().also { measurements.put(key, it) }
        }
        withContext(NzikDispatchers.MEDIA) {
            val roundness = WidgetRoundness.read(context)
            val cropVideo = context.preferences.getBoolean(cropVideoThumbnailsKey, true)
            bounds.forEach { (id, size) ->
                val image = artwork[id]
                if (image != null) {
                    val width = if (size.square) minOf(size.width, size.height) else size.width
                    val height = if (size.square) width else size.height
                    // The record is intrinsically circular, independently of thumbnail preferences.
                    val radius = if (id == R.id.widget_turntable_album_art) minOf(width, height) / 2f
                        else roundness.radius(width.toFloat(), height.toFloat(), density, true, image.artist)
                    val crop = image.artist || !isVideoArtwork(displayArtworkSource(image.source)) || cropVideo
                    views.setImageViewBitmap(id, artworkBitmap(context, image.bitmap, width, height, radius, crop))
                } else if (id in backgroundIds) {
                    val radius = roundness.radius(size.width.toFloat(), size.height.toFloat(), density, false)
                    val cacheKey = "${size.width}:${size.height}:$radius"
                    val bitmap = backgrounds.get(cacheKey) ?: renderBitmap(size.width, size.height, radius) { canvas, paint, rect ->
                        paint.color = Color.WHITE
                        canvas.drawRect(rect, paint)
                    }.also { backgrounds.put(cacheKey, it) }
                    views.setImageViewBitmap(id, bitmap)
                }
            }
        }
    }

    internal fun artworkBitmap(context: Context, bitmap: Bitmap?, width: Int, height: Int, radius: Float, crop: Boolean = true): Bitmap =
        renderBitmap(width, height, radius) { canvas, paint, rect ->
            if (bitmap == null) {
                val fallback = requireNotNull(context.getDrawable(R.drawable.ic_launcher_box))
                fallback.bounds = Rect(0, 0, rect.width().roundToInt(), rect.height().roundToInt())
                fallback.draw(canvas)
            } else {
                val scale = if (crop) maxOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
                    else minOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
                val left = (rect.width() - bitmap.width * scale) / 2f
                val top = (rect.height() - bitmap.height * scale) / 2f
                canvas.drawBitmap(bitmap, null, RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale), paint)
            }
        }

    private fun renderBitmap(width: Int, height: Int, radius: Float, draw: (Canvas, Paint, RectF) -> Unit): Bitmap {
        val scale = minOf(1f, 400f / maxOf(width, height))
        val result = Bitmap.createBitmap((width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val rect = RectF(0f, 0f, result.width.toFloat(), result.height.toFloat())
        val clip = Path().apply { addRoundRect(rect, radius * scale, radius * scale, Path.Direction.CW) }
        canvas.clipPath(clip)
        draw(canvas, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG), rect)
        return result
    }
}
