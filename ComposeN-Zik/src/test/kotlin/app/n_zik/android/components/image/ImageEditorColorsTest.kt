package app.n_zik.android.components.image

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.customColorKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.util.SelectedStateListDrawable
import com.yalantis.ucrop.view.widget.AspectRatioTextView
import com.yalantis.ucrop.view.widget.HorizontalProgressWheelView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.yalantis.ucrop.R as CropR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageEditorColorsTest {
    @Test
    fun `library options use NZik colors and expose crop rotate zoom and free handles`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val colors = ImageEditorColors(Color.BLACK, Color.DKGRAY, Color.WHITE, Color.CYAN, true)
        val options = colors.options(context).optionBundle
        assertEquals(Color.DKGRAY, options.getInt(UCrop.Options.EXTRA_TOOL_BAR_COLOR))
        assertEquals(Color.WHITE, options.getInt(UCrop.Options.EXTRA_UCROP_WIDGET_COLOR_TOOLBAR))
        assertEquals(Color.CYAN, options.getInt(UCrop.Options.EXTRA_UCROP_COLOR_CONTROLS_WIDGET_ACTIVE))
        assertFalse(options.getBoolean(UCrop.Options.EXTRA_STATUS_BAR_LIGHT, true))
        assertFalse(options.getBoolean(UCrop.Options.EXTRA_HIDE_BOTTOM_CONTROLS))
        assertTrue(options.getBoolean(UCrop.Options.EXTRA_FREE_STYLE_CROP))
        assertEquals(2048, options.getInt(UCrop.Options.EXTRA_MAX_BITMAP_SIZE))
        assertEquals("PNG", options.getString(UCrop.Options.EXTRA_COMPRESSION_FORMAT_NAME))
        assertEquals(3, requireNotNull(options.getIntArray(UCrop.Options.EXTRA_ALLOWED_GESTURES)).size)
    }

    @Test
    fun `native controls keep text icons selection dots and wheel ticks readable on light and dark surfaces`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        controller.setup()
        try {
            val activity = controller.get()
            for (surface in listOf(Color.BLACK, Color.WHITE, Color.rgb(32, 36, 47))) {
                // Deliberately hostile custom colors reproduce invisible text and selected accents.
                val colors = ImageEditorColors(surface, surface, surface, surface, surface == Color.BLACK)
                val options = colors.options(activity).optionBundle
                val text = options.getInt(UCrop.Options.EXTRA_UCROP_WIDGET_COLOR_TOOLBAR)
                val accent = options.getInt(UCrop.Options.EXTRA_UCROP_COLOR_CONTROLS_WIDGET_ACTIVE)
                assertContrast(text, surface)
                assertContrast(accent, surface)
                val root = FrameLayout(activity).apply { id = CropR.id.ucrop_photobox }
                val controls = FrameLayout(activity).apply { id = CropR.id.controls_wrapper }
                root.addView(controls)
                LayoutInflater.from(activity).inflate(CropR.layout.ucrop_controls, controls, true)
                // Match UCropActivity.setupStatesWrapper and its initially selected crop tab.
                for (id in listOf(CropR.id.image_view_state_aspect_ratio, CropR.id.image_view_state_rotate, CropR.id.image_view_state_scale)) {
                    val icon = controls.findViewById<ImageView>(id)
                    icon.setImageDrawable(SelectedStateListDrawable(requireNotNull(icon.drawable), accent))
                }
                controls.findViewById<View>(CropR.id.state_aspect_ratio).isSelected = true
                val ratios = controls.findViewById<ViewGroup>(CropR.id.layout_aspect_ratio)
                val ratioWrapper = LayoutInflater.from(activity).inflate(CropR.layout.ucrop_aspect_ratio, ratios, false) as ViewGroup
                ratios.addView(ratioWrapper)
                val ratio = ratioWrapper.getChildAt(0) as AspectRatioTextView
                ratio.setAspectRatio(AspectRatio("1:1", 1f, 1f))
                colors.applyControls(root)
                for (id in listOf(CropR.id.controls_wrapper, CropR.id.wrapper_controls, CropR.id.wrapper_states)) {
                    assertEquals(surface, (root.findViewById<View>(id).background as ColorDrawable).color)
                }
                assertNull(controls.findViewById<ImageView>(CropR.id.controls_shadow).imageTintList)
                for (selected in listOf(false, true, false)) {
                    ratioWrapper.isSelected = selected
                    assertEquals(if (selected) accent else text, ratio.currentTextColor)
                    for (id in listOf(CropR.id.state_aspect_ratio, CropR.id.state_rotate, CropR.id.state_scale)) {
                        val tab = controls.findViewById<ViewGroup>(id)
                        tab.isSelected = selected
                        for (index in 0 until tab.childCount) {
                            when (val child = tab.getChildAt(index)) {
                                is TextView -> assertContrast(child.currentTextColor, surface)
                                is ImageView -> {
                                    val tint = requireNotNull(child.imageTintList)
                                    assertEquals(if (selected) accent else text, tint.getColorForState(child.drawableState, 0))
                                    assertIconPixels(child, if (selected) accent else text)
                                }
                            }
                        }
                    }
                }
                ratioWrapper.isSelected = true
                assertPaintedColor(ratio, surface, accent, "selected ratio dot", bottomOnly = true)
                for (id in listOf(CropR.id.rotate_scroll_wheel, CropR.id.scale_scroll_wheel)) {
                    val wheel = controls.findViewById<HorizontalProgressWheelView>(id)
                    assertPaintedColor(wheel, surface, accent, "wheel ticks", excludeCenter = true)
                }
                for (id in listOf(CropR.id.layout_rotate_wheel, CropR.id.layout_scale_wheel)) {
                    val panel = controls.findViewById<ViewGroup>(id)
                    for (index in 0 until panel.childCount) {
                        val child = panel.getChildAt(index)
                        if (child is TextView) assertContrast(child.currentTextColor, surface)
                    }
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `every NZik palette mode and dynamic hue supplies readable toolbar and selected text`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.preferences
        try {
            for (name in ColorPaletteName.entries) for (mode in ColorPaletteMode.entries) {
                prefs.edit().clear().putEnum(colorPaletteNameKey, name).putEnum(colorPaletteModeKey, mode).commit()
                assertOptionsContrast(ImageEditorColors.resolve(context), context)
            }
            for (mode in listOf(ColorPaletteMode.Light, ColorPaletteMode.Dark)) for (hue in 0 until 360 step 30) {
                prefs.edit().clear().putEnum(colorPaletteNameKey, ColorPaletteName.CustomColor)
                    .putEnum(colorPaletteModeKey, mode)
                    .putInt(customColorKey, Color.HSVToColor(floatArrayOf(hue.toFloat(), 1f, 1f))).commit()
                assertOptionsContrast(ImageEditorColors.resolve(context), context)
            }
            prefs.edit().clear().putEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
                .putLong("widget_palette_timestamp", 1L)
                .putInt("widget_palette_background1", Color.WHITE)
                .putInt("widget_palette_background2", Color.WHITE)
                .putInt("widget_palette_text", Color.WHITE)
                .putInt("widget_palette_accent", Color.WHITE)
                .putBoolean("widget_palette_isDark", true).commit()
            val options = ImageEditorColors.resolve(context).options(context).optionBundle
            assertTrue(options.getBoolean(UCrop.Options.EXTRA_STATUS_BAR_LIGHT))
            assertTrue(options.getBoolean(UCrop.Options.EXTRA_NAVIGATION_BAR_LIGHT))
            assertOptionsContrast(ImageEditorColors.resolve(context), context)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private fun assertOptionsContrast(colors: ImageEditorColors, context: Context) {
        val options = colors.options(context).optionBundle
        val surface = options.getInt(UCrop.Options.EXTRA_TOOL_BAR_COLOR)
        assertEquals(255, Color.alpha(surface))
        assertContrast(options.getInt(UCrop.Options.EXTRA_UCROP_WIDGET_COLOR_TOOLBAR), surface)
        assertContrast(options.getInt(UCrop.Options.EXTRA_UCROP_COLOR_CONTROLS_WIDGET_ACTIVE), surface)
    }

    private fun assertContrast(foreground: Int, background: Int) {
        assertTrue("Insufficient contrast: ${Integer.toHexString(foreground)} on ${Integer.toHexString(background)}",
            ColorUtils.calculateContrast(foreground, background) >= 4.5)
    }

    private fun assertIconPixels(icon: ImageView, expected: Int) {
        val drawable = requireNotNull(icon.drawable)
        val previousBounds = Rect(drawable.bounds)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, 64, 64)
            drawable.draw(Canvas(bitmap))
            var opaquePixels = 0
            for (y in 0 until 64) for (x in 0 until 64) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) == 255) {
                    assertEquals("Native icon ${icon.id} reverted to library colors", expected, pixel)
                    opaquePixels++
                }
            }
            assertTrue("Native icon ${icon.id} did not draw", opaquePixels > 5)
        } finally {
            drawable.bounds = previousBounds
            bitmap.recycle()
        }
    }

    private fun assertPaintedColor(
        view: View,
        surface: Int,
        expected: Int,
        label: String,
        bottomOnly: Boolean = false,
        excludeCenter: Boolean = false,
    ) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.visibility = View.VISIBLE
        val parent = FrameLayout(view.context).apply {
            setBackgroundColor(surface)
            addView(view, FrameLayout.LayoutParams(320, 80))
        }
        parent.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 320, 80)
        val bitmap = Bitmap.createBitmap(320, 80, Bitmap.Config.ARGB_8888)
        try {
            parent.draw(Canvas(bitmap))
            var matchingPixels = 0
            for (y in (if (bottomOnly) 50 else 0) until 80) for (x in 80 until 240) {
                if ((!excludeCenter || x !in 150..170) && bitmap.getPixel(x, y) == expected) matchingPixels++
            }
            assertTrue("$label did not draw its readable color", matchingPixels > 5)
        } finally {
            bitmap.recycle()
        }
    }
}
