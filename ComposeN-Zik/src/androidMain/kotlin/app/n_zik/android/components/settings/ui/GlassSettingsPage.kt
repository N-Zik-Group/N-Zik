package app.n_zik.android.components.settings.ui

import app.n_zik.android.utils.percentLabel
import kotlin.math.roundToInt

import android.graphics.Color as AndroidColor
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

import app.n_zik.android.components.settings.common.SettingsPageTitle
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DialogColorPicker
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassArtworkColor
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassOptimizationEnabledKey
import app.n_zik.android.components.glass.glassAmountKey
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.components.glass.glassDepthEffectKey
import app.n_zik.android.components.glass.glassDepthKey
import app.n_zik.android.components.glass.glassDialogDimmingKey
import app.n_zik.android.components.glass.glassDispersionKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.glassIndicatorOpacityKey
import app.n_zik.android.components.glass.glassMenuDimmingKey
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.glassRimKey
import app.n_zik.android.components.glass.glassTint
import app.n_zik.android.components.glass.glassTintStrengthKey
import app.n_zik.android.components.glass.glassVibrancyKey
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.components.glass.rememberGlassStore
import app.n_zik.android.components.navigation.nav.GlassNavigationBarHeight
import app.n_zik.android.components.navigation.nav.GlassNavigationPlayerGap
import app.n_zik.android.components.pullrefresh.rememberPullRefreshOverlayInsetProvider
import app.n_zik.android.components.search.SearchHeaderVisibility
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.settings.common.filterSettingsSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.common.LocalSettingsSearchMessage
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsPageDescription
import app.n_zik.android.components.settings.common.SettingsEntryResultRow
import app.n_zik.android.components.settings.common.SettingsScrollToTop
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsSearchContentVisibility
import app.n_zik.android.components.settings.common.SettingsSearchEmptyFeedback
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import app.n_zik.android.components.settings.common.rememberSettingsContentPadding
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference

// Effect controls adapted from Square GlassSettings (Convx Project, GPL-3.0); see NOTICE-square.txt.
/** Dedicated native settings page with a live preview independent of the master switch. */
@Composable
fun GlassSettingsPage(target: String = "", onSearchTarget: ((String) -> Unit)? = null) {
    val host = LocalGlassMenuHost.current
    DisposableEffect(host) {
        val unregister = host?.registerStandaloneSettingsPage()
        onDispose { unregister?.invoke() }
    }
    GlassSettingsContent(pinPreview = true, target = target, searchEnabled = true, onSearchTarget = onSearchTarget)
}

@Composable
internal fun GlassSettingsContent(
    modifier: Modifier = Modifier,
    onboardingFooter: (@Composable () -> Unit)? = null,
    pinPreview: Boolean = false,
    target: String = "",
    searchEnabled: Boolean = false,
    onSearchTarget: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var searchInput by rememberSaveable(target, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var searchFocused by remember { mutableStateOf(false) }
    var requestedTarget by rememberSaveable(target) { mutableStateOf(target) }
    var searchTargetHandled by rememberSaveable(target) { mutableStateOf(false) }
    val query = searchInput.text
    val historyPage = SearchHistoryStore.PAGE_SETTINGS_GLASS
    val history = if (searchEnabled) {
        val historyPaused by rememberPreference(pauseSearchHistoryKey, false)
        rememberSearchHistory(historyPage, historyPaused)
    } else null
    fun recordQuery() {
        history?.value = SearchHistoryStore.record(context.preferences, historyPage, searchInput.text)
    }
    val searching = searchEnabled && query.isNotBlank()
    val searchFocus = rememberSettingsSearchFocus(searchEnabled && searchFocused, query.isBlank())
    fun LazyListScope.settingsGroup(key: String, content: @Composable () -> Unit) {
        item(key, contentType = "group") {
            Column(Modifier.animateItem().settingsSearchContentVisibility(searchFocus.contentAlpha)) { content() }
        }
    }
    val results = if (searching) {
        searchSettingsEntries(context, query).filter { it.cardTitleRes == R.string.glass_effect_title }
    } else emptyList()
    if (searchEnabled) SettingsSearchEmptyFeedback(query, results.isEmpty())

    val registry = remember { SettingsTargetRegistry() }
    val scroll = rememberLazyListState()
    val config = rememberGlassSettings(GlassScope.Shared)
    val hasTint = glassTint(config).isSpecified
    val showMessage = LocalSettingsSearchMessage.current
    val unavailableMessage = stringResource(R.string.settings_target_unavailable)
    LaunchedEffect(requestedTarget) {
        val option = glassSearchOption(requestedTarget)
        if (option == null || searching || searchTargetHandled) return@LaunchedEffect
        val targetId = option.target.id
        val keys = listOf("header") + GlassSearchOptions.map { it.section }.distinct()
        scroll.scrollToItem(0)
        scroll.animateScrollToItem(keys.indexOf(option.section))
        // Expanded cards animate into layout before registering their exact control.
        val found = withTimeoutOrNull(1500) {
            snapshotFlow { registry.targets[targetId] }.first { it != null }
        }
        val resolved = if (found != null) targetId else {
            val requirement = when {
                option.requiredTintSource != null -> {
                    val label = context.getString(R.string.glass_effect_tint_source)
                    val choice = context.getString(option.requiredTintSource.labelRes())
                    "$label › $choice"
                }
                option.fallback == "glass.quality" && config.quality == GlassQuality.Performance &&
                    isGlassSearchLabelSupported(context, option.target.labelRes) -> {
                    val label = context.getString(R.string.glass_effect_rendering_quality)
                    val balanced = context.getString(R.string.glass_effect_quality_balanced)
                    val high = context.getString(R.string.glass_effect_quality_high)
                    "$label › $balanced / $high"
                }
                else -> null
            }
            val message = when {
                requirement != null -> context.getString(R.string.settings_target_prerequisite, requirement)
                targetId == "glass.tintStrength" && !hasTint ->
                    context.getString(R.string.glass_effect_tint_strength_requires_color)
                else -> unavailableMessage
            }
            showMessage(message)
            option.fallback ?: run {
                searchTargetHandled = true
                return@LaunchedEffect
            }
        }
        if (found == null && option.fallback == "glass.quality") scroll.animateScrollToItem(keys.indexOf("material"))
        withFrameNanos { }
        val requester = withTimeoutOrNull(1500) {
            snapshotFlow { registry.targets[resolved] }.first { it != null }
        }
        requester?.bringIntoView()
        searchTargetHandled = true
        if (requester != null) registry.blink(resolved)
    }
    val store = rememberGlassStore()
    val floatingBackdrop = if (searchEnabled) rememberGlassFloatingActionBackdrop() else null
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
    val visibleChromeInset = if (pinPreview && onboardingFooter == null) {
        rememberPullRefreshOverlayInsetProvider()
    } else {
        remember { { 0.dp } }
    }
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
            // Reserve hidden chrome space so the list end does not clamp the scroll position.
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
            Column(Modifier.fillMaxSize().glassFloatingActionContent(floatingBackdrop)) {
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
                        if (searchEnabled) {
                            Column {
                                SearchHeaderVisibility(searchFocus.titleVisible) {
                                    GlassSettingsHeading(config, pinPreview)
                                }
                                UnifiedSearchBar(
                                    input = searchInput,
                                    onInputChanged = {
                                        searchInput = it
                                        if (it.text.isNotBlank()) requestedTarget = ""
                                    },
                                    placeholderRes = R.string.settings_global_search_hint,
                                    modifier = Modifier.padding(searchFocus.barPadding),
                                    baseSurface = colorPalette().background1,
                                    onClear = { searchInput = TextFieldValue("") },
                                    onSearch = ::recordQuery,
                                    onFocused = { searchFocused = it },
                                    textFieldTestTag = "settings.glass.search",
                                )
                                history?.let { state ->
                                    val matches = filterSettingsSearchHistory(state.value, query)
                                    SearchHistorySection(
                                        visible = searchFocused && matches.isNotEmpty(),
                                        queries = matches,
                                        allQueries = state.value,
                                        onQueryClick = {
                                            searchInput = TextFieldValue(it)
                                            requestedTarget = ""
                                            recordQuery()
                                        },
                                        onQueryDelete = { state.value = SearchHistoryStore.delete(context.preferences, historyPage, it) },
                                        onQueryDeleteAll = { state.value = SearchHistoryStore.deleteAll(context.preferences, historyPage) },
                                    )
                                }
                            }
                        } else {
                            GlassSettingsHeading(config, pinPreview)
                        }
                    }
                    if (searching) {
                        items(results, key = { "entry:${it.id}" }, contentType = { "entry" }) { result ->
                            SettingsEntryResultRow(
                                result = result,
                                surfaceTag = "settings.glass.result.entry.${result.id}",
                                rowTag = "settings.glass.entry.${result.id}",
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    recordQuery()
                                    keyboard?.hide()
                                    focusManager.clearFocus(force = true)
                                    if (onSearchTarget != null) {
                                        onSearchTarget(result.id)
                                    } else {
                                        searchInput = TextFieldValue("")
                                        searchTargetHandled = false
                                        requestedTarget = result.id
                                    }
                                },
                            )
                        }
                        return@LazyColumn
                    }
                    settingsGroup("master") { UiGlassCard(showGroupHeading = searchEnabled) }
                    settingsGroup("material") {
                        GlassSection(R.string.glass_effect_group_material, searchEnabled) {
                            OtherSwitchSettingEntry(
                                title = stringResource(R.string.glass_effect_optimization_title),
                                text = stringResource(R.string.glass_effect_optimization_description),
                                isChecked = config.optimizationEnabled, icon = R.drawable.settings,
                                onCheckedChange = { store.setBoolean(glassOptimizationEnabledKey, it) },
                                modifier = Modifier.testTag("settings.glass.optimization").settingsSearchTarget("glass.optimization"),
                            )
                            OtherEnumValueSelectorSettingsEntry(
                                title = stringResource(R.string.glass_effect_rendering_quality),
                                icon = R.drawable.settings,
                                selectedValue = config.quality,
                                onValueSelected = store::setProfile,
                                modifier = Modifier.testTag("settings.glass.quality").settingsSearchTarget("glass.quality"),
                                valueText = { value -> stringResource(when (value) {
                                    GlassQuality.Performance -> R.string.glass_effect_quality_fastest
                                    GlassQuality.Balanced -> R.string.glass_effect_quality_balanced
                                    GlassQuality.Quality -> R.string.glass_effect_quality_high
                                }) },
                            )
                            if (liquid) {
                                GlassSlider("vibrancy", stringResource(R.string.glass_effect_color_vibrancy), config.vibrancy, 0f..2f, percentLabel((config.vibrancy * 50).roundToInt()), R.drawable.eye, inputScale = 50f) { store.setFloat(glassVibrancyKey, it) }
                                GlassSlider("blur", stringResource(R.string.glass_effect_frost), config.blur, 0f..24f, "${config.blur.roundToInt()} dp", R.drawable.drop_blur) { store.setFloat(glassBlurKey, it) }
                            } else {
                                Text(stringResource(if (config.quality == GlassQuality.Performance) R.string.glass_effect_quality_fastest_description else R.string.glass_effect_unsupported), color = ink.copy(alpha = .66f), style = typography().xxs, modifier = Modifier.padding(18.dp))
                            }
                        }
                    }
                    settingsGroup("colour") {
                        GlassSection(R.string.glass_effect_group_color, searchEnabled) {
                            TintRow(config, onTint = store::setTint, onColor = store::setColor, onStrength = { store.setFloat(glassTintStrengthKey, it) })
                            GlassSlider("film", stringResource(R.string.glass_effect_film), config.film, 0f..1f, percentLabel((config.film * 100).roundToInt()), R.drawable.droplet, inputScale = 100f) { store.setFloat(glassFilmKey, it) }
                            GlassSlider("indicator", stringResource(R.string.glass_effect_active_tab_indicator), config.indicatorOpacity, 0f..1f, percentLabel((config.indicatorOpacity * 100).roundToInt()), R.drawable.checkmark, inputScale = 100f) { store.setFloat(glassIndicatorOpacityKey, it) }
                        }
                    }
                    settingsGroup("edges") {
                        GlassSection(R.string.glass_effect_group_edges, searchEnabled) {
                            GlassSlider("rim", stringResource(R.string.glass_effect_rim), config.rim, 0f..1f, percentLabel((config.rim * 100).roundToInt()), R.drawable.sparkles, inputScale = 100f) { store.setFloat(glassRimKey, it) }
                            if (refractive) {
                                GlassSlider("refractionDepth", stringResource(R.string.glass_effect_refraction_depth), config.depth, 0f..1f, "${(config.depth * 48).roundToInt()} dp", R.drawable.sound_effect, inputScale = 48f) { store.setFloat(glassDepthKey, it) }
                                GlassSlider("refractionAmount", stringResource(R.string.glass_effect_refraction_amount), config.amount, 0f..1f, "${(config.amount * 48).roundToInt()} dp", R.drawable.resize, inputScale = 48f) { store.setFloat(glassAmountKey, it) }
                                GlassSwitch("dispersion", R.string.glass_effect_dispersion, config.dispersion, R.drawable.color_palette) { store.setBoolean(glassDispersionKey, it) }
                                GlassSwitch("depth", R.string.glass_effect_depth_enabled, config.depthEffect, R.drawable.sort_grid) { store.setBoolean(glassDepthEffectKey, it) }
                            } else if (liquid) {
                                Text(stringResource(R.string.glass_effect_refraction_unsupported), color = ink.copy(alpha = .66f), style = typography().xxs, modifier = Modifier.padding(18.dp))
                            }
                        }
                    }
                    settingsGroup("readability") {
                        GlassSection(R.string.glass_effect_group_readability, searchEnabled) {
                            GlassSlider("dialogDimming", stringResource(R.string.glass_effect_dialog_dimming), config.dialogDimming, 0f..1f, percentLabel((config.dialogDimming * 100).roundToInt()), R.drawable.eye_off, inputScale = 100f) { store.setFloat(glassDialogDimmingKey, it) }
                            GlassSlider("menuDimming", stringResource(R.string.glass_effect_menu_dimming), config.menuDimming, 0f..1f, percentLabel((config.menuDimming * 100).roundToInt()), R.drawable.menu, inputScale = 100f) { store.setFloat(glassMenuDimmingKey, it) }
                        }
                    }
                    settingsGroup("interactions") { GlassInteractionSettingsCard(config, store, requestedTarget, showGroupHeading = searchEnabled) }
                    GlassDestinationGroup.entries.forEach { group ->
                        settingsGroup("destination.${group.name}") {
                            val pendingTarget = requestedTarget.takeUnless { searchTargetHandled }.orEmpty()
                            GlassDestinationCard(group, config, store, pendingTarget, showGroupHeading = searchEnabled)
                        }
                    }
                    onboardingFooter?.let { footer ->
                        item("onboarding.footer", contentType = "footer") { footer() }
                    }
                }
            }
            if (searchEnabled) {
                GlassFloatingActions(floatingBackdrop) {
                    SettingsScrollToTop(scroll, modifier = Modifier.testTag("settings.glass.scrollToTop"))
                }
            }
        }
    }
}

@Composable
private fun GlassSettingsHeading(config: GlassSettings, pinPreview: Boolean) {
    Column {
        SettingsPageTitle(stringResource(R.string.glass_effect_title), R.drawable.droplet, showBetaBadge = true)
        SettingsPageDescription(stringResource(R.string.glass_effect_summary))
        if (!pinPreview) GlassPreview(config)
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
            val label = stringResource(if (enabled) R.string.common_state_on else R.string.common_state_off)
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
    val accent = glassArtworkColor() ?: colorPalette().accent
    BoxWithConstraints(Modifier.fillMaxWidth().clip(uiRoundnessShape())) {
        val nativeWidth = 360.dp
        val verticalPadding = 12.dp
        val nativeHeight = Dimensions.miniPlayerHeight + GlassNavigationBarHeight +
            GlassNavigationPlayerGap + verticalPadding * 2
        val scale = minOf(maxWidth / nativeWidth, maxPaneHeight / nativeHeight, 1f)
        Box(Modifier.fillMaxWidth().height(nativeHeight * scale), contentAlignment = Alignment.Center) {
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
                // Both bars are measured at their native size before the preview transform.
                Box(Modifier.requiredSize(nativeWidth, nativeHeight).graphicsLayer { scaleX = scale; scaleY = scale }) {
                    GlassRippleProvider(config) {
                        Column(
                            Modifier.fillMaxSize().padding(vertical = verticalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            GlassMiniPlayerPreview(config = config, modifier = Modifier.padding(horizontal = 8.dp))
                            GlassNavigationPreview(
                                config = config,
                                modifier = Modifier.padding(top = GlassNavigationPlayerGap)
                                    .testTag("settings.glass.navigationPreview"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TintRow(config: GlassSettings, onTint: (GlassTintSource) -> Unit, onColor: (Int) -> Unit, onStrength: (Float) -> Unit) {
    val sources = remember { availableGlassTintSources() }
    // Preserve a restored system choice for supported devices while reflecting its untinted fallback here.
    val selectedSource = config.source.takeIf { it in sources } ?: GlassTintSource.None
    val chosen = glassTint(config)
    var showColorPicker by remember(config.source) { mutableStateOf(false) }
    val hsv = FloatArray(3)
    if (chosen.isSpecified) AndroidColor.colorToHSV(chosen.toArgb(), hsv)
    OtherEnumValueSelectorSettingsEntry(
        title = stringResource(R.string.glass_effect_tint_source),
        icon = R.drawable.color_palette,
        selectedValue = selectedSource,
        onValueSelected = onTint,
        values = sources,
        modifier = Modifier.testTag("settings.glass.tintSource").settingsSearchTarget("glass.tintSource"),
        valueText = { value -> stringResource(value.labelRes()) },
    )
    if (selectedSource == GlassTintSource.Custom) {
        OtherSettingsEntry(
            title = stringResource(R.string.glass_effect_custom_color),
            text = "",
            icon = R.drawable.color_palette,
            modifier = Modifier.testTag("settings.glass.tint").settingsSearchTarget("glass.tint"),
            onClick = { showColorPicker = true },
            trailingContent = {
                Box(Modifier.size(24.dp).clip(uiRoundnessShape()).background(chosen))
            },
        )
        DialogPresence(visible = showColorPicker) {
            DialogColorPicker(onDismiss = { showColorPicker = false }, initialColor = chosen) {
                onColor(it.toArgb())
                showColorPicker = false
            }
        }
        SliderSettingsEntry(
            title = stringResource(R.string.glass_effect_tint), text = "", state = hsv[0], range = 0f..360f,
            stepSize = 0f, icon = R.drawable.color_palette, inputValueText = Float::toString,
            toDisplay = { stringResource(R.string.glass_effect_hue, it.roundToInt()) },
            onSlide = { onColor(Color.hsv(it, hsv[1], hsv[2]).toArgb()) },
            modifier = Modifier.testTag("settings.glass.hue").settingsSearchTarget("glass.hue"),
        )
    }
    if (chosen.isSpecified) GlassSlider("tintStrength", stringResource(R.string.glass_effect_tint_strength), config.tintStrength, 0f..1f, percentLabel((config.tintStrength * 100).roundToInt()), R.drawable.drop_half_fill, inputScale = 100f, onChange = onStrength)
}

@Composable
private fun GlassSection(@StringRes title: Int, showGroupHeading: Boolean, content: @Composable () -> Unit) {
    SettingsSectionCard(
        title = stringResource(title),
        groupTitleRes = if (showGroupHeading) GlassSectionGroupTitles[title] else null,
        icon = when (title) {
            R.string.glass_effect_group_material -> R.drawable.settings
            R.string.glass_effect_group_edges -> R.drawable.resize
            R.string.glass_effect_group_readability -> R.drawable.eye_off
            else -> R.drawable.color_palette
        },
        content = content,
    )
}

@Composable
private fun GlassSlider(
    id: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    @DrawableRes icon: Int,
    inputScale: Float = 1f,
    onChange: (Float) -> Unit,
) {
    SliderSettingsEntry(
        title = label, text = "", state = value, range = range, icon = icon,
        stepSize = 0f, toDisplay = { readout }, onSlide = onChange, inputScale = inputScale,
        modifier = Modifier.testTag("settings.glass.slider.$label").settingsSearchTarget("glass.$id"),
    )
}

@Composable
private fun GlassSwitch(id: String, @StringRes label: Int, checked: Boolean, @DrawableRes icon: Int, onChange: (Boolean) -> Unit) {
    OtherSwitchSettingEntry(title = stringResource(label), text = "", isChecked = checked, icon = icon, onCheckedChange = onChange, modifier = Modifier.settingsSearchTarget("glass.$id"))
}
