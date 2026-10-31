package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.rememberSettingsContentPadding

// Effect controls adapted from Square GlassSettings (Convx Project, GPL-3.0); see NOTICE-square.txt.
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.LocalSettingsSearchMessage
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.LocalGlassArtworkColor
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassAmountKey
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.components.glass.glassDepthEffectKey
import app.n_zik.android.components.glass.glassDepthKey
import app.n_zik.android.components.glass.glassDispersionKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassIndicatorOpacityKey
import app.n_zik.android.components.glass.glassDialogDimmingKey
import app.n_zik.android.components.glass.glassMenuDimmingKey
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.glassRimKey
import app.n_zik.android.components.glass.glassTint
import app.n_zik.android.components.glass.glassTintStrengthKey
import app.n_zik.android.components.glass.glassVibrancyKey
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.rememberGlassStore
import app.n_zik.android.components.dialog.common.DialogColorPicker
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import app.n_zik.android.components.navigation.nav.GlassNavigationPlayerGap
import app.n_zik.android.components.pullrefresh.rememberPullRefreshOverlayInsetProvider
import kotlin.math.roundToInt

/** Dedicated native settings page with a live preview independent of the master switch. */
@Composable
fun GlassSettingsPage(target: String = "") {
    val host = LocalGlassMenuHost.current
    DisposableEffect(host) {
        host?.standaloneSettings = true
        onDispose { host?.standaloneSettings = false }
    }
    GlassSettingsContent(pinPreview = true, target = target)
}

@Composable
internal fun GlassSettingsContent(
    modifier: Modifier = Modifier,
    onboardingFooter: (@Composable () -> Unit)? = null,
    pinPreview: Boolean = false,
    target: String = "",
) {
    val registry = remember { SettingsTargetRegistry() }
    val scroll = rememberLazyListState()
    val showMessage = LocalSettingsSearchMessage.current
    val unavailableMessage = stringResource(R.string.settings_target_unavailable)
    val option = glassSearchOption(target)
    LaunchedEffect(target) {
        if (option == null) return@LaunchedEffect
        val keys = listOf("header", "master", "interactions", "material", "colour", "readability", "edges") +
            GlassDestinationGroup.entries.map { "destination.${it.name}" }
        scroll.scrollToItem(keys.indexOf(option.section))
        // Expanded cards animate into layout before registering their exact control.
        val found = withTimeoutOrNull(1500) {
            snapshotFlow { registry.targets[target] }.first { it != null }
        }
        val resolved = if (found != null) target else {
            showMessage(unavailableMessage)
            option.fallback ?: return@LaunchedEffect
        }
        if (found == null && option.fallback == "glass.quality") scroll.scrollToItem(keys.indexOf("material"))
        withFrameNanos { }
        val requester = withTimeoutOrNull(1500) {
            snapshotFlow { registry.targets[resolved] }.first { it != null }
        }
        requester?.let {
            requester.bringIntoView()
            registry.blink(resolved)
        }
    }
    val store = rememberGlassStore()
    val config = rememberGlassSettings(app.n_zik.android.components.glass.GlassScope.Shared)
    val preview = config.copy(enabled = true)
    val renderer = glassRenderer(preview)
    val liquid = renderer != GlassRenderer.Tint
    val refractive = renderer == GlassRenderer.Refraction
    val ink = colorPalette().text
    val pagePadding = if (onboardingFooter != null) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        else rememberSettingsContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer + 24.dp))
    val previewPadding = remember(pagePadding) {
        object : PaddingValues by pagePadding {
            override fun calculateTopPadding() = 0.dp
            override fun calculateBottomPadding() = 0.dp
        }
    }
    // A pinned surface follows visible chrome, whereas scrolling content keeps its initial reserve.
    val visibleChromeInset = if (pinPreview) rememberPullRefreshOverlayInsetProvider() else remember { { 0.dp } }
    val topPadding = remember(visibleChromeInset, pinPreview) {
        object : PaddingValues {
            override fun calculateTopPadding() = if (pinPreview) visibleChromeInset() else 0.dp
            override fun calculateBottomPadding() = 0.dp
            override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
            override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
        }
    }

    CompositionLocalProvider(LocalSettingsTargets provides registry) {
        BoxWithConstraints(modifier.fillMaxSize().background(colorPalette().background0).testTag("settings.glass").padding(topPadding)) {
            // Keep both panes readable while leaving room for controls in short landscape windows.
            val previewHeight = (maxHeight * .28f).coerceIn(24.dp, 166.dp)
            // Hiding chrome increases the list viewport. At its end LazyColumn would otherwise
            // clamp the scroll position backwards, moving the user's last visible controls.
            // Extend the tail by that exact gain; the adaptive preview consumes part of it
            // in short landscape windows. Both the item anchor and offset then stay stable.
            val hiddenChrome = if (pinPreview)
                (pagePadding.calculateTopPadding() - visibleChromeInset()).coerceAtLeast(0.dp) else 0.dp
            val expandedPreviewHeight = ((maxHeight - hiddenChrome).coerceAtLeast(0.dp) * .28f)
                .coerceIn(24.dp, 166.dp)
            val scrollTailReserve = (hiddenChrome - (previewHeight - expandedPreviewHeight)).coerceAtLeast(0.dp)
            val controlsPadding = remember(pagePadding, pinPreview, scrollTailReserve) {
                object : PaddingValues by pagePadding {
                    override fun calculateTopPadding() = if (pinPreview) 0.dp else pagePadding.calculateTopPadding()
                    override fun calculateBottomPadding() = pagePadding.calculateBottomPadding() + scrollTailReserve
                }
            }
            Column(Modifier.fillMaxSize()) {
                if (pinPreview) {
                    Box(Modifier.padding(previewPadding)) {
                        GlassPreview(config, maxPaneHeight = previewHeight)
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).testTag("settings.glass.controls"),
                    contentPadding = controlsPadding,
                    state = scroll,
                ) {
                    item("header", contentType = "header") {
                        Column {
                            HeaderWithIcon(
                                title = stringResource(R.string.liquid_glass),
                                iconId = R.drawable.color_palette,
                                enabled = false,
                                showIcon = true,
                                modifier = Modifier,
                                onClick = {},
                            )
                            app.n_zik.android.components.settings.common.SettingsDescription(
                                text = stringResource(R.string.page_glass_summary),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            if (!pinPreview) GlassPreview(config)
                        }
                    }
                    item("master", contentType = "group") { UiGlassCard() }
                    item("interactions", contentType = "group") { GlassInteractionSettingsCard(config, store, target) }
                    item("material", contentType = "group") {
                        GlassSection(R.string.glass_group_material) {
                            OtherEnumValueSelectorSettingsEntry(
                                title = stringResource(R.string.quality),
                                icon = R.drawable.settings,
                                selectedValue = config.quality,
                                onValueSelected = store::setProfile,
                                modifier = Modifier.testTag("settings.glass.quality").settingsSearchTarget("glass.quality"),
                                valueText = { value -> stringResource(when (value) {
                                    GlassQuality.Performance -> R.string.glass_performance
                                    GlassQuality.Balanced -> R.string.glass_balanced
                                    GlassQuality.Quality -> R.string.glass_quality
                                }) },
                            )
                            if (liquid) {
                                GlassSlider("vibrancy", stringResource(R.string.glass_vibrancy), config.vibrancy, 0f..2f, "${(config.vibrancy * 50).roundToInt()}%", R.drawable.eye) { store.setFloat(glassVibrancyKey, it) }
                                GlassSlider("blur", stringResource(R.string.glass_blur), config.blur, 0f..24f, "${config.blur.roundToInt()} dp", R.drawable.drop_blur) { store.setFloat(glassBlurKey, it) }
                            } else {
                                Text(stringResource(if (config.quality == GlassQuality.Performance) R.string.glass_performance_note else R.string.glass_unsupported), color = ink.copy(alpha = .66f), style = typography().xxs, modifier = Modifier.padding(18.dp))
                            }
                        }
                    }
                    item("colour", contentType = "group") {
                        GlassSection(R.string.glass_group_colour) {
                            TintRow(config, onTint = store::setTint, onColor = store::setColor, onStrength = { store.setFloat(glassTintStrengthKey, it) })
                            GlassSlider("film", stringResource(R.string.glass_film), config.film, 0f..1f, "${(config.film * 100).roundToInt()}%", R.drawable.droplet) { store.setFloat(glassFilmKey, it) }
                            GlassSlider("indicator", stringResource(R.string.glass_indicator), config.indicatorOpacity, 0f..1f, "${(config.indicatorOpacity * 100).roundToInt()}%", R.drawable.checkmark) { store.setFloat(glassIndicatorOpacityKey, it) }
                        }
                    }
                    item("readability", contentType = "group") {
                        GlassSection(R.string.glass_group_readability) {
                            GlassSlider("dialogDimming", stringResource(R.string.glass_dialog_dimming), config.dialogDimming, 0f..1f, "${(config.dialogDimming * 100).roundToInt()}%", R.drawable.eye_off) { store.setFloat(glassDialogDimmingKey, it) }
                            GlassSlider("menuDimming", stringResource(R.string.glass_menu_dimming), config.menuDimming, 0f..1f, "${(config.menuDimming * 100).roundToInt()}%", R.drawable.menu) { store.setFloat(glassMenuDimmingKey, it) }
                        }
                    }
                    item("edges", contentType = "group") {
                        GlassSection(R.string.glass_group_edges) {
                            GlassSlider("rim", stringResource(R.string.glass_rim), config.rim, 0f..1f, "${(config.rim * 100).roundToInt()}%", R.drawable.sparkles) { store.setFloat(glassRimKey, it) }
                            if (refractive) {
                                GlassSlider("refractionDepth", stringResource(R.string.glass_refraction_depth), config.depth, 0f..1f, "${(config.depth * 48).roundToInt()} dp", R.drawable.sound_effect) { store.setFloat(glassDepthKey, it) }
                                GlassSlider("refractionAmount", stringResource(R.string.glass_refraction_amount), config.amount, 0f..1f, "${(config.amount * 48).roundToInt()} dp", R.drawable.resize) { store.setFloat(glassAmountKey, it) }
                                GlassSwitch("dispersion", R.string.glass_dispersion, config.dispersion, R.drawable.color_palette) { store.setBoolean(glassDispersionKey, it) }
                                GlassSwitch("depth", R.string.glass_depth, config.depthEffect, R.drawable.sort_grid) { store.setBoolean(glassDepthEffectKey, it) }
                            } else if (liquid) {
                                Text(stringResource(R.string.glass_refraction_unsupported), color = ink.copy(alpha = .66f), style = typography().xxs, modifier = Modifier.padding(18.dp))
                            }
                        }
                    }
                    GlassDestinationGroup.entries.forEach { group ->
                        item("destination.${group.name}", contentType = "group") {
                            GlassDestinationCard(group, config, store, target)
                        }
                    }
                    onboardingFooter?.let { footer ->
                        item("onboarding.footer", contentType = "footer") { footer() }
                    }
                }
            }
        }
    }
}

/** Simultaneous comparison, using native components with only the local master value changed. */
@Composable
internal fun GlassPreview(config: GlassSettings, maxPaneHeight: Dp = 166.dp) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag("settings.glass.preview"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(true, false).forEach { enabled ->
            val label = stringResource(if (enabled) R.string.on else R.string.off)
            Column(
                Modifier.weight(1f).testTag("settings.glass.preview.$enabled")
                    .semantics { stateDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = colorPalette().text, style = typography().xs)
                GlassPreviewPane(config.copy(enabled = enabled), maxPaneHeight)
            }
        }
    }
}

@Composable
private fun GlassPreviewPane(config: GlassSettings, maxPaneHeight: Dp) {
    // Only the stage is recorded. Sampling its foreground would create a RenderNode cycle.
    val stage = rememberLayerBackdrop()
    val accent = LocalGlassArtworkColor.current ?: LocalGlassMenuHost.current?.artworkColor ?: colorPalette().accent
    BoxWithConstraints(Modifier.fillMaxWidth().clip(uiRoundnessShape())) {
        val nativeWidth = 360.dp
        val scale = minOf(maxWidth / nativeWidth, maxPaneHeight / 166.dp, 1f)
        Box(Modifier.fillMaxWidth().height(166.dp * scale), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize()
                .then(if (glassRenderer(config) != GlassRenderer.Tint) Modifier.layerBackdrop(stage) else Modifier)
                .background(Brush.linearGradient(listOf(accent, Color(0xFFFF7A45), Color(0xFF2ED3C6), Color(0xFF1B1B1B))))) {
                Column(Modifier.matchParentSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    repeat(6) { index ->
                        Box(Modifier.fillMaxWidth(if (index % 2 == 0) .94f else .7f).height(5.dp)
                            .background(if (index % 2 == 0) Color.White.copy(alpha = .85f) else Color.Black.copy(alpha = .5f)))
                    }
                }
            }
            CompositionLocalProvider(LocalGlassBackdrop provides stage, LocalGlassPreviewSettings provides config) {
                Column(
                    Modifier.requiredWidth(nativeWidth).height(166.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GlassMiniPlayerPreview(config = config, modifier = Modifier.padding(horizontal = 8.dp))
                    GlassNavigationPreview(config = config, modifier = Modifier.padding(top = GlassNavigationPlayerGap))
                }
            }
        }
    }
}

@Composable
private fun TintRow(config: GlassSettings, onTint: (GlassTintSource) -> Unit, onColor: (Int) -> Unit, onStrength: (Float) -> Unit) {
    val chosen = glassTint(config)
    var showColorPicker by remember(config.source) { mutableStateOf(false) }
    val hsv = FloatArray(3)
    if (chosen.isSpecified) android.graphics.Color.colorToHSV(chosen.toArgb(), hsv)
    OtherEnumValueSelectorSettingsEntry(
        title = stringResource(R.string.liquid_glass_tint_source),
        icon = R.drawable.color_palette,
        selectedValue = config.source,
        onValueSelected = onTint,
        modifier = Modifier.testTag("settings.glass.tintSource").settingsSearchTarget("glass.tintSource"),
        valueText = { value -> stringResource(when (value) {
            GlassTintSource.None -> R.string.glass_tint_none
            GlassTintSource.ThemeAccent -> R.string.liquid_glass_theme_accent
            GlassTintSource.Artwork -> R.string.liquid_glass_artwork
            GlassTintSource.SystemAccent -> R.string.liquid_glass_system_accent
            GlassTintSource.Custom -> R.string.liquid_glass_custom
        }) },
    )
    if (config.source == GlassTintSource.Custom) {
        OtherSettingsEntry(
            title = stringResource(R.string.glass_tint),
            text = stringResource(R.string.liquid_glass_color),
            icon = R.drawable.color_palette,
            modifier = Modifier.testTag("settings.glass.tint").settingsSearchTarget("glass.tint"),
            onClick = { showColorPicker = true },
            trailingContent = {
                Box(Modifier.size(24.dp).clip(uiRoundnessShape()).background(chosen))
            },
        )
        DialogPresence(visible = showColorPicker) {
            DialogColorPicker(onDismiss = { showColorPicker = false }) {
                onColor(it.toArgb())
                showColorPicker = false
            }
        }
        SliderSettingsEntry(
            title = stringResource(R.string.glass_tint), text = "", state = hsv[0], range = 0f..360f,
            stepSize = 0f, icon = R.drawable.color_palette, inputValueText = Float::toString,
            toDisplay = { stringResource(R.string.glass_hue, it.roundToInt()) },
            onSlide = { onColor(Color.hsv(it, hsv[1], hsv[2]).toArgb()) },
            modifier = Modifier.testTag("settings.glass.hue").settingsSearchTarget("glass.hue"),
        )
    }
    if (chosen.isSpecified) GlassSlider("tintStrength", stringResource(R.string.glass_tint_strength), config.tintStrength, 0f..1f, "${(config.tintStrength * 100).roundToInt()}%", R.drawable.drop_half_fill, onChange = onStrength)
}

@Composable
private fun GlassSection(@StringRes title: Int, content: @Composable () -> Unit) {
    SettingsSectionCard(
        title = stringResource(title),
        icon = when (title) {
            R.string.glass_group_material -> R.drawable.settings
            R.string.glass_group_edges -> R.drawable.resize
            R.string.glass_group_readability -> R.drawable.eye_off
            else -> R.drawable.color_palette
        },
        content = content,
    )
}

@Composable
private fun GlassSlider(id: String, label: String, value: Float, range: ClosedFloatingPointRange<Float>, readout: String, @DrawableRes icon: Int, onChange: (Float) -> Unit) {
    SliderSettingsEntry(
        title = label, text = "", state = value, range = range, icon = icon,
        stepSize = 0f, toDisplay = { readout }, onSlide = onChange, inputValueText = Float::toString,
        modifier = Modifier.testTag("settings.glass.slider.$label").settingsSearchTarget("glass.$id"),
    )
}

@Composable
private fun GlassSwitch(id: String, @StringRes label: Int, checked: Boolean, @DrawableRes icon: Int, onChange: (Boolean) -> Unit) {
    OtherSwitchSettingEntry(title = stringResource(label), text = "", isChecked = checked, icon = icon, onCheckedChange = onChange, modifier = Modifier.settingsSearchTarget("glass.$id"))
}
