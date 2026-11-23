package app.n_zik.android.components.settings.appearance.preview

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.LiquidButton
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassSettingsCardRim
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.components.ui.DownloadIconButton
import app.n_zik.android.components.ui.UiTextButton
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.shimmerEffect

@Composable
internal fun ThemeStoreCard(
    theme: StoreTheme,
    catalog: ThemeStoreCatalog,
    glass: Boolean,
    official: Boolean,
    suspended: Boolean = false,
    showLocalCreator: Boolean = false,
    modifier: Modifier = Modifier,
    showCatalogInfo: Boolean = false,
    refreshVersion: Int = 0,
    download: ThemeCardDownload? = null,
    onWithdraw: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    val shimmer = forceLoadingShimmerPreview()
    val blocked = suspended && !official
    val offline by OfflineMode.rememberEnabled()
    var followUp by rememberSaveable(theme.themeId) { mutableStateOf(false) }
    if (blocked && followUp && !offline) {
        ThemeReportDialog(theme, null) { followUp = false }
    }
    val publishedPreview = if (blocked) null else theme.playerPreview(glass, colorPalette().isDark)
    val open = onOpen.takeUnless { blocked }
    val preview = if (shimmer) null else rememberStorePreview(theme, publishedPreview, catalog, refreshVersion)
    val context = LocalContext.current
    var sizeLoading by remember(catalog, theme, showCatalogInfo, refreshVersion) { mutableStateOf(showCatalogInfo) }
    val appearanceSize by produceState<Int?>(null, catalog, theme, showCatalogInfo, refreshVersion) {
        try {
            value = if (showCatalogInfo) catalog.appearanceSize(theme) else null
        } finally {
            sizeLoading = false
        }
    }
    val reviewRead = rememberStoreReviews(catalog, theme, showCatalogInfo && !offline, refreshVersion)
    ThemeCardSurface(modifier = modifier, onClick = open) {
        Box(Modifier.fillMaxWidth().aspectRatio(StorePreviewAspectRatio), contentAlignment = Alignment.Center) {
            if (shimmer && !blocked) {
                Spacer(Modifier.matchParentSize().shimmerEffect())
            } else if (publishedPreview != null) {
                FadingStorePreviewImage(preview?.file, publishedPreview.caption)
            } else {
                StorePreviewImage(null, null, showImageRim = false, fitPortraitFrame = true)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (open == null) 10.dp else 0.dp)) {
            val author = if (showLocalCreator) localThemeCreatorName(theme) else theme.author.login
            ThemeCardIdentity(theme.title, author, official, shimmer)
            if (blocked) {
                StoreText(stringResource(R.string.theme_store_report_theme_suspended), secondary = true)
            } else if (showCatalogInfo) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f)) {
                        val review = reviewRead.summary
                        if (reviewRead.initialPending) {
                            StoreLoadingLine(null, typography().xxs, width = 0.5f)
                        } else if (review != null) {
                            val label = if (review.count == 0) stringResource(R.string.theme_store_no_reviews)
                            else stringResource(R.string.theme_store_rating_count, review.average, review.count)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (review.count > 0) Icon(painterResource(R.drawable.star_brilliant), null, Modifier.size(12.dp), tint = colorPalette().accent)
                                if (shimmer) StoreLoadingLine(label, typography().xxs)
                                else StoreText(label, secondary = true, style = typography().xxs)
                            }
                        } else if (!offline) {
                            val label = stringResource(R.string.theme_store_reviews_unavailable)
                            if (shimmer) StoreLoadingLine(label, typography().xxs)
                            else StoreText(label, secondary = true, style = typography().xxs)
                        }
                    }
                    if (sizeLoading && appearanceSize == null) Box(Modifier.width(32.dp)) {
                        StoreLoadingLine(null, typography().xxs, width = 1f)
                    } else appearanceSize?.let {
                        val label = Formatter.formatShortFileSize(context, it.toLong())
                        if (shimmer) StoreLoadingLine(label, typography().xxs)
                        else StoreText(label, secondary = true, style = typography().xxs)
                    }
                }
            }
        }
        if (!blocked && download != null) ThemeCardDownloadRow(download, shimmer)
        if (blocked && !offline) {
            Box(Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 10.dp), contentAlignment = Alignment.CenterEnd) {
                StoreSeeButton(R.string.theme_store_report_follow_up, onOpen = { followUp = true })
            }
        } else if (open != null) {
            StoreCardSeeAction(open, Modifier.padding(end = 4.dp, bottom = 10.dp), onWithdraw.takeUnless { offline })
        }
    }
}

@Immutable
internal data class ThemeCardDownload(val downloaded: Boolean, val pending: Boolean, val onDownload: (() -> Unit)?)

@Composable
private fun ThemeCardDownloadRow(download: ThemeCardDownload, shimmer: Boolean) {
    val (label, icon) = when {
        download.downloaded -> R.string.theme_store_download_saved to R.drawable.downloaded
        download.pending -> R.string.theme_store_downloading to R.drawable.download_progress
        else -> R.string.theme_store_not_downloaded to R.drawable.download
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            val status = stringResource(label)
            if (shimmer) StoreLoadingLine(status, typography().xxs)
            else StoreText(status, secondary = true, style = typography().xxs)
        }
        val onDownload = download.onDownload
        if (!download.downloaded && !download.pending && onDownload != null) {
            DownloadIconButton(
                onClick = onDownload,
                icon = icon,
                color = colorPalette().text,
                modifier = Modifier.size(20.dp),
                contentDescription = stringResource(R.string.theme_store_download),
            )
        } else {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = colorPalette().textSecondary)
        }
    }
}

@Composable
internal fun ThemeCardIdentity(title: String, author: String, official: Boolean, shimmer: Boolean = false) {
    val titleStyle = typography().xs.copy(fontWeight = FontWeight.SemiBold)
    if (shimmer) StoreLoadingLine(title, titleStyle)
    else StoreText(title, style = titleStyle, marquee = true)
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            if (shimmer) StoreLoadingLine(author, typography().xxs)
            else StoreText(author, secondary = true, style = typography().xxs, marquee = true)
        }
        OfficialCreatorBadge(official)
    }
}

@Composable
private fun StoreCardSeeAction(onOpen: () -> Unit, modifier: Modifier = Modifier, onWithdraw: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(modifier).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onWithdraw != null) {
            StoreIconAction(onClick = onWithdraw, size = 32.dp) {
                Icon(painterResource(R.drawable.trash), stringResource(R.string.theme_store_withdraw),
                    Modifier.size(16.dp), tint = colorPalette().text)
            }
        }
        StoreSeeButton(R.string.theme_store_see, onOpen)
    }
}

@Composable
internal fun StoreSeeButton(label: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val shape = uiRoundnessShape()
    val settings = rememberGlassSettings()
    val height = 32.dp
    val content: @Composable () -> Unit = {
        BasicText(stringResource(label), style = typography().xxs.copy(color = colorPalette().text))
        Icon(painterResource(R.drawable.chevron_forward), null, Modifier.size(12.dp), tint = colorPalette().text)
    }
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        val background = colorPalette().background0
        LiquidButton(
            onClick = onOpen,
            backdrop = rememberCanvasBackdrop { drawRect(background) },
            modifier = modifier,
            settings = settings.forArea(GlassArea.ActionButtons),
            contentHeight = height,
            contentPadding = 6.dp,
            filmColor = surfaceColor(colorPalette().background1),
            shape = shape,
        ) { content() }
    } else {
        UiTextButton(
            onClick = onOpen,
            modifier = modifier.height(height),
            shape = shape,
            colors = storeGlassButtonColors(),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) { content() }
    }
}

@Composable
private fun storeGlassButtonColors() = ButtonDefaults.textButtonColors(
    containerColor = if (rememberGlassSettings().let {
        it.usesGlassMaterial(GlassArea.ActionButtons) || it.usesGlassContour(GlassArea.ActionButtons)
    }) surfaceColor(colorPalette().background1) else Color.Transparent,
)

@Composable
internal fun ThemeCardSurface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val palette = colorPalette()
    val shape = uiRoundnessShape()
    SettingsCardSurface(
        modifier = modifier.fillMaxWidth().glassSettingsCardRim(shape),
        shape = shape,
        containerColor = surfaceColor(palette.background1),
        backgroundColor = palette.background0,
        shadowColor = palette.background0,
        foregroundRim = true,
    ) {
        Column(Modifier.fillMaxWidth().clip(shape).then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
        )) { content() }
    }
}

@Composable
internal fun StoreIconAction(
    available: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    border: BorderStroke = BorderStroke(1.dp, colorPalette().accent),
    glyph: @Composable () -> Unit,
) {
    val settings = rememberGlassSettings()
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        val background = colorPalette().background0
        LiquidButton(
            onClick = onClick,
            backdrop = rememberCanvasBackdrop { drawRect(background) },
            modifier = modifier.size(size),
            settings = settings.forArea(GlassArea.ActionButtons),
            enabled = available,
            contentHeight = size,
            contentPadding = 0.dp,
            filmColor = surfaceColor(colorPalette().background1),
        ) { glyph() }
        return
    }
    UiTextButton(
        onClick = onClick,
        enabled = available,
        modifier = modifier.size(size),
        colors = storeGlassButtonColors(),
        border = border,
        contentPadding = PaddingValues(0.dp),
    ) { glyph() }
}
