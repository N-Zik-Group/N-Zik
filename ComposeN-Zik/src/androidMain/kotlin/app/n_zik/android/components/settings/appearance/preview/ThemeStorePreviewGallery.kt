package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.settings.SettingsDialogTabRow
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.typography
import app.n_zik.android.ui.components.forceLoadingShimmerPreview

@Composable
internal fun ThemeGalleryPlaceholder() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = (maxWidth - 16.dp) / 3
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(3) { ThemeGalleryCellPlaceholder(Modifier.width(width), null) }
        }
    }
}

@Composable
private fun ThemeGalleryCellPlaceholder(modifier: Modifier, caption: String?) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemeCardSurface(Modifier.fillMaxWidth().aspectRatio(StorePreviewAspectRatio)) {
            StoreLoadingShape(Modifier.fillMaxSize())
        }
        StoreLoadingLine(caption, typography().xs)
    }
}

@Composable
internal fun rememberGalleryVariantSelection(themeId: String, revision: Int, currentVariant: Boolean): MutableState<Boolean> {
    val saver = listSaver<MutableState<Boolean>, Any>(
        save = { listOf(themeId, revision, currentVariant, it.value) },
        restore = { saved ->
            val matches = saved[0] == themeId && saved[1] == revision && saved[2] == currentVariant
            mutableStateOf(if (matches) saved[3] as Boolean else currentVariant)
        },
    )
    return rememberSaveable(themeId, revision, currentVariant, saver = saver) { mutableStateOf(currentVariant) }
}

/** Matches the same scene across palette and Glass variants, including catalogs with duplicate scenes. */
internal fun galleryPreviewSceneKeys(previews: List<StorePreview>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return previews.map { preview ->
        val scene = preview.path.substringBeforeLast('.')
            .removeSuffix("-glass").removeSuffix("-normal")
            .removeSuffix("-dark").removeSuffix("-light")
        val occurrence = occurrences.getOrDefault(scene, 0)
        occurrences[scene] = occurrence + 1
        "$scene:$occurrence"
    }
}

/** Keep the viewer's existing pages and replace variants only for matching scenes. */
internal fun updateGalleryViewerScenes(
    displayed: List<Pair<StorePreview, String>>,
    available: List<Pair<StorePreview, String>>,
): List<Pair<StorePreview, String>> {
    val previewsByScene = available.associate { (preview, scene) -> scene to preview }
    return displayed.map { (preview, scene) -> (previewsByScene[scene] ?: preview) to scene }
}

private data class OpenThemeGallery(val scene: String, val previews: List<Pair<StorePreview, String>>)

@Composable
internal fun ThemePreviewGallery(theme: StoreTheme, catalog: ThemeStoreCatalog, retry: Int) {
    if (theme.previews.isEmpty()) return
    val currentGlass = rememberGlassSettings().enabled
    val variants = listOf(false, true).filter { glass -> theme.previews.any { it.glassVariant == glass } }
    var selectedGlass by rememberGalleryVariantSelection(theme.themeId, theme.revision, currentGlass)
    val variant = selectedGlass.takeIf { it in variants } ?: variants.firstOrNull()
    val currentDark = colorPalette().isDark
    var selectedDark by rememberGalleryVariantSelection(theme.themeId, theme.revision, currentDark)
    val hasPaletteVariants = theme.previews.any { it.darkPreview == true } &&
        theme.previews.any { it.darkPreview == false }
    val previews = remember(theme, variant, selectedDark, hasPaletteVariants) {
        theme.galleryPreviews(variant).filter {
            !hasPaletteVariants || it.darkPreview == null || it.darkPreview == selectedDark
        }
    }
    val scenes = remember(previews) { previews.zip(galleryPreviewSceneKeys(previews)) }
    var openGallery by remember(theme.themeId, theme.revision) { mutableStateOf<OpenThemeGallery?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (variants.size > 1) SettingsDialogTabRow(
            labels = listOf(stringResource(R.string.theme_store_preview_normal), stringResource(R.string.theme_store_preview_glass)),
            selectedTabIndex = if (variant == true) 1 else 0,
            equalWidthWhenFitting = true,
            followNestedTarget = false,
        ) { selectedGlass = it == 1 }
        if (hasPaletteVariants) SettingsDialogTabRow(
            labels = listOf(
                stringResource(R.string.settings_theme_light_mode_label),
                stringResource(R.string.settings_theme_dark_mode_label),
            ),
            selectedTabIndex = if (selectedDark) 1 else 0,
            equalWidthWhenFitting = true,
            followNestedTarget = false,
        ) { selectedDark = it == 1 }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = (maxWidth - 16.dp) / 3
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scenes, key = { it.second }, contentType = { "themePreview" }) { (preview, scene) ->
                    ThemeGalleryPreview(theme, preview, catalog, retry, Modifier.width(width)) {
                        openGallery = OpenThemeGallery(scene, scenes)
                    }
                }
            }
        }
    }
    openGallery?.let { gallery ->
        val viewerScenes = remember(gallery.previews, scenes) { updateGalleryViewerScenes(gallery.previews, scenes) }
        SideEffect {
            if (openGallery === gallery && viewerScenes != gallery.previews) {
                openGallery = gallery.copy(previews = viewerScenes)
            }
        }
        ThemePreviewGalleryDialog(
            previews = viewerScenes,
            initialPage = viewerScenes.indexOfFirst { it.second == gallery.scene },
            onDismiss = { openGallery = null },
            itemKey = { it.second },
            caption = { galleryPreviewLabel(it.first) },
        ) { (preview, _) ->
            if (forceLoadingShimmerPreview()) StoreLoadingShape(Modifier.fillMaxSize())
            else {
                val file = rememberStorePreview(theme, preview, catalog, retry)?.file
                FadingStorePreviewImage(file, galleryPreviewLabel(preview))
            }
        }
    }
}

@Composable
private fun ThemeGalleryPreview(
    theme: StoreTheme,
    preview: StorePreview,
    catalog: ThemeStoreCatalog,
    retry: Int,
    modifier: Modifier,
    onExpand: () -> Unit,
) {
    if (forceLoadingShimmerPreview()) {
        ThemeGalleryCellPlaceholder(modifier, galleryPreviewLabel(preview))
        return
    }
    val asset = rememberStorePreview(theme, preview, catalog, retry)
    val label = galleryPreviewLabel(preview)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemeCardSurface(
            Modifier.fillMaxWidth().aspectRatio(StorePreviewAspectRatio),
            onClick = onExpand.takeIf { asset != null },
        ) {
            Box(Modifier.fillMaxSize()) {
                FadingStorePreviewImage(asset?.file, label)
            }
        }
        BasicText(label, Modifier.basicMarquee(iterations = Int.MAX_VALUE), style = typography().xs.copy(color = colorPalette().text, textAlign = TextAlign.Center), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun galleryPreviewLabel(preview: StorePreview): String = preview.caption ?: when (preview.role) {
    "player" -> stringResource(R.string.theme_store_preview_player)
    "queue" -> stringResource(R.string.theme_store_preview_queue)
    "navigation" -> stringResource(R.string.theme_store_group_navigation)
    else -> submissionPreviewLabel(preview.path.substringAfterLast('/').substringBeforeLast('.'))
}
