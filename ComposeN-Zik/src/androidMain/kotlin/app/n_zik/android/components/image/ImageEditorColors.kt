package app.n_zik.android.components.image

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.ui.styling.applyPitchBlack
import app.n_zik.android.ui.styling.colorPaletteOf
import app.n_zik.android.ui.styling.customColorPalette
import app.n_zik.android.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.customColorKey
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.preferences
import app.n_zik.android.R
import com.kieronquinn.monetcompat.core.MonetCompat
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.view.widget.AspectRatioTextView
import com.yalantis.ucrop.view.widget.HorizontalProgressWheelView
import android.graphics.Color as AndroidColor
import com.yalantis.ucrop.R as CropR

/** Adapts NZik's selected palette to uCrop's native controls. */
internal data class ImageEditorColors(val background: Int, val surface: Int, val text: Int, val accent: Int, val dark: Boolean) {
    fun options(context: Context, originalSize: Boolean = false): UCrop.Options =
        readable().createOptions(context, originalSize)

    private fun createOptions(context: Context, originalSize: Boolean) = UCrop.Options().apply {
        setToolbarTitle(context.getString(R.string.image_editor_title))
        setToolbarColor(surface)
        setToolbarWidgetColor(text)
        setRootViewBackgroundColor(background)
        setActiveControlsWidgetColor(accent)
        setLogoColor(accent)
        setStatusBarLight(!dark)
        setNavigationBarLight(!dark)
        setCompressionFormat(Bitmap.CompressFormat.PNG)
        setCompressionQuality(100)
        if (!originalSize) setMaxBitmapSize(2048)
        setImageToCropBoundsAnimDuration(200)
        setFreeStyleCropEnabled(true)
        setAllowedGestures(UCropActivity.SCALE, UCropActivity.ROTATE, UCropActivity.ALL)
        setAspectRatioOptions(0,
            AspectRatio(context.getString(R.string.image_editor_original), 0f, 0f),
            AspectRatio(null, 1f, 1f), AspectRatio(null, 4f, 3f),
            AspectRatio(null, 16f, 9f), AspectRatio(null, 3f, 4f))
    }

    fun applyControls(root: View) {
        val (background, surface, text, accent) = readable()
        root.findViewById<View>(CropR.id.ucrop_photobox).setBackgroundColor(background)
        val controls = root.findViewById<ViewGroup>(CropR.id.controls_wrapper)
        controls.setBackgroundColor(surface)
        root.findViewById<View>(CropR.id.wrapper_states).setBackgroundColor(surface)
        // The library's decorative panel otherwise remains white in a dark NZik theme.
        root.findViewById<ViewGroup>(CropR.id.wrapper_controls).apply {
            setBackgroundColor(surface)
            getChildAt(0).setBackgroundColor(surface)
        }
        // uCrop's runtime wrappers override tint with explicit filters when selection changes.
        for ((id, drawable) in listOf(
            CropR.id.image_view_state_aspect_ratio to CropR.drawable.ucrop_crop,
            CropR.id.image_view_state_rotate to CropR.drawable.ucrop_rotate,
            CropR.id.image_view_state_scale to CropR.drawable.ucrop_scale,
        )) {
            root.findViewById<ImageView>(id).setImageDrawable(
                AppCompatResources.getDrawable(root.context, drawable)?.mutate(),
            )
        }
        val colors = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()), intArrayOf(accent, text))
        fun tint(view: View) {
            if (view is AspectRatioTextView) view.setActiveColor(accent)
            if (view is TextView) view.setTextColor(colors)
            if (view is ImageView && view.drawable != null && view.id != CropR.id.controls_shadow) view.imageTintList = colors
            if (view is HorizontalProgressWheelView) {
                view.setMiddleLineColor(accent)
                // uCrop exposes only the center-line color; tint its drawing layer to include the ticks.
                view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply {
                    colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN)
                })
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) tint(view.getChildAt(index))
        }
        tint(controls)
    }

    private fun readable(): ImageEditorColors {
        val opaqueSurface = ColorUtils.setAlphaComponent(surface, 255)
        return copy(
            background = ColorUtils.setAlphaComponent(background, 255),
            surface = opaqueSurface,
            text = contrasting(text, opaqueSurface),
            accent = contrasting(accent, opaqueSurface),
            dark = ColorUtils.calculateContrast(AndroidColor.WHITE, opaqueSurface) >
                ColorUtils.calculateContrast(AndroidColor.BLACK, opaqueSurface),
        )
    }

    private fun contrasting(color: Int, surface: Int): Int {
        val opaque = ColorUtils.compositeColors(color, surface)
        if (ColorUtils.calculateContrast(opaque, surface) >= 4.5) return opaque
        val target = if (ColorUtils.calculateContrast(AndroidColor.WHITE, surface) >
            ColorUtils.calculateContrast(AndroidColor.BLACK, surface)) AndroidColor.WHITE else AndroidColor.BLACK
        // Preserve the theme's hue as far as the text contrast requirement permits.
        var low = 0f
        var high = 1f
        repeat(12) {
            val fraction = (low + high) / 2f
            if (ColorUtils.calculateContrast(ColorUtils.blendARGB(opaque, target, fraction), surface) >= 4.5) {
                high = fraction
            } else {
                low = fraction
            }
        }
        return ColorUtils.blendARGB(opaque, target, high)
    }

    companion object {
        fun resolve(context: Context): ImageEditorColors {
            val prefs = context.preferences
            val mode = prefs.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
            val name = prefs.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
            val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val dark = mode == ColorPaletteMode.Dark || mode == ColorPaletteMode.PitchBlack || (mode == ColorPaletteMode.System && systemDark)
            var palette = colorPaletteOf(name, mode, systemDark)
            when (name) {
                ColorPaletteName.CustomColor -> palette = dynamicColorPaletteOf(Color(prefs.getInt(customColorKey, Color.Green.hashCode())), dark)
                ColorPaletteName.Customized -> palette = customColorPalette(palette, context, systemDark)
                ColorPaletteName.MaterialYou -> runCatching { MonetCompat.getInstance().getAccentColor(context) }
                    .getOrNull()?.let { palette = dynamicColorPaletteOf(Color(it), dark) }
                else -> Unit
            }
            if (mode == ColorPaletteMode.PitchBlack) palette = palette.applyPitchBlack
            // MainActivity publishes the fully resolved artwork palette for non-Compose consumers.
            val dynamic = name == ColorPaletteName.Dynamic && prefs.contains("widget_palette_timestamp")
            return if (dynamic) ImageEditorColors(
                prefs.getInt("widget_palette_background1", palette.background1.toArgb()),
                prefs.getInt("widget_palette_background2", palette.background2.toArgb()),
                prefs.getInt("widget_palette_text", palette.text.toArgb()),
                prefs.getInt("widget_palette_accent", palette.accent.toArgb()),
                prefs.getBoolean("widget_palette_isDark", palette.isDark),
            ) else ImageEditorColors(palette.background0.toArgb(), palette.background1.toArgb(), palette.text.toArgb(), palette.accent.toArgb(), palette.isDark)
        }
    }
}
