// Lens mapping adapted from vendored Kyant0/backdrop (Apache-2.0); see backdrop/internal/Shaders.kt.
package app.n_zik.android.components.player

import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassEdgeLightColor
import app.n_zik.android.components.glass.glassTint
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.asAndroidRuntimeShader
import app.n_zik.android.components.glass.backdrop.effects.runtimeShaderEffect

/** This immutable, unscaled field is shared by both player variants, independent of rotation. */
@Composable
internal fun rememberPlayerRibbedField(enabled: Boolean): PlayerRibbedField? {
    val resources = LocalContext.current.resources
    return remember(resources, enabled) {
        if (enabled && Build.VERSION.SDK_INT >= 33)
            PlayerRibbedField(ImageBitmap.imageResource(resources, R.drawable.player_ribbed_sdf).asAndroidBitmap())
        else null
    }
}

@RequiresApi(33)
internal class PlayerRibbedField(val bitmap: Bitmap) {
    val shader = bitmap.fieldShader()
}

@RequiresApi(33)
private fun Bitmap.fieldShader() = BitmapShader(this, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
    setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
}

internal fun BackdropEffectScope.playerRibbedLens(field: PlayerRibbedField?, height: Float, amount: Float,
    depthEffect: Boolean, dispersion: Boolean) {
    if (Build.VERSION.SDK_INT < 33 || field == null || height <= 0f || amount <= 0f) return
    padding = (padding - height).coerceAtLeast(0f)
    runtimeShaderEffect("PlayerRibbedLens", RibbedLensShader, "content") {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("fieldSize", field.bitmap.width.toFloat())
        setFloatUniform("height", height)
        setFloatUniform("amount", -amount)
        setFloatUniform("depth", if (depthEffect) 1f else 0f)
        setFloatUniform("dispersion", if (dispersion) 1f else 0f)
        // Raw input buffers preserve the RG16 data in wide-gamut player windows too.
        asAndroidRuntimeShader().setInputBuffer("field", field.shader)
    }
}

/** Actual boundary-normal highlight on API33; shared directional contour on older renderers. */
@Composable
internal fun Modifier.playerRibbedHighlight(settings: GlassSettings, field: PlayerRibbedField?, opticsProgress: Float = 1f): Modifier {
    val settings = settings.inScope(GlassScope.PlayerTransport)
    if (!settings.usesGlassContour(GlassArea.ActionButtons)) return this
    if (Build.VERSION.SDK_INT < 33 || field == null)
        return glassAreaContour(settings.copy(rim = settings.rim * opticsProgress), GlassArea.ActionButtons, PlayerTransportRibbedShape)
    val color = glassEdgeLightColor(settings, glassTint(settings))
        .copy(alpha = (.3f * settings.rim / .55f * opticsProgress).coerceIn(0f, 1f))
    return drawWithCache {
        val shader = android.graphics.RuntimeShader(RibbedHighlightShader).apply {
            setFloatUniform("size", size.width, size.height)
            setFloatUniform("offset", 0f, 0f)
            setFloatUniform("fieldSize", field.bitmap.width.toFloat())
            setColorUniform("color", color.toArgb())
            setInputBuffer("field", field.shader)
        }
        val outline = PlayerTransportRibbedShape.createOutline(size, layoutDirection, this)
        val paint = Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = kotlin.math.ceil(.8.dp.toPx()) * 2f
            blendMode = BlendMode.Plus
            this.shader = shader
        }
        onDrawWithContent {
            drawContent()
            // The caller clips the whole pane with this identical outline, keeping its rim inside.
            drawContext.canvas.drawOutline(outline, paint)
        }
    }
}

// RG16 distance, signed in original normalized pane units. Five constant-time lookups
// replace 360 segment tests per fragment. Clip remains the original exact polygon.
private const val RibbedFieldShader = """
uniform shader field;
uniform float2 size;
uniform float2 offset;
uniform float fieldSize;
float distanceAt(float2 uv) {
    half4 data = field.eval(uv * fieldSize);
    return ((float(data.r) * 65280.0 + float(data.g) * 255.0) / 65535.0 - 0.5) * 2.0;
}
float2 boundaryNormal(float2 uv) {
    float2 texel = float2(1.0 / fieldSize, 0.0);
    float2 g = float2(distanceAt(uv + texel) - distanceAt(uv - texel),
        distanceAt(uv + texel.yx) - distanceAt(uv - texel.yx));
    return g / max(length(g), 0.000001);
}
"""

internal const val RibbedLensShader = """
uniform shader content;
uniform float height;
uniform float amount;
uniform float depth;
uniform float dispersion;
$RibbedFieldShader
half4 main(float2 coord) {
    float2 uv = (coord + offset) / size;
    float sd = distanceAt(uv) * min(size.x, size.y);
    if (-sd >= height) return content.eval(coord);
    float x = clamp(1.0 + min(sd, 0.0) / height, 0.0, 1.0);
    float d = (1.0 - sqrt(max(0.0, 1.0 - x * x))) * amount;
    float2 radial = uv - 0.5;
    radial /= max(length(radial), 0.000001);
    float2 normal = boundaryNormal(uv) + depth * radial;
    normal /= max(length(normal), 0.000001);
    float2 refracted = coord + d * normal;
    if (dispersion < 0.5) return content.eval(refracted);
    float2 spread = d * normal * ((uv.x - 0.5) * (uv.y - 0.5) * 4.0);
    half4 r = content.eval(refracted + spread);
    half4 o = content.eval(refracted + spread * (2.0 / 3.0));
    half4 y = content.eval(refracted + spread / 3.0);
    half4 g = content.eval(refracted);
    half4 c = content.eval(refracted - spread / 3.0);
    half4 b = content.eval(refracted - spread * (2.0 / 3.0));
    half4 p = content.eval(refracted - spread);
    return half4((r.r + o.r + y.r) / 3.5 + p.r / 7.0,
        o.g / 7.0 + (y.g + g.g + c.g) / 3.5,
        (c.b + b.b + p.b) / 3.0,
        (r.a + o.a + y.a + g.a + c.a + b.a + p.a) / 7.0);
}
"""

internal const val RibbedHighlightShader = """
layout(color) uniform half4 color;
$RibbedFieldShader
half4 main(float2 coord) {
    float2 normal = boundaryNormal((coord + offset) / size);
    float intensity = pow(abs(dot(normal, float2(0.70710678, 0.70710678))), 1.0);
    return color * intensity;
}
"""
