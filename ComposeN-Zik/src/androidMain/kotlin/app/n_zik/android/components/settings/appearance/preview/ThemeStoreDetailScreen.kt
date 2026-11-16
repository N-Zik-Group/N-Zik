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
import app.n_zik.android.components.ui.FullPageLoader
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import app.n_zik.android.components.glass.GlassTextField as TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
    val builtIn = remember(context, themeId) { if (themeId == BundledThemeId) bundledDefaultTheme(context) else null }
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
    val catalog = remember(context, offline, localPackage) { themeStoreCatalog(context, offline || localPackage) }
    val title = stringResource(R.string.theme_store_details)
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var lastSync by remember(catalog) { mutableStateOf<Long?>(null) }
    var theme by remember(themeId, revision, library, offline) { mutableStateOf<StoreTheme?>(null) }
    LaunchedEffect(catalog, themeId, revision, library, retry) {
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
            if (saved == null) Toaster.e(R.string.theme_store_offline_unavailable)
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
            val selected = theme
            if (removedLocal != null) {
                FullPageLoader(modifier = Modifier.fillMaxSize().padding(padding))
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
                    if (loading) {
                        FullPageLoader()
                    } else {
                        SettingsDescription(stringResource(R.string.theme_store_catalog_unavailable), textAlign = TextAlign.Center)
                        UiButton(onClick = { expireThemeStoreReads(); retry++ }) { Text(stringResource(R.string.common_action_retry)) }
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
    val scope = rememberCoroutineScope()
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
    val reviews by produceState<StoreReviewSummary?>(null, theme, catalog, retry) {
        if (offline || localPackage) return@produceState
        value = catalog.reviews(theme)
    }
    val id = "${theme.themeId}:${theme.revision}"
    val downloaded = builtIn ?: downloads.firstOrNull { it.id == id }
    var appearance by remember(theme.appearance) { mutableStateOf<StoreAppearance?>(null) }
    LaunchedEffect(theme, catalog, retry, downloaded) {
        appearance = loadDetail("appearance") { catalog.appearance(theme, downloaded) } ?: appearance
    }
    var downloading by remember { mutableStateOf(false) }
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
    val support = theme.compatibility.supportAt(BuildConfig.VERSION_CODE)
    val appVersion = BuildConfig.VERSION_CODE
    val dismiss = { dialog = null }
    val apply: (Set<StoreGroup>) -> Unit = { groups ->
        if (getActiveProfile(context) == profileId) {
            appearance?.selectedSettings(theme.compatibility, appVersion, groups)?.let { settings ->
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
        DetailDialog.Customize -> appearance?.let { parsed ->
            ThemeCustomizeDialog(
                groups = parsed.groups,
                compatibilityNotice = compatibilityLabel(support).takeIf { support != StoreSupport.Supported },
                dismiss = dismiss,
            ) { selection ->
                apply(selection)
                dismiss()
            }
        }
        DetailDialog.Reviews -> ThemeReviewsDialog(theme, reviews, catalog, offline, dismiss)
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
                DetailRow(revisionLabel(revision), R.drawable.alternative_version) {
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
                    StoreText(theme.title, style = typography().xxl.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        item {
            BasicText(theme.description, style = typography().xxs.copy(color = colorPalette().textSecondary))
        }
        if (theme.tags.isNotEmpty()) {
            item {
                GlassHomeChips<String?>(
                    chips = theme.tags.canonicalStoreTags().map { it to storeTagLabel(it) },
                    currentValue = null,
                    onValueUpdate = { selected -> onOpenTag(selected) },
                )
            }
        }
        if (localPackage) item {
            val authorName = localThemeCreatorName(theme)
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (builtIn != null) {
                    Icon(painterResource(R.drawable.ic_launcher_box), null,
                        Modifier.size(40.dp).clip(uiRoundnessShape()), tint = androidx.compose.ui.graphics.Color.Unspecified)
                } else if (theme.author.githubId > 0) {
                    CreatorAvatar(null, theme.author, catalog, 40.dp)
                } else {
                    app.n_zik.android.components.ui.screens.profiles.ProfileFaceAvatar(
                        avatar = app.n_zik.android.utils.FaceAvatar.Initials(authorName), faceName = authorName, size = 40.dp,
                    )
                }
                StoreText(authorName, style = typography().s.copy(fontWeight = FontWeight.Bold))
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
                    CreatorAvatar(creator, theme.author, catalog, 40.dp)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.weight(1f, fill = false)) {
                                StoreText(creator?.displayName ?: theme.author.login, style = typography().s.copy(fontWeight = FontWeight.Bold))
                            }
                            OfficialCreatorBadge(verifiedIds?.contains(theme.author.githubId) == true)
                        }
                        StoreText("@${theme.author.login}", secondary = true, style = typography().xs)
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
                        DetailRow(reviewLabel(reviews), R.drawable.star_brilliant) { dialog = DetailDialog.Reviews }
                        DetailRow(revisionLabel(theme), R.drawable.alternative_version) { dialog = DetailDialog.Revisions }
                        firstPublication?.let { DetailRow(stringResource(R.string.theme_store_published_on, it), R.drawable.calendar) }
                        DetailRow(
                            appearance?.let { Formatter.formatShortFileSize(context, it.size.toLong()) }
                                ?: stringResource(R.string.theme_store_size_unavailable),
                            R.drawable.download,
                        )
                    }
                    OtherInfoSettingsEntry(
                        title = stringResource(R.string.theme_store_compatibility),
                        text = compatibilityLabel(support),
                        icon = R.drawable.information,
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
            val ready = hasGroups && support == StoreSupport.Supported
            val latestRevision = publicationCatalog.orEmpty().filter { it.themeId == theme.themeId }
                .maxByOrNull { it.revision }?.revision
            val updateTarget = latestRevision?.takeIf {
                !offline && !localPackage && downloaded != null && it > theme.revision &&
                    library.download("${theme.themeId}:$it") == null
            }
            val isUpdate = !localPackage && latestRevision == theme.revision && library.downloads().any {
                it.theme?.themeId == theme.themeId && it.theme.revision < theme.revision
            }
            Column(
                modifier = Modifier.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (downloaded == null && !localPackage) {
                    ButtonRow(listOf(DialogButton(
                        stringResource(when {
                            downloading -> R.string.theme_store_downloading
                            isUpdate -> R.string.common_action_update
                            else -> R.string.theme_store_download
                        }),
                        {
                            downloading = true
                            scope.launch {
                                val notification = ThemeDownloadNotification(context.applicationContext, theme)
                                notification.show()
                                try {
                                    val parsed = catalog.downloadForOffline(theme, appearance)
                                    if (getActiveProfile(context) == profileId) {
                                        val saved = SavedTheme(theme.title, parsed.document, theme)
                                        library.save(saved)
                                        appearance = parsed
                                        Toaster.s(R.string.theme_store_download_saved)
                                        notification.show(result = true)
                                    } else {
                                        notification.cancel()
                                    }
                                } catch (cancelled: CancellationException) {
                                    notification.cancel()
                                    throw cancelled
                                } catch (error: Exception) {
                                    notification.show(result = false)
                                    Timber.tag("ThemeStore").w(error, "Could not download theme")
                                    Toaster.e(R.string.theme_store_download_failed)
                                } finally {
                                    downloading = false
                                }
                            }
                        },
                        enabled = !downloading,
                        primary = true,
                    )))
                } else if (downloaded != null) {
                    if (updateTarget != null) ButtonRow(listOf(DialogButton(
                        stringResource(R.string.common_action_update),
                        { onRevision(updateTarget) },
                        primary = true,
                    )))
                    ButtonRow(listOf(
                        DialogButton(stringResource(R.string.theme_store_customize), { dialog = DetailDialog.Customize }, enabled = hasGroups),
                        DialogButton(stringResource(R.string.common_action_apply), { apply(appearance?.groups?.keys.orEmpty()) }, primary = true, enabled = ready),
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
private fun ThemePreviewGallery(theme: StoreTheme, catalog: ThemeStoreCatalog, retry: Int) {
    if (theme.previews.isEmpty()) return
    val currentGlass = rememberGlassSettings().enabled
    val variants = listOf(false, true).filter { glass -> theme.previews.any { it.glassVariant == glass } }
    var selectedGlass by rememberSaveable { mutableStateOf(currentGlass) }
    val variant = selectedGlass.takeIf { it in variants } ?: variants.firstOrNull()
    val previews = theme.galleryPreviews(variant)
    var expandedPath by remember(theme.themeId, theme.revision, variant) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (variants.size > 1) SettingsDialogTabRow(
            labels = listOf(stringResource(R.string.theme_store_preview_normal), stringResource(R.string.theme_store_preview_glass)),
            selectedTabIndex = if (variant == true) 1 else 0,
            equalWidthWhenFitting = true,
            followNestedTarget = false,
        ) { selectedGlass = it == 1 }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = (maxWidth - 16.dp) / 3
            key(variant) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(previews, key = { it.path }) { preview ->
                        ThemeGalleryPreview(theme, preview, catalog, retry, Modifier.width(width)) {
                            expandedPath = preview.path
                        }
                    }
                }
            }
        }
    }
    val initialPage = previews.indexOfFirst { it.path == expandedPath }
    ThemePreviewGalleryDialog(
        previews = previews,
        initialPage = initialPage,
        onDismiss = { expandedPath = null },
        itemKey = { it.path },
        caption = { galleryPreviewLabel(it) },
    ) { preview ->
        val file = rememberStorePreview(theme, preview, catalog, retry)?.file
        StorePreviewImage(file, galleryPreviewLabel(preview), showImageRim = false, fitPortraitFrame = true)
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
    val asset = rememberStorePreview(theme, preview, catalog, retry)
    val label = galleryPreviewLabel(preview)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemeCardSurface(
            Modifier.fillMaxWidth().aspectRatio(StorePreviewAspectRatio),
            onClick = onExpand.takeIf { asset != null },
        ) {
            Box(Modifier.fillMaxSize()) {
                StorePreviewImage(asset?.file, label, showImageRim = false, fitPortraitFrame = true)
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
    else -> preview.path.substringAfter('/').substringBeforeLast('.').replace('-', ' ')
}

@Composable
private fun DetailRow(title: String, icon: Int, onClick: (() -> Unit)? = null) {
    if (onClick == null) OtherInfoSettingsEntry(title = title, text = "", icon = icon)
    else OtherSettingsEntry(title = title, text = "", icon = icon, onClick = onClick)
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

@Composable
private fun ThemeReviewsDialog(theme: StoreTheme, reviews: StoreReviewSummary?, catalog: ThemeStoreCatalog, offline: Boolean, dismiss: () -> Unit) {
    val context = LocalContext.current
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
            StoreText(reviewLabel(reviews))
            reviews?.comments.orEmpty().forEach { review ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CreatorAvatar(null, review.author, catalog, 30.dp)
                        Box(Modifier.weight(1f, fill = false)) { StoreText("@${review.author.login}") }
                        DetailIcon(R.drawable.star_brilliant, Modifier.size(14.dp))
                        StoreText(stringResource(R.string.theme_store_review_rating, review.rating), secondary = true)
                    }
                    val hidden = "${theme.themeId}:reviews:${theme.revision}" in pending ||
                        "${theme.themeId}:review:${theme.revision}:${review.author.githubId}" in pending
                    SettingsDescription(if (hidden) stringResource(R.string.theme_store_report_message_hidden) else review.comment)
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
