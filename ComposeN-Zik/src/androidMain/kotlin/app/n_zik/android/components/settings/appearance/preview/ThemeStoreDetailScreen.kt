package app.n_zik.android.components.settings.appearance.preview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import app.n_zik.android.components.glass.GlassTextField as TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.utils.shimmerEffect
import app.n_zik.android.components.ui.NzikCheckbox
import app.n_zik.android.colorPalette
import app.n_zik.android.components.AppPullToRefreshBox
import app.n_zik.android.components.artist.ArtistFollowButton
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogButtonPlacement
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.glass.PageIconButton
import app.n_zik.android.components.settings.accounts.GitHubAccount
import app.n_zik.android.components.settings.accounts.rememberGitHubPublicationAuthorization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.components.dialog.settings.SettingsDialogTabRow
import app.n_zik.android.components.ui.screens.home.GlassHomeChips
import app.n_zik.android.components.settings.common.OtherInfoSettingsEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.rememberGlassStore
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.ui.UiButton
import app.n_zik.android.components.ui.UiTextButton
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.navigation.currentPageNavigationEntry
import app.n_zik.android.core.navigation.navigationChromeTween
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.typography
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
internal fun ThemeStoreDetailScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit,
    themeId: String,
    revision: Int,
) {
    val context = LocalContext.current
    val offline by OfflineMode.rememberEnabled()
    val profileId = rememberGlassStore().profileId
    val preferences = remember(context, profileId) { context.preferences }
    val library = remember(preferences) { ThemeStoreLibrary(preferences) }
    val downloads by key(library) {
        remember(library) { library.observeDownloads() }
            .collectAsStateWithLifecycle(initialValue = library.downloads())
    }
    val initialDownload = remember(library, themeId, revision) {
        downloads.firstOrNull { it.id == "$themeId:$revision" }
    }
    var retry by remember { mutableIntStateOf(0) }
    val builtIn = rememberBundledDefaultTheme(retry, enabled = themeId == BundledThemeId)
    val localPackage = builtIn != null || initialDownload?.imported == true
    val removedLocal = initialDownload?.takeIf { saved -> saved.imported && downloads.none { it.id == saved.id } }
    val replacement = removedLocal?.restoredStoreDownload(downloads)
    val detailEntry = currentPageNavigationEntry()
        ?: remember(navController, themeId, revision) { navController.currentBackStackEntry }
    val currentEntry by navController.currentBackStackEntryAsState()
    fun leaveDetail() {
        if (navController.currentBackStackEntry !== detailEntry) return
        val previous = navController.previousBackStackEntry?.destination?.route
        if (navController.popBackStack() && previous?.startsWith(NavRoutes.themeStore.name) == true) return
        navController.navigate(NavRoutes.themeStore.name) { launchSingleTop = true }
    }
    LaunchedEffect(removedLocal, replacement, currentEntry) {
        if (removedLocal == null || detailEntry == null || currentEntry !== detailEntry) return@LaunchedEffect
        if (replacement != null) {
            val restored = checkNotNull(replacement.theme)
            navController.navigate("${NavRoutes.themeStoreDetail.name}/${restored.themeId}/${restored.revision}") {
                popUpTo(detailEntry.destination.id) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            leaveDetail()
        }
    }
    val catalog = remember(context, offline, localPackage, builtIn != null) {
        themeStoreCatalog(context, offline || (localPackage && builtIn == null))
    }
    val title = stringResource(R.string.theme_store_details)
    var loading by remember { mutableStateOf(true) }
    var lastSync by remember(catalog) { mutableStateOf<Long?>(null) }
    var theme by remember(themeId, revision, library, offline) { mutableStateOf<StoreTheme?>(null) }
    LaunchedEffect(catalog, themeId, revision, library, retry, builtIn) {
        loading = true
        val id = "$themeId:$revision"
        if (localPackage) {
            theme = builtIn?.theme ?: library.download(id)?.theme
            loading = false
            return@LaunchedEffect
        }
        val catalogState = catalog.cachedState()
        catalogState?.syncedAt?.let { lastSync = it }
        val cachedLatest = catalogState?.themes?.firstOrNull { it.themeId == themeId }
        val cached = cachedLatest?.takeIf { it.revision == revision }
        val saved = library.download(id)?.theme
        if (offline) {
            theme = saved
            loading = false
            return@LaunchedEffect
        }
        if (theme == null) theme = cached ?: saved
        try {
            if ((cached == null && saved == null) || retry > 0) {
                val updated = loadDetail("catalog for theme detail") { catalog.refreshState() }
                if (updated != null) lastSync = updated.syncedAt
                val latest = if (updated == null) cachedLatest else updated.themes.firstOrNull { it.themeId == themeId }
                theme = latest?.takeIf { it.revision == revision } ?: saved ?: cached
                    ?: latest?.takeIf { revision in 1 until it.revision }?.let { catalog.publishedRevision(it, revision) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not load theme detail")
            Toaster.e(R.string.theme_store_catalog_unavailable)
        } finally {
            loading = false
        }
    }
    val floating = NavigationBarPosition.BottomFloating.isCurrent()
    val bottom = Dimensions.navBarBottomPadding(floating) + if (floating) 10.dp else 15.dp
    Skeleton(
        navController = navController,
        topInsetHandledByContent = true,
        capturePageGlassBackdrop = false,
        miniPlayer = miniPlayer,
        navBarContent = { item -> item(0, title, R.drawable.ui) },
    ) { _ ->
        val padding = rememberPageChromeContentPadding(
            PaddingValues(start = ThemeStoreGutter, end = ThemeStoreGutter, bottom = bottom),
        )
        AppPullToRefreshBox(
            isRefreshing = loading && retry > 0,
            onRefresh = { if (!loading) { expireThemeStoreReads(); retry++ } },
            modifier = Modifier.fillMaxSize(),
        ) {
            val bundledTheme = builtIn?.theme
            val selected = bundledTheme ?: theme
            val loadingBundledPreviews = bundledTheme?.previews?.isEmpty() == true
            if (removedLocal != null || (selected == null && loading) || loadingBundledPreviews) {
                val previewedTheme = selected ?: initialDownload?.theme
                val ownsTheme = previewedTheme != null && signedInCreatorId() == previewedTheme.author.githubId
                val showSettings = previewedTheme != null && downloads.any { it.id == "${previewedTheme.themeId}:${previewedTheme.revision}" }
                ThemeDetailPlaceholder(
                    padding, previewedTheme, localPackage, lastSync,
                    showFollowActions = !ownsTheme,
                    showWithdraw = ownsTheme && !offline,
                    showSettings = showSettings,
                ) {
                    if (previewedTheme != null && removedLocal == null && !loadingBundledPreviews) {
                        ThemePreviewGallery(previewedTheme, catalog, retry)
                    } else {
                        ThemeGalleryPlaceholder()
                    }
                }
            } else if (selected != null) {
                key(profileId, selected.themeId, selected.revision) {
                    ThemeStoreDetailContent(selected, catalog, library, downloads, retry, padding, profileId, lastSync, localPackage, builtIn, onRemoved = ::leaveDetail, onRevision = { target ->
                        navController.navigate("${NavRoutes.themeStoreDetail.name}/${selected.themeId}/$target")
                    }) {
                        if (offline) Toaster.e(R.string.theme_store_offline_unavailable)
                        else navController.navigate("${NavRoutes.themeStoreCreator.name}/${selected.author.githubId}")
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                    DetailHeader()
                    val message = when {
                        localPackage -> R.string.common_load_failed
                        offline -> R.string.theme_store_offline_unavailable
                        else -> R.string.theme_store_catalog_unavailable
                    }
                    SettingsDescription(stringResource(message), textAlign = TextAlign.Center)
                    if (!offline && !localPackage) {
                        UiButton(onClick = { expireThemeStoreReads(); retry++ }) {
                            StoreText(stringResource(R.string.common_action_retry))
                        }
                    }
                }
            }
        }
    }
}

private enum class DetailDialog { Customize, Reviews, Revisions, Remove, UpdateSettings, Withdraw }

@Composable
private fun ThemeStoreDetailContent(
    theme: StoreTheme,
    catalog: ThemeStoreCatalog,
    library: ThemeStoreLibrary,
    downloads: List<SavedTheme>,
    retry: Int,
    padding: PaddingValues,
    profileId: String,
    lastSync: Long?,
    localPackage: Boolean,
    builtIn: SavedTheme?,
    onRemoved: () -> Unit,
    onRevision: (Int) -> Unit,
    onOpenCreator: () -> Unit,
) {
    val context = LocalContext.current
    val shimmer = forceLoadingShimmerPreview()
    val offline by OfflineMode.rememberEnabled()
    val accountId = signedInCreatorId()
    val ownsPublishedTheme = !localPackage && accountId != null && accountId == theme.author.githubId
    val verifiedIds = rememberVerifiedCreatorIds(catalog, retry)
    val pendingReports = rememberPendingThemeStoreReports(retry)
    val official = builtIn != null || verifiedIds?.contains(theme.author.githubId) == true
    if (!localPackage && !official && "${theme.themeId}:theme" in pendingReports) {
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(120.dp)) { StorePreviewImage(null, null, showImageRim = false) }
            SettingsDescription(stringResource(R.string.theme_store_report_theme_suspended))
        }
        return
    }
    var reportTheme by rememberSaveable { mutableStateOf(false) }
    if (reportTheme && !offline) {
        ThemeReportDialog(theme, null) { reportTheme = false }
    }
    val creator by produceState<CreatorProfile?>(null, theme.author, catalog, retry) {
        if (offline || localPackage) return@produceState
        value = loadDetail("creator") { catalog.creators().firstOrNull { it.githubId == theme.author.githubId } }
    }
    var reviewRetry by remember { mutableIntStateOf(0) }
    val reviewRead = rememberStoreReviews(catalog, theme, !offline && !localPackage, retry, reviewRetry)
    val reviews = reviewRead.summary
    val id = "${theme.themeId}:${theme.revision}"
    val downloaded = builtIn ?: downloads.firstOrNull { it.id == id }
    val appVersion = BuildConfig.VERSION_CODE
    val support = theme.supportAt(appVersion)
    val supported = support == StoreSupport.Supported
    var appearance by remember(theme.appearance) { mutableStateOf<StoreAppearance?>(null) }
    LaunchedEffect(theme, catalog, retry, downloaded, supported) {
        if (supported) appearance = loadDetail("appearance") { catalog.appearance(theme, downloaded) } ?: appearance
    }
    val downloadQueue = rememberThemeStoreDownloadQueue(profileId)
    val pendingDownloads by remember(downloadQueue) { downloadQueue?.activeDownloads ?: kotlinx.coroutines.flow.flowOf(emptySet<String>()) }
        .collectAsStateWithLifecycle(initialValue = emptySet(), context = NzikDispatchers.DATA)
    val downloading = id in pendingDownloads
    var followed by remember { mutableStateOf(library.follows().any { it.githubId == theme.author.githubId }) }
    var publicationAlerts by remember(library, theme.author) { mutableStateOf(library.publicationsEnabled(theme.author.githubId)) }
    val publicationCatalog by produceState<List<StoreTheme>?>(null, catalog, retry) {
        if (!localPackage) value = catalog.cached() ?: if (offline) null else loadDetail("catalog for publication alerts") { catalog.refresh() }
    }
    val canWithdraw = ownsPublishedTheme && !offline && publicationCatalog?.any { it.themeId == theme.themeId } == true
    val firstPublication by produceState(theme.publishedOn.takeIf { theme.revision == 1 }, theme, catalog, retry) {
        if (theme.revision > 1 && !localPackage) {
            value = library.download("${theme.themeId}:1")?.theme?.publishedOn
                ?: if (offline) null else loadDetail("first publication date") { catalog.firstPublicationDate(theme) }
        }
    }
    val revisions by produceState(listOf(theme), catalog, theme, retry, publicationCatalog, downloads) {
        val indexed = if (offline) emptyList() else publicationCatalog.orEmpty().filter { it.themeId == theme.themeId }
        val saved = downloads.mapNotNull { it.theme?.takeIf { savedTheme -> savedTheme.themeId == theme.themeId } }
        value = (listOf(theme) + indexed + saved).distinctBy { it.revision }.sortedByDescending { it.revision }
    }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    val onOpenTag: (String?) -> Unit = { selectedTag = it }
    selectedTag?.let { tag ->
        DefaultDialog(title = storeTagLabel(tag), onDismiss = { selectedTag = null },
            footer = { DetailCloseButton { selectedTag = null } },
        ) { SettingsDescription(theme.description) }
    }
    var dialog by rememberSaveable { mutableStateOf<DetailDialog?>(null) }
    var updateMode by remember(library, theme.themeId) { mutableStateOf(library.updateMode(theme.themeId)) }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dismiss = { dialog = null }
    val apply: (Set<StoreGroup>) -> Unit = { groups ->
        if (getActiveProfile(context) == profileId) {
            appearance?.selectedSettings(theme, appVersion, groups)?.let { settings ->
                undo = settings.applyTo(
                    context.preferences,
                    GlassProfilePreferences.preferences(context, profileId),
                    app.n_zik.android.utils.DataStoreUtils.prefs(context),
                )
                Toaster.i(R.string.theme_store_applied)
            }
        }
    }
    when (dialog) {
        DetailDialog.Customize -> if (supported) appearance?.let { parsed ->
            ThemeCustomizeDialog(
                groups = parsed.groups,
                compatibilityNotice = null,
                dismiss = dismiss,
            ) { selection ->
                apply(selection)
                dismiss()
            }
        }
        DetailDialog.Reviews -> ThemeReviewsDialog(theme, reviewRead, catalog, offline, {
            expireThemeStoreReviews(theme)
            reviewRetry++
        }, dismiss)
        DetailDialog.UpdateSettings -> ValueSelectorDialog(
            title = stringResource(R.string.theme_store_update_settings),
            titleSecondary = stringResource(R.string.theme_store_update_apply_manually),
            selectedValue = updateMode,
            values = ThemeUpdateMode.entries,
            onDismiss = dismiss,
            onValueSelected = { mode ->
                if (getActiveProfile(context) == profileId) {
                    library.setUpdateMode(theme.themeId, mode)
                    updateMode = mode
                }
            },
            valueText = { mode ->
                stringResource(when (mode) {
                    ThemeUpdateMode.AutoDownload -> R.string.theme_store_update_auto_download
                    ThemeUpdateMode.Notify -> R.string.theme_store_update_notify_only
                    ThemeUpdateMode.Off -> R.string.theme_store_update_off
                })
            },
        )
        DetailDialog.Revisions -> DefaultDialog(
            title = stringResource(R.string.theme_store_choose_revision),
            onDismiss = dismiss,
            footer = { DetailCloseButton(dismiss) },
        ) {
            revisions.forEach { revision ->
                DetailRow(revisionLabel(revision), R.drawable.alternative_version, shimmer) {
                    dismiss()
                    if (revision.revision != theme.revision) onRevision(revision.revision)
                }
            }
        }
        DetailDialog.Withdraw -> if (canWithdraw) {
            ThemeWithdrawalDialog(theme, profileId, dismiss)
        } else Unit
        DetailDialog.Remove -> DefaultDialog(
            title = stringResource(R.string.theme_store_remove_title),
            onDismiss = dismiss,
            footer = {
                ButtonRow(listOf(
                    DialogButton(stringResource(R.string.common_action_cancel), dismiss,
                        placement = DialogButtonPlacement.Start),
                    DialogButton(stringResource(R.string.common_action_delete), {
                        if (getActiveProfile(context) == profileId) {
                            library.removeDownload(id)
                            Toaster.s(R.string.theme_store_remove_done)
                            if (localPackage) onRemoved()
                        }
                        dismiss()
                    }, danger = true, placement = DialogButtonPlacement.End),
                ))
            },
        ) {
            SettingsDescription(stringResource(R.string.theme_store_remove_confirm))
        }
        null -> Unit
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { DetailHeader() }
        lastSync?.let { timestamp ->
            item(key = "detail-last-sync") { StoreLastSync(timestamp) }
        }
        item { ThemePreviewGallery(theme, catalog, retry) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f, fill = false)) {
                    val style = typography().xxl.copy(fontWeight = FontWeight.Bold)
                    if (shimmer) StoreLoadingLine(theme.title, style)
                    else StoreText(theme.title, style = style)
                }
            }
        }
        item {
            val style = typography().xxs.copy(color = colorPalette().textSecondary)
            if (shimmer) StoreLoadingLine(theme.description, style, maxLines = Int.MAX_VALUE)
            else BasicText(theme.description, style = style)
        }
        if (theme.tags.isNotEmpty()) {
            item {
                ThemeDetailTags(theme, shimmer = shimmer, onSelect = onOpenTag)
            }
        }
        if (localPackage) item {
            val authorName = localThemeCreatorName(theme)
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (shimmer) {
                    Spacer(Modifier.size(40.dp).clip(artistThumbnailShape()).shimmerEffect())
                } else if (builtIn != null) {
                    Icon(painterResource(R.drawable.ic_launcher_box), null,
                        Modifier.size(40.dp).clip(uiRoundnessShape()), tint = androidx.compose.ui.graphics.Color.Unspecified)
                } else if (theme.author.githubId > 0) {
                    CreatorAvatar(null, theme.author, catalog, 40.dp)
                } else {
                    app.n_zik.android.components.ui.screens.profiles.ProfileFaceAvatar(
                        avatar = app.n_zik.android.utils.FaceAvatar.Initials(authorName), faceName = authorName, size = 40.dp,
                    )
                }
                val style = typography().s.copy(fontWeight = FontWeight.Bold)
                if (shimmer) StoreLoadingLine(authorName, style)
                else StoreText(authorName, style = style)
                OfficialCreatorBadge(builtIn != null)
            }
        }
        if (!localPackage) item {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.weight(1f).clip(uiRoundnessShape()).clickable(onClick = onOpenCreator),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (shimmer) Spacer(Modifier.size(40.dp).clip(artistThumbnailShape()).shimmerEffect())
                    else CreatorAvatar(creator, theme.author, catalog, 40.dp)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.weight(1f, fill = false)) {
                                val name = creator?.displayName ?: theme.author.login
                                val style = typography().s.copy(fontWeight = FontWeight.Bold)
                                if (shimmer) StoreLoadingLine(name, style)
                                else StoreText(name, style = style)
                            }
                            OfficialCreatorBadge(verifiedIds?.contains(theme.author.githubId) == true)
                        }
                        val handle = "@${theme.author.login}"
                        if (shimmer) StoreLoadingLine(handle, typography().xs)
                        else StoreText(handle, secondary = true, style = typography().xs)
                    }
                }
                if (!ownsPublishedTheme) {
                ArtistFollowButton(likeState = true.takeIf { followed }, onClick = {
                    if (getActiveProfile(context) == profileId) {
                        followed = !followed
                        library.setFollowed(theme.author, followed)
                        Toaster.i(if (followed) R.string.theme_store_followed else R.string.theme_store_unfollowed)
                    }
                })
                CreatorPublicationBell(publicationAlerts, !offline && publicationCatalog != null) {
                    publicationCatalog?.let { current ->
                        publicationAlerts = !publicationAlerts
                        library.setPublicationsEnabled(theme.author.githubId, publicationAlerts, current)
                    }
                }
                }
                val shareLabel = stringResource(R.string.theme_store_share)
                StoreIconAction(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "nzik://theme/${theme.themeId}/${theme.revision}")
                        try {
                            context.startActivity(Intent.createChooser(send, shareLabel))
                        } catch (error: ActivityNotFoundException) {
                            Toaster.e(R.string.theme_store_share_failed)
                        }
                    },
                ) {
                    DetailIcon(R.drawable.share_social, Modifier.size(20.dp), shareLabel)
                }
                if (canWithdraw) {
                    StoreIconAction(onClick = { dialog = DetailDialog.Withdraw }) {
                        DetailIcon(R.drawable.trash, Modifier.size(20.dp), stringResource(R.string.theme_store_withdraw))
                    }
                }
                if (downloaded != null) {
                    StoreIconAction(onClick = { dialog = DetailDialog.UpdateSettings }) {
                        DetailIcon(R.drawable.settings, Modifier.size(20.dp), stringResource(R.string.theme_store_update_settings))
                    }
                }
            }
        }
        item {
            ThemeCardSurface {
                Column {
                    if (!localPackage) {
                        val summaryPending = reviewRead.initialPending
                        DetailRow(
                            if (summaryPending) stringResource(R.string.theme_store_reviews) else reviewLabel(reviews),
                            R.drawable.star_brilliant,
                            shimmer || summaryPending,
                        ) { dialog = DetailDialog.Reviews }
                        if (builtIn == null) {
                            DetailRow(revisionLabel(theme), R.drawable.alternative_version, shimmer) { dialog = DetailDialog.Revisions }
                        }
                        firstPublication?.let { DetailRow(stringResource(R.string.theme_store_published_on, it), R.drawable.calendar, shimmer) }
                        DetailRow(
                            appearance?.let { Formatter.formatShortFileSize(context, it.size.toLong()) }
                                ?: stringResource(R.string.theme_store_size_unavailable),
                            R.drawable.download,
                            shimmer,
                        )
                    }
                    if (builtIn == null) OtherInfoSettingsEntry(
                        title = stringResource(R.string.theme_store_compatibility),
                        text = compatibilityLabel(support),
                        icon = R.drawable.information,
                        shimmerText = shimmer,
                    )
                    if (!offline && verifiedIds != null && !official && publicationCatalog.orEmpty().any { it.themeId == theme.themeId }) {
                        DetailRow(stringResource(R.string.theme_store_report_theme), R.drawable.information) {
                            reportTheme = true
                        }
                    }
                }
            }
        }
        item {
            val hasGroups = appearance?.groups?.isNotEmpty() == true
            val latestTheme = publicationCatalog.orEmpty().filter { it.themeId == theme.themeId }
                .maxByOrNull { it.revision }
            val updateTarget = latestTheme?.takeIf {
                !offline && !localPackage && downloaded != null && it.revision > theme.revision &&
                    it.supportAt(appVersion) == StoreSupport.Supported &&
                    library.download("${theme.themeId}:${it.revision}") == null
            }?.revision
            val isUpdate = !localPackage && latestTheme?.revision == theme.revision && library.downloads().any {
                it.theme?.themeId == theme.themeId && it.theme.revision < theme.revision
            }
            Column(
                modifier = Modifier.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (supported && downloaded == null && !localPackage) {
                    ButtonRow(listOf(DialogButton(
                        stringResource(when {
                            downloading -> R.string.theme_store_downloading
                            isUpdate -> R.string.common_action_update
                            else -> R.string.theme_store_download
                        }),
                        { downloadQueue?.enqueue(theme) },
                        enabled = !downloading && !offline && downloadQueue != null,
                        primary = true,
                    )))
                } else if (downloaded != null) {
                    if (updateTarget != null) ButtonRow(listOf(DialogButton(
                        stringResource(R.string.common_action_update),
                        { onRevision(updateTarget) },
                        primary = true,
                    )))
                    if (supported) ButtonRow(listOf(
                        DialogButton(stringResource(R.string.theme_store_customize), { dialog = DetailDialog.Customize }, enabled = hasGroups),
                        DialogButton(stringResource(R.string.common_action_apply), { apply(appearance?.groups?.keys.orEmpty()) }, primary = true, enabled = hasGroups),
                    ))
                    if (builtIn == null) ButtonRow(listOf(DialogButton(
                        stringResource(R.string.common_action_delete),
                        { dialog = DetailDialog.Remove },
                        danger = true,
                    )))
                }
                undo?.let { restore ->
                    UiTextButton(onClick = {
                        if (getActiveProfile(context) == profileId) {
                            restore()
                            Toaster.i(R.string.theme_store_undo_success)
                        }
                        undo = null
                    }) { StoreText(stringResource(R.string.theme_store_undo)) }
                }
            }
        }
    }
}

@Composable
private fun ThemeDetailPlaceholder(
    padding: PaddingValues,
    theme: StoreTheme?,
    localPackage: Boolean,
    lastSync: Long?,
    showFollowActions: Boolean,
    showWithdraw: Boolean,
    showSettings: Boolean,
    gallery: @Composable () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { DetailHeader() }
        lastSync?.let { timestamp -> item { StoreLastSync(timestamp) } }
        item { gallery() }
        item { StoreLoadingLine(theme?.title, typography().xxl.copy(fontWeight = FontWeight.Bold)) }
        item { StoreLoadingLine(theme?.description, typography().xxs, width = 0.85f, maxLines = Int.MAX_VALUE) }
        if (theme != null && theme.tags.isNotEmpty()) {
            item { ThemeDetailTags(theme, shimmer = true) {} }
        }
        item {
            Row(
                Modifier.padding(vertical = if (localPackage) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Spacer(Modifier.size(40.dp).clip(artistThumbnailShape()).shimmerEffect())
                    Column(Modifier.weight(1f)) {
                        StoreLoadingLine(theme?.author?.login, typography().s.copy(fontWeight = FontWeight.Bold))
                        if (!localPackage) StoreLoadingLine(theme?.author?.login?.let { "@$it" }, typography().xs, width = 0.35f)
                    }
                }
                if (!localPackage) {
                    if (showFollowActions) {
                        ArtistFollowButton(likeState = null, onClick = {}, enabled = false)
                        CreatorPublicationBell(enabled = false, available = false, onToggle = {})
                    }
                    StoreIconAction(available = false, onClick = {}) {
                        DetailIcon(R.drawable.share_social, Modifier.size(20.dp), stringResource(R.string.theme_store_share))
                    }
                    if (showWithdraw) StoreIconAction(available = false, onClick = {}) {
                        DetailIcon(R.drawable.trash, Modifier.size(20.dp), stringResource(R.string.theme_store_withdraw))
                    }
                    if (showSettings) StoreIconAction(available = false, onClick = {}) {
                        DetailIcon(R.drawable.settings, Modifier.size(20.dp), stringResource(R.string.theme_store_update_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeDetailTags(theme: StoreTheme, shimmer: Boolean = false, onSelect: (String?) -> Unit) {
    GlassHomeChips<String?>(
        chips = theme.tags.canonicalStoreTags().map { it to storeTagLabel(it) },
        currentValue = null,
        onValueUpdate = onSelect,
        shimmer = shimmer,
    )
}

@Composable
private fun ThemeGalleryPlaceholder() {
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
private fun DetailHeader() {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailIcon(R.drawable.ui, Modifier.size(20.dp))
            StoreText(stringResource(R.string.theme_store_details), style = typography().xxl.copy(fontWeight = FontWeight.Bold))
        }
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
private fun ThemePreviewGallery(theme: StoreTheme, catalog: ThemeStoreCatalog, retry: Int) {
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

@Composable
private fun DetailRow(title: String, icon: Int, shimmerText: Boolean = false, onClick: (() -> Unit)? = null) {
    if (onClick == null) OtherInfoSettingsEntry(title = title, text = "", icon = icon, shimmerText = shimmerText)
    else OtherSettingsEntry(title = title, text = "", icon = icon, shimmerText = shimmerText, onClick = onClick)
}

@Composable
private fun DetailIcon(icon: Int, modifier: Modifier, label: String? = null) {
    Icon(painterResource(icon), contentDescription = label, modifier = modifier, tint = colorPalette().text)
}

@Composable
private fun revisionLabel(theme: StoreTheme): String =
    theme.publishedOn?.let { stringResource(R.string.theme_store_revision_dated, theme.revision, it) }
        ?: stringResource(R.string.theme_store_revision, theme.revision)

@Composable
private fun reviewLabel(reviews: StoreReviewSummary?): String = when {
    reviews == null -> stringResource(R.string.theme_store_reviews_unavailable)
    reviews.count == 0 -> stringResource(R.string.theme_store_no_reviews)
    else -> stringResource(R.string.theme_store_rating_count, reviews.average, reviews.count)
}

@Composable
private fun compatibilityLabel(support: StoreSupport): String = when (support) {
    StoreSupport.Pending -> stringResource(R.string.theme_store_support_pending)
    StoreSupport.RequiresUpdate -> stringResource(R.string.theme_store_support_requires_update)
    StoreSupport.Legacy -> stringResource(R.string.theme_store_support_legacy, ThemeLegacyMaxVersionCode)
    StoreSupport.Supported -> stringResource(R.string.theme_store_support_minimum)
}

private val pendingReportsByAccount = kotlinx.coroutines.flow.MutableStateFlow<Map<Long, Set<String>>>(emptyMap())

@Composable
internal fun rememberPendingThemeStoreReports(refreshVersion: Int): Set<String> {
    val accountId = signedInCreatorId()
    val login by GitHubAccount.login.collectAsStateWithLifecycle()
    val offline by OfflineMode.rememberEnabled()
    val reports by pendingReportsByAccount.collectAsStateWithLifecycle()
    LaunchedEffect(accountId, login, offline, refreshVersion) {
        val currentLogin = login
        if (!offline && accountId != null && currentLogin != null) {
            try {
                val pending = pendingThemeStoreReports(accountId, currentLogin)
                pendingReportsByAccount.value = pendingReportsByAccount.value + (accountId to pending)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not load pending reports")
            }
        }
    }
    return reports[accountId].orEmpty()
}

internal data class StoreReviewRead(val summary: StoreReviewSummary?, val initialPending: Boolean)

@Composable
internal fun rememberStoreReviews(
    catalog: ThemeStoreCatalog,
    theme: StoreTheme,
    enabled: Boolean,
    refreshVersion: Int,
    retry: Int = 0,
): StoreReviewRead {
    var summary by remember(catalog, theme.themeId, theme.revision) { mutableStateOf<StoreReviewSummary?>(null) }
    var loading by remember(catalog, theme.themeId, theme.revision) { mutableStateOf(enabled) }
    LaunchedEffect(catalog, theme, enabled, refreshVersion, retry) {
        if (!enabled) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            catalog.reviews(theme)?.let { summary = it }
        } finally {
            loading = false
        }
    }
    return StoreReviewRead(summary, enabled && loading && summary == null)
}

@Composable
private fun ThemeReviewsDialog(
    theme: StoreTheme,
    reviewRead: StoreReviewRead,
    catalog: ThemeStoreCatalog,
    offline: Boolean,
    onRetry: () -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val shimmer = forceLoadingShimmerPreview()
    val reviews = reviewRead.summary
    val scope = rememberCoroutineScope()
    val authorId = signedInCreatorId()
    val pending = rememberPendingThemeStoreReports(0)
    var editing by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var reportedReviewer by rememberSaveable { mutableStateOf<Long?>(null) }
    var reportAllReviews by rememberSaveable { mutableStateOf(false) }
    if (reportAllReviews && !offline) {
        ThemeReportDialog(theme, null, allReviews = true) { reportAllReviews = false }
        return
    }
    val reportedReview = reviews?.comments?.firstOrNull { it.author.githubId == reportedReviewer }
    if (reportedReview != null && !offline) {
        ThemeReportDialog(theme, reportedReview) { reportedReviewer = null }
        return
    }
    val awaiting by GitHubAccount.awaiting.collectAsStateWithLifecycle()
    val existing = reviews?.comments?.firstOrNull { it.author.githubId == authorId }
    var rating by rememberSaveable(authorId, existing) { mutableIntStateOf(existing?.rating ?: 0) }
    var comment by rememberSaveable(authorId, existing) { mutableStateOf(existing?.comment.orEmpty()) }
    if (editing && authorId != null) {
        val submit: () -> Unit = {
            busy = true
            scope.launch {
                try {
                    val token = checkNotNull(GitHubAccount.tokenFor(context, authorId))
                    val url = submitCreatorProfile(token, authorId, emptyMap(),
                        review = ThemeReviewSubmission(theme, rating, comment))
                    val number = url.substringAfterLast('/').toInt()
                    context.getSharedPreferences("theme_creator_submissions", 0).edit()
                        .putString("$authorId:$number", "open").apply()
                    notifyCreatorSubmissionSent(context, authorId, number, R.string.theme_store_review_sent)
                    Toaster.s(R.string.theme_store_review_sent)
                    editing = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (blocked: CreatorPublicationBlocked) {
                    Toaster.e(blocked.publicMessage, android.widget.Toast.LENGTH_LONG)
                } catch (_: CreatorPublicationUnchanged) {
                    Toaster.i(R.string.theme_store_review_unchanged)
                } catch (_: CreatorPublicationWorkflowPermission) {
                    GitHubAccount.requireWorkflowAuthorization(context)
                    Toaster.i(R.string.theme_store_creator_workflow_authorization)
                } catch (_: CreatorPublicationSessionExpired) {
                    GitHubAccount.clearPublicationToken(context)
                    Toaster.i(R.string.theme_store_creator_session_expired)
                } catch (error: Exception) {
                    Timber.tag("ThemeStore").w(error, "Review submission failed")
                    Toaster.e(R.string.theme_store_review_error)
                } finally {
                    busy = false
                }
            }
        }
        val authorize = rememberGitHubPublicationAuthorization(submit)
        val back = { if (!busy && !awaiting) editing = false }
        DefaultDialog(
            title = stringResource(R.string.theme_store_review_write), onDismiss = back,
            footer = {
                ButtonRow(listOf(
                    DialogButton(stringResource(R.string.common_action_cancel), back, danger = true, enabled = !busy && !awaiting),
                    DialogButton(stringResource(R.string.theme_store_review_send), {
                        if (GitHubAccount.tokenFor(context, authorId) != null) submit()
                        else authorize(authorId)
                    }, primary = true, enabled = rating in 1..5 && comment.isNotBlank() && !busy && !awaiting)
                ))
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (star in 1..5) {
                        PageIconButton(onClick = { if (!busy) rating = star }) {
                            Icon(painterResource(R.drawable.star_brilliant),
                                stringResource(R.string.theme_store_review_rating, star),
                                Modifier.size(20.dp),
                                tint = if (star <= rating) colorPalette().accent else colorPalette().textSecondary)
                        }
                    }
                }
                TextField(comment, { if (it.themeTextLength <= 2000) comment = it },
                    Modifier.fillMaxWidth(), enabled = !busy, minLines = 3, maxLines = 5,
                    label = { Text(stringResource(R.string.theme_store_review_comment)) },
                    shape = uiRoundnessShape(), colors = InputDialog.defaultTextFieldColors())
            }
        }
        return
    }
    DefaultDialog(
        title = stringResource(R.string.theme_store_reviews),
        onDismiss = dismiss,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!offline && !reviews?.comments.isNullOrEmpty()) {
                    ButtonRow(listOf(DialogButton(
                        stringResource(R.string.theme_store_report_all_reviews),
                        { reportAllReviews = true },
                    )))
                }
                ButtonRow(listOf(
                    DialogButton(stringResource(R.string.common_action_close), dismiss, danger = true,
                        placement = DialogButtonPlacement.Start),
                    DialogButton(
                        text = stringResource(R.string.theme_store_review_send),
                        onClick = {
                            when {
                                authorId == null -> Toaster.i(R.string.theme_store_review_sign_in)
                                offline -> Toaster.e(R.string.theme_store_offline_unavailable)
                                else -> editing = true
                            }
                        },
                        enabled = !reviewRead.initialPending,
                        primary = authorId != null && !offline,
                        textColor = colorPalette().textSecondary.takeIf { authorId == null || offline },
                        placement = DialogButtonPlacement.End,
                        wrapLabel = true,
                    ),
                ))
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (reviewRead.initialPending) {
                StoreLoadingLine(null, typography().s)
                repeat(2) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(30.dp).clip(artistThumbnailShape()).shimmerEffect())
                            Box(Modifier.weight(1f)) { StoreLoadingLine(null, typography().s, width = 0.45f) }
                            DetailIcon(R.drawable.star_brilliant, Modifier.size(14.dp))
                            Box(Modifier.width(48.dp)) { StoreLoadingLine(null, typography().s, width = 1f) }
                        }
                        StoreLoadingLine(null, typography().xxs, width = 0.85f)
                    }
                }
            } else {
                if (shimmer) StoreLoadingLine(reviewLabel(reviews), typography().s)
                else StoreText(reviewLabel(reviews))
                if (reviews == null && !offline) {
                    UiTextButton(onClick = onRetry) { StoreText(stringResource(R.string.common_action_retry)) }
                }
            }
            reviews?.comments.orEmpty().forEach { review ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (shimmer) Spacer(Modifier.size(30.dp).clip(artistThumbnailShape()).shimmerEffect())
                        else CreatorAvatar(null, review.author, catalog, 30.dp)
                        val handle = "@${review.author.login}"
                        Box(Modifier.weight(1f, fill = false)) {
                            if (shimmer) StoreLoadingLine(handle, typography().s)
                            else StoreText(handle)
                        }
                        DetailIcon(R.drawable.star_brilliant, Modifier.size(14.dp))
                        val label = stringResource(R.string.theme_store_review_rating, review.rating)
                        if (shimmer) StoreLoadingLine(label, typography().s)
                        else StoreText(label, secondary = true)
                    }
                    val hidden = "${theme.themeId}:reviews:${theme.revision}" in pending ||
                        "${theme.themeId}:review:${theme.revision}:${review.author.githubId}" in pending
                    val comment = if (hidden) stringResource(R.string.theme_store_report_message_hidden) else review.comment
                    if (shimmer) StoreLoadingLine(comment, typography().xxs, maxLines = Int.MAX_VALUE)
                    else SettingsDescription(comment)
                    if (!offline) UiTextButton(onClick = { reportedReviewer = review.author.githubId }) {
                        StoreText(stringResource(R.string.theme_store_report_review))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThemeReportDialog(theme: StoreTheme, review: StoreReview?, allReviews: Boolean = false, dismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountId = signedInCreatorId()
    var reason by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val awaiting by GitHubAccount.awaiting.collectAsStateWithLifecycle()
    val title = stringResource(when {
        review != null -> R.string.theme_store_report_review
        allReviews -> R.string.theme_store_report_all_reviews
        else -> R.string.theme_store_report_theme
    })
    val close = { if (!sending && !awaiting) dismiss() }
    val submit: () -> Unit = {
        val submittedReason = reason.trim()
        sending = true
        scope.launch {
            try {
                val id = checkNotNull(GitHubAccount.userId(context))
                val token = checkNotNull(GitHubAccount.tokenFor(context, id))
                val result = submitThemeReport(token, id, theme, review, allReviews, submittedReason)
                val message = if (result.updated) R.string.theme_store_report_updated else R.string.theme_store_report_sent
                Toaster.s(context.getString(message, result.number))
                if (result.pending) {
                    val target = when {
                        review != null -> "review:${theme.revision}:${review.author.githubId}"
                        allReviews -> "reviews:${theme.revision}"
                        else -> "theme"
                    }
                    val reports = pendingReportsByAccount.value
                    pendingReportsByAccount.value = reports +
                        (id to (reports[id].orEmpty() + "${theme.themeId}:$target"))
                }
                dismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (blocked: CreatorPublicationBlocked) {
                Toaster.e(blocked.publicMessage)
            } catch (_: CreatorPublicationSessionExpired) {
                GitHubAccount.clearPublicationToken(context)
                Toaster.i(R.string.theme_store_creator_session_expired)
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not submit report")
                Toaster.e(R.string.theme_store_report_error)
            } finally {
                sending = false
            }
        }
    }
    val authorize = rememberGitHubPublicationAuthorization(submit)
    DefaultDialog(title = title, onDismiss = close, footer = {
        ButtonRow(listOf(
            DialogButton(stringResource(R.string.common_action_cancel), close, danger = true, enabled = !sending && !awaiting),
            DialogButton(stringResource(R.string.theme_store_report_send), {
                if (accountId != null && GitHubAccount.tokenFor(context, accountId) != null) submit()
                else authorize(accountId)
            }, primary = true, enabled = reason.isNotBlank() && !sending && !awaiting),
        ))
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsDescription(stringResource(R.string.theme_store_report_public_notice))
            TextField(reason, { if (it.themeTextLength <= 2000) reason = it },
                Modifier.fillMaxWidth(), enabled = !sending && !awaiting, minLines = 3, maxLines = 5,
                label = { Text(stringResource(R.string.theme_store_report_reason)) },
                shape = uiRoundnessShape(), colors = InputDialog.defaultTextFieldColors())
        }
    }
}

@Composable
private fun DetailCloseButton(dismiss: () -> Unit) {
    ButtonRow(listOf(DialogButton(stringResource(R.string.common_action_close), dismiss, danger = true)))
}

@Composable
private fun ThemeCustomizeDialog(
    groups: Map<StoreGroup, ThemeSettings>,
    compatibilityNotice: String?,
    dismiss: () -> Unit,
    onApply: (Set<StoreGroup>) -> Unit,
) {
    var selected by remember(groups) { mutableStateOf(groups.keys) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val canApply = selected.isNotEmpty() && compatibilityNotice == null
    DefaultDialog(
        title = stringResource(R.string.theme_store_customize),
        onDismiss = dismiss,
        footer = {
            ButtonRow(listOf(
                DialogButton(stringResource(R.string.common_action_cancel), dismiss, danger = true),
                DialogButton(
                    stringResource(R.string.common_action_apply),
                    { onApply(selected) },
                    primary = true,
                    enabled = canApply,
                ),
            ))
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsDescription(stringResource(R.string.theme_store_choose_groups))
            StoreGroup.entries.forEach { group ->
                val (label, icon) = when (group) {
                    StoreGroup.Colors -> R.string.theme_store_group_colors to R.drawable.color_palette
                    StoreGroup.PlayerAndQueue -> R.string.theme_store_group_player_queue to R.drawable.musical_notes
                    StoreGroup.Glass -> R.string.theme_store_preview_glass to R.drawable.sparkles
                    StoreGroup.Font -> R.string.theme_store_group_font to R.drawable.text
                    StoreGroup.Navigation -> R.string.theme_store_group_navigation to R.drawable.arrow_forward
                    StoreGroup.PageLayout -> R.string.theme_store_group_layout to R.drawable.sort_grid
                    StoreGroup.Lyrics -> R.string.theme_store_group_lyrics to R.drawable.song_lyrics
                    StoreGroup.Animations -> R.string.theme_store_group_animations to R.drawable.star_brilliant
                }
                ThemeSettingsCategoryRow(
                    label, icon, groups[group], group in selected, expanded == group.name,
                    onCheckedChange = { checked -> selected = if (checked) selected + group else selected - group },
                    onExpand = { expanded = if (expanded == group.name) null else group.name },
                )
            }
            SettingsDescription(stringResource(R.string.theme_store_customize_description))
            compatibilityNotice?.let { SettingsDescription(it) }
        }
    }
}

@Composable
internal fun ThemeSettingsCategoryRow(
    label: Int,
    icon: Int,
    settings: ThemeSettings?,
    checked: Boolean,
    isExpanded: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onExpand: () -> Unit,
) {
    val enabled = settings != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp)
            .alpha(if (enabled) 1f else .45f).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NzikCheckbox(checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp)
                .clip(uiRoundnessShape())
                .clickable(enabled = enabled, onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DetailIcon(icon, Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                StoreText(stringResource(label), style = typography().s)
                val count = settings?.let { it.plain.size + it.glass.size } ?: 0
                SettingsDescription(if (settings == null) {
                    stringResource(R.string.theme_store_group_unavailable)
                } else {
                    pluralStringResource(R.plurals.theme_store_settings_count, count, count)
                })
            }
            if (enabled) DetailIcon(
                if (isExpanded) R.drawable.chevron_up else R.drawable.chevron_down,
                Modifier.size(16.dp),
            )
        }
    }
    if (settings != null) AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn(navigationChromeTween()) + expandVertically(navigationChromeTween()),
        exit = fadeOut(navigationChromeTween()) + shrinkVertically(navigationChromeTween()),
    ) {
        val values = remember(settings) { (settings.plain + settings.glass).toList() }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(start = 64.dp, end = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(values, key = { it.first }) { (key, value) ->
                val name = (themeSettingNames[key] ?: key).readableThemeValue()
                val detail = when (value) {
                    true -> stringResource(R.string.theme_store_setting_on)
                    false -> stringResource(R.string.theme_store_setting_off)
                    UseThemeDefault -> stringResource(R.string.common_option_default)
                    is String -> value.readableThemeValue()
                    else -> value.toString()
                }
                BasicText(
                    stringResource(R.string.theme_store_setting_detail, name, detail),
                    style = typography().xxs.copy(color = colorPalette().textSecondary),
                )
            }
        }
    }
}

private val themeWordBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])")

private fun String.readableThemeValue(): String =
    replace('_', ' ').replace(themeWordBoundary, " ").replaceFirstChar { it.titlecase() }

private suspend fun <T> loadDetail(name: String, load: suspend () -> T): T? = try {
    load()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Timber.tag("ThemeStore").w(error, "Could not load theme %s", name)
    null
}
