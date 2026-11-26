@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.n_zik.android.updater.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import app.n_zik.android.components.settings.common.UPDATER_CHANGELOG_TARGET
import app.n_zik.android.components.settings.common.UPDATER_OPEN_OPTIONS_TARGET
import app.n_zik.android.components.settings.common.UpdaterSettingsOption
import app.n_zik.android.components.settings.common.SettingsBottomPanel
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.rebelonion.translator.Language
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassMenuTrigger
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassContentCaptureEnabled
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.menu.DropdownMenu
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.Languages
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.models.CheckUpdateState
import app.n_zik.android.updater.models.UpdateEndpoints
import app.n_zik.android.updater.services.UpdateDownloadManager
import app.n_zik.android.updater.services.Updater
import app.n_zik.android.utils.bold
import app.n_zik.android.utils.checkBetaUpdatesKey
import app.n_zik.android.utils.checkUpdateStateKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.contentTranslationChoices
import app.n_zik.android.utils.contentTranslationLanguage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.lastUpdateCheckKey
import app.n_zik.android.utils.otherLanguageAppUpdateKey
import app.n_zik.android.utils.percentLabel
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold

@Composable
fun UpdateScreen(navController: NavController, settingsTarget: String = "") {
    UpdaterGlassScope { UpdateScreenContent(navController, settingsTarget) }
}

@Composable
private fun UpdateScreenContent(navController: NavController, settingsTarget: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val downloadState = if (BuildConfig.IS_AUTOUPDATE) {
        UpdateDownloadManager.downloadState.collectAsStateWithLifecycle().value
    } else UpdateDownloadManager.DownloadState.Idle
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)

    var checkUpdateState by rememberPreference(checkUpdateStateKey, CheckUpdateState.Enabled)
    // Beta builds enable the beta channel by default.
    var checkBetaUpdates by rememberPreference(checkBetaUpdatesKey, Updater.isBetaFamily(BuildConfig.VERSION_NAME))

    // Ignore stale channel preferences for builds without auto-update.
    val (effectiveCheckState, effectiveCheckBeta) =
        Updater.effectiveUpdaterSettings(checkUpdateState, checkBetaUpdates, BuildConfig.IS_AUTOUPDATE)

    var otherLanguageApp by rememberPreference(otherLanguageAppUpdateKey, Languages.System)
    val appLocale = Locale.getDefault()
    val appLang = appLocale.language
    val activeTranslateLang = remember(otherLanguageApp, appLocale) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else contentTranslationLanguage(appLocale)
    }
    val changelogTranslationTarget = activeTranslateLang.changelogTranslationTarget
    var isTranslationActive by rememberPreference("updateTranslationActive", BuildConfig.IS_AUTOUPDATE && appLang != "en")
    var showLanguageDialog by remember { mutableStateOf(false) }

    var showInstallWarningDialog by remember { mutableStateOf(false) }
    var apkPathToInstall by remember { mutableStateOf<String?>(null) }
    var isPreInstallBackupRunning by remember { mutableStateOf(false) }
    val autoBackupEnabled = BackupManager.isEnabled(context.preferences)
    val preInstallBackupEnabled by rememberPreference(BackupManager.PREF_PRE_INSTALL, false)
    val preInstallBackupUri by rememberPreference(BackupManager.PREF_URI, "")
    val preInstallCoroutineScope = rememberCoroutineScope()

    // Preserve download results and any active download across screen exits.
    DisposableEffect(Unit) {
        onDispose {
            val currentState = UpdateDownloadManager.downloadState.value
            if (BuildConfig.IS_AUTOUPDATE && currentState is UpdateDownloadManager.DownloadState.Failed) {
                UpdateDownloadManager.resetState()
            }
        }
    }

    val currentVersion = BuildConfig.VERSION_NAME
    val release = Updater.githubRelease.takeIf { BuildConfig.IS_AUTOUPDATE }
    val newVersion = if (release != null) Updater.getDisplayVersion() else currentVersion
    val hasUpdate = release != null && Updater.isVersionNewer(release.tagName, currentVersion)
    val isFoss = BuildConfig.BUILD_TYPE.equals("foss", ignoreCase = true)
    val currentLabel = Updater.flavorLabel(currentVersion, isFoss) ?: Updater.FlavorLabel(R.string.updates_build_release_label)
    val currentBuildTypeLabel = if (currentLabel.literal != null) currentLabel.literal
    else stringResource(currentLabel.resId) + if (currentLabel.append32) " 32" else ""
    val updateLabel = Updater.flavorLabel(newVersion, isFoss) ?: Updater.FlavorLabel(R.string.updates_build_release_label)
    val updateBuildTypeLabel = if (updateLabel.literal != null) updateLabel.literal
    else stringResource(updateLabel.resId) + if (updateLabel.append32) " 32" else ""
    // Selection can clear after a failed re-check while the last APK remains cached.
    val fileSize = if (hasUpdate) try { Updater.build.readableSize } catch (_: Exception) { "" } else ""

    val footerBackdrop = rememberLayerBackdrop()
    val updaterGlass = rememberGlassSettings()
    val singleDownloadSurface = updaterGlass.usesSingleUpdaterDownloadSurface()
    val captureFooterBackdrop = updaterGlass.usesGlassMaterial(GlassArea.SettingsCards) &&
        glassContentCaptureEnabled(updaterGlass)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorPalette().background0,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            SettingsBottomPanel(
                backdrop = footerBackdrop.takeIf { captureFooterBackdrop },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rememberPageChromeContentPadding(topInsetProvider = { 0.dp }))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (val state = downloadState) {
                    is UpdateDownloadManager.DownloadState.Starting,
                    is UpdateDownloadManager.DownloadState.Downloading,
                    is UpdateDownloadManager.DownloadState.Completed -> {
                        UpdaterCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            drawSurface = !singleDownloadSurface,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                if (state is UpdateDownloadManager.DownloadState.Downloading) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val downloadStr = if (hasUpdate) {
                                            stringResource(R.string.updates_downloading_status)
                                        } else {
                                            val extraInfo = if (fileSize.isNotEmpty()) "$currentBuildTypeLabel - $fileSize" else currentBuildTypeLabel
                                            stringResource(R.string.updates_reinstalling_version_progress, currentVersion, extraInfo)
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            BasicText(
                                                text = downloadStr,
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                maxLines = 1,
                                                style = typography().s.bold.copy(color = colorPalette().text)
                                            )
                                        }
                                        BasicText(
                                            text = percentLabel((state.progress * 100).toInt()),
                                            modifier = Modifier.padding(start = 8.dp).width(40.dp),
                                            style = typography().s.copy(
                                                color = colorPalette().accent,
                                                textAlign = TextAlign.End
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearWavyProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(uiRoundnessShape()),
                                        color = colorPalette().accent,
                                        trackColor = colorPalette().background2
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        UpdaterIconAction(
                                            onClick = {
                                                Toaster.i(R.string.downloads_cancelled)
                                                UpdateDownloadManager.cancelDownload(context)
                                            },
                                        ) {
                                            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            BasicText(
                                                text = stringResource(R.string.common_action_cancel),
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                maxLines = 1,
                                                style = typography().s.semiBold.copy(color = colorPalette().red)
                                            )
                                            }
                                        }
                                    }
                                } else if (state is UpdateDownloadManager.DownloadState.Starting) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            BasicText(
                                                text = stringResource(R.string.updates_starting_status),
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                maxLines = 1,
                                                style = typography().s.bold.copy(color = colorPalette().text)
                                            )
                                        }
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.padding(start = 8.dp).size(20.dp),
                                            color = colorPalette().accent,
                                        )
                                    }
                                } else if (state is UpdateDownloadManager.DownloadState.Completed) {
                                    UpdaterActionCard(
                                        onClick = {
                                                val apkPath = state.filePath
                                                if (autoBackupEnabled && preInstallBackupEnabled && preInstallBackupUri.isNotEmpty() && !isPreInstallBackupRunning) {
                                                    apkPathToInstall = apkPath
                                                    isPreInstallBackupRunning = true
                                                    preInstallCoroutineScope.launch {
                                                        try {
                                                            val success = withContext(NzikDispatchers.DATA) {
                                                                BackupManager.executePreInstallBackup(context)
                                                            }
                                                            isPreInstallBackupRunning = false
                                                            if (success) {
                                                                UpdateDownloadManager.installApk(context, apkPath)
                                                            } else {
                                                                Toaster.e(R.string.backup_before_update_failed)
                                                            }
                                                        } catch (e: Exception) {
                                                            Timber.tag("UpdateScreen").e(e, "Pre-install backup failed with exception")
                                                            isPreInstallBackupRunning = false
                                                            Toaster.e(R.string.backup_before_update_failed)
                                                        }
                                                    }
                                                } else if (!isPreInstallBackupRunning) {
                                                    apkPathToInstall = apkPath
                                                    showInstallWarningDialog = true
                                                }

                                        },
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = colorPalette().accent),
                                        shape = uiRoundnessShape()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.checkmark),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BasicText(
                                                text = stringResource(R.string.updates_action_install),
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                maxLines = 1,
                                                style = typography().s.bold.copy(color = Color.White)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (state is UpdateDownloadManager.DownloadState.Failed) {
                                LaunchedEffect(state) {
                                    Toaster.e(state.error)
                                    UpdateDownloadManager.resetState()
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (BuildConfig.IS_AUTOUPDATE && hasUpdate) {
                                    UpdaterActionCard(
                                        onClick = {
                                                UpdateDownloadManager.startDownload(
                                                    context,
                                                    Updater.build.downloadUrl,
                                                    newVersion
                                                )

                                        },
                                        modifier = Modifier
                                            .weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = colorPalette().accent),
                                        shape = uiRoundnessShape()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(R.drawable.download),
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                BasicText(
                                                    text = if (state is UpdateDownloadManager.DownloadState.Failed)
                                                        stringResource(R.string.common_action_retry)
                                                    else
                                                        stringResource(R.string.downloads_title),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.bold.copy(color = Color.White)
                                                )
                                            }
                                        }
                                    }
                                } else if (BuildConfig.IS_AUTOUPDATE) {
                                    UpdaterActionCard(
                                        onClick = {
                                                Toaster.i(R.string.updates_checking_status)
                                                Updater.checkForUpdate(true, effectiveCheckBeta, showDialog = false)

                                        },
                                        modifier = Modifier
                                            .weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                                        shape = uiRoundnessShape(),
                                        border = BorderStroke(1.dp, colorPalette().accent.copy(alpha = 0.5f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(R.drawable.update),
                                                    contentDescription = null,
                                                    tint = colorPalette().accent,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                BasicText(
                                                    text = stringResource(R.string.updates_action_check),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.semiBold.copy(color = colorPalette().accent)
                                                )
                                            }
                                        }
                                    }
                                }

                                UpdaterActionCard(
                                        onClick = {
                                            // Use the actual tag because dev builds append a date.
                                            val tag = (if (hasUpdate) release?.tagName else null)
                                                ?: Updater.releaseTag(currentVersion)
                                            uriHandler.openUri("${UpdateEndpoints.REPO_URL}/releases/tag/$tag")

                                        },
                                    modifier = Modifier
                                        .weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                                    shape = uiRoundnessShape(),
                                    border = BorderStroke(1.dp, colorPalette().textSecondary.copy(alpha = 0.5f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.github_icon),
                                                contentDescription = null,
                                                tint = colorPalette().text,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BasicText(
                                                text = stringResource(R.string.common_brand_github),
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                maxLines = 1,
                                                style = typography().s.semiBold.copy(color = colorPalette().text)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()
            .then(if (captureFooterBackdrop) Modifier.layerBackdrop(footerBackdrop) else Modifier)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(rememberPageChromeContentPadding(base = paddingValues))
            ) {

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                        animationSpec = tween(600),
                        initialScale = 0.8f
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        UpdaterCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = surfaceColor(colorPalette().background1)
                            ),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                    val requestedOption = UpdaterSettingsOption.entries.firstOrNull { it.routeKey == settingsTarget }
                                    var showMenu by rememberSaveable(settingsTarget) {
                                        mutableStateOf(requestedOption != null || settingsTarget == UPDATER_OPEN_OPTIONS_TARGET)
                                    }
                                    var highlightRequestedOption by rememberSaveable(settingsTarget) { mutableStateOf(requestedOption != null) }
                                    val optionHighlight = DropdownMenu.Item.colors().copy(textColor = colorPalette().accent)
                                    fun colorsFor(option: UpdaterSettingsOption) = optionHighlight.takeIf {
                                        highlightRequestedOption && requestedOption == option
                                    }
                                    fun dismissOptions() {
                                        showMenu = false
                                        highlightRequestedOption = false
                                    }
                                    val menuTrigger = remember { GlassMenuTriggerState() }
                                    GlassMenuTrigger(menuTrigger, uiRoundnessShape()) {
                                        UpdaterIconAction(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ellipsis_vertical),
                                                contentDescription = stringResource(R.string.common_menu_label),
                                                tint = colorPalette().text,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    val menu = DropdownMenu(
                                        expanded = showMenu,
                                        trigger = menuTrigger,
                                        containerColor = surfaceColor(colorPalette().background0).copy(0.90f),
                                        onDismissRequest = ::dismissOptions,
                                    )

                                    // Only release and beta builds can receive beta updates.
                                    val showBetaToggle = BuildConfig.IS_AUTOUPDATE &&
                                        Updater.supportsBetaUpdatePreference(BuildConfig.VERSION_NAME)
                                    if (showBetaToggle) {
                                        menu.add(
                                            DropdownMenu.Item(
                                                iconId = R.drawable.shield_checkmark,
                                                customText = UpdaterSettingsOption.BetaUpdates.labelWithValue(
                                                    stringResource(R.string.updates_beta_enabled_label),
                                                    stringResource(if (effectiveCheckBeta) R.string.common_state_on else R.string.common_state_off),
                                                ),
                                                colors = colorsFor(UpdaterSettingsOption.BetaUpdates),
                                                enabled = BuildConfig.IS_AUTOUPDATE
                                            ) {
                                                highlightRequestedOption = false
                                                checkBetaUpdates = !checkBetaUpdates
                                            }
                                        )
                                    }
                                    if (BuildConfig.IS_AUTOUPDATE) {
                                        val stateStr = when(effectiveCheckState) {
                                            CheckUpdateState.Enabled -> stringResource(R.string.common_state_on)
                                            CheckUpdateState.Ask -> stringResource(R.string.updates_check_mode_ask)
                                            CheckUpdateState.Disabled -> stringResource(R.string.common_state_off)
                                        }
                                        menu.add(
                                            DropdownMenu.Item(
                                                iconId = R.drawable.update,
                                                customText = UpdaterSettingsOption.AutomaticCheck.labelWithValue(
                                                    stringResource(R.string.updates_automatic_check_title), stateStr,
                                                ),
                                                colors = colorsFor(UpdaterSettingsOption.AutomaticCheck),
                                            ) {
                                                highlightRequestedOption = false
                                                checkUpdateState = when (checkUpdateState) {
                                                    CheckUpdateState.Enabled -> CheckUpdateState.Ask
                                                    CheckUpdateState.Ask -> CheckUpdateState.Disabled
                                                    CheckUpdateState.Disabled -> CheckUpdateState.Enabled
                                                }
                                            }
                                        )
                                    }
                                    menu.add(
                                        DropdownMenu.Item(
                                            iconId = R.drawable.translate,
                                            customText = "${stringResource(R.string.common_translation_title)}: \n${otherLanguageApp.text}",
                                            colors = colorsFor(UpdaterSettingsOption.Translation),
                                        ) {
                                            dismissOptions()
                                            showLanguageDialog = true
                                        }
                                    )
                                    if (BuildConfig.IS_AUTOUPDATE) {
                                        val currentVersionStr = BuildConfig.VERSION_NAME
                                        val currentDownloadUrl = Updater.redownloadUrl(BuildConfig.APP_NAME, currentVersionStr)

                                        val downloadText = UpdaterSettingsOption.DownloadCurrentVersion.labelWithValue(
                                            stringResource(R.string.updates_action_download_current_version), currentVersionStr,
                                        )
                                        menu.add(
                                            DropdownMenu.Item(
                                                iconId = R.drawable.download,
                                                customText = downloadText,
                                                colors = colorsFor(UpdaterSettingsOption.DownloadCurrentVersion),
                                            ) {
                                                dismissOptions()
                                                try {
                                                    Timber.tag("UpdateScreen").d("Redownload URL: $currentDownloadUrl")
                                                    UpdateDownloadManager.startDownload(
                                                        context = context,
                                                        apkUrl = currentDownloadUrl,
                                                        version = currentVersionStr
                                                    )
                                                    Updater.fetchCurrentChangelog()
                                                } catch (e: Exception) {
                                                    Timber.tag("UpdateScreen").e(e, "Error fetching update")
                                                    Toaster.w(R.string.updates_not_available_yet)
                                                }
                                            }
                                        )
                                        menu.add(
                                            DropdownMenu.Item(
                                                R.drawable.trash,
                                                R.string.recovery_clear_cache,
                                                colors = colorsFor(UpdaterSettingsOption.ClearCache),
                                                enabled = downloadState !is UpdateDownloadManager.DownloadState.Starting &&
                                                    downloadState !is UpdateDownloadManager.DownloadState.Downloading,
                                            ) {
                                                dismissOptions()
                                                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                                    val cleared = clearUpdateCache(context)
                                                    withContext(NzikDispatchers.UI) {
                                                        if (cleared) Toaster.s(R.string.updates_cache_cleared)
                                                        else Toaster.e(R.string.updates_cache_clear_failed)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    menu.Draw()
                                }

                                DialogPresence(visible = showLanguageDialog) {
                                    ValueSelectorDialog(
                                        title = stringResource(R.string.common_translation_title),
                                        selectedValue = otherLanguageApp,
                                        onValueSelected = {
                                            otherLanguageApp = it
                                            isTranslationActive = it.translatorLanguage != Language.ENGLISH
                                            showLanguageDialog = false
                                        },
                                        valueText = { it.text },
                                        values = contentTranslationChoices,
                                        onDismiss = { showLanguageDialog = false }
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val isReinstalling = downloadState !is UpdateDownloadManager.DownloadState.Idle && UpdateDownloadManager.downloadingVersion == currentVersion
                                    if (hasUpdate || isReinstalling) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(uiRoundnessShape())
                                                .iconBadgeBackground(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(if (isReinstalling) R.drawable.download else R.drawable.arrow_up),
                                                contentDescription = null,
                                                tint = colorPalette().accent,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        BasicText(
                                            text = if (isReinstalling) stringResource(R.string.updates_reinstalling_status) else stringResource(R.string.updates_channel_available_format, updateBuildTypeLabel),
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().l.bold.copy(color = colorPalette().text, textAlign = TextAlign.Center)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        BasicText(
                                            text = if (isReinstalling) currentVersion else newVersion,
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().s.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center)
                                        )
                                        if (fileSize.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            BasicText(
                                                text = stringResource(R.string.updates_file_size, fileSize),
                                                style = typography().xs.copy(color = colorPalette().textSecondary)
                                            )
                                        }
                                    } else if (release != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(uiRoundnessShape())
                                                .iconBadgeBackground(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.checkmark),
                                                contentDescription = null,
                                                tint = colorPalette().accent,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        BasicText(
                                            text = stringResource(R.string.updates_up_to_date),
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().l.bold.copy(color = colorPalette().text, textAlign = TextAlign.Center)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        BasicText(
                                            text = stringResource(R.string.updates_current_version_format, currentVersion),
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().s.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(uiRoundnessShape())
                                                .iconBadgeBackground(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.information),
                                                contentDescription = null,
                                                tint = colorPalette().accent,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        BasicText(
                                            text = BuildConfig.APP_NAME,
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().l.bold.copy(color = colorPalette().text, textAlign = TextAlign.Center)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val stateStr = when(effectiveCheckState) {
                                            CheckUpdateState.Enabled -> stringResource(R.string.updates_check_mode_enabled_status)
                                            CheckUpdateState.Ask -> stringResource(R.string.updates_check_mode_ask_status)
                                            CheckUpdateState.Disabled -> stringResource(R.string.updates_check_mode_disabled_status)
                                        }
                                        BasicText(
                                            text = stringResource(
                                                R.string.updates_version_and_status,
                                                stringResource(R.string.updates_current_version_format, currentVersion),
                                                stateStr,
                                            ),
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().s.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center)
                                        )
                                    }
                                    if (BuildConfig.IS_AUTOUPDATE) {
                                        val lastCheckTime by rememberPreference(lastUpdateCheckKey, 0L)
                                        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                        val lastCheckStr = if (lastCheckTime > 0) sdf.format(Date(lastCheckTime)) else stringResource(R.string.updates_never_checked)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        BasicText(
                                            text = if (lastCheckTime > 0) stringResource(R.string.updates_last_check_format, lastCheckStr) else stringResource(R.string.updates_never_checked),
                                            style = typography().xxs.secondary.copy(color = colorPalette().textSecondary.copy(alpha = 0.7f))
                                        )
                                    } else {
                                        BasicText(
                                            text = stringResource(R.string.updates_manual_update_install_source_message),
                                            modifier = Modifier.padding(top = 8.dp),
                                            style = typography().xxs.secondary.copy(textAlign = TextAlign.Center),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val changelogRequester = remember { BringIntoViewRequester() }
                var changelogPlaced by remember { mutableStateOf(false) }
                var changelogTargetHandled by rememberSaveable(settingsTarget) { mutableStateOf(false) }
                val targetChangelog = settingsTarget == UPDATER_CHANGELOG_TARGET && !changelogTargetHandled
                LaunchedEffect(targetChangelog, changelogPlaced) {
                    if (targetChangelog && changelogPlaced) {
                        changelogRequester.bringIntoView()
                        changelogTargetHandled = true
                    }
                }
                UpdaterReleaseNotes(
                    headerModifier = if (targetChangelog) Modifier
                        .bringIntoViewRequester(changelogRequester)
                        .onGloballyPositioned { changelogPlaced = it.size.height > 0 }
                    else Modifier,
                    isReinstalling = downloadState !is UpdateDownloadManager.DownloadState.Idle &&
                        UpdateDownloadManager.downloadingVersion == currentVersion,
                    hasUpdate = hasUpdate,
                    currentVersion = currentVersion,
                    newVersion = newVersion,
                    colorPaletteMode = colorPaletteMode,
                    isTranslationActive = isTranslationActive,
                    changelogTranslationTarget = changelogTranslationTarget,
                    onToggleTranslation = { isTranslationActive = !isTranslationActive },
                    onChooseTranslationLanguage = { showLanguageDialog = true },
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        DialogPresence(target = apkPathToInstall.takeIf { BuildConfig.IS_AUTOUPDATE && showInstallWarningDialog }) { installationPath ->
            ConfirmationDialog(
                title = stringResource(R.string.updates_install_dialog_title),
                text = stringResource(R.string.updates_install_confirmation_message),
                onDismiss = { showInstallWarningDialog = false },
                onConfirm = {
                    val apkPath = installationPath
                    showInstallWarningDialog = false
                    if (apkPath != null) {
                        UpdateDownloadManager.installApk(context, apkPath)
                    }
                },
                confirmText = stringResource(R.string.updates_install_confirm),
                cancelText = stringResource(R.string.updates_install_cancel_for_backup)
            )
        }

        if (isPreInstallBackupRunning) {
            BackHandler {
                isPreInstallBackupRunning = false
                Toaster.w(R.string.downloads_cancelled)
            }
        }
        DialogPresence(visible = isPreInstallBackupRunning) {
            DialogFrame(
                onDismiss = { isPreInstallBackupRunning = false },
                title = stringResource(R.string.backup_before_update_running_title)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colorPalette().accent,
                    )
                    BasicText(
                        text = stringResource(R.string.backup_before_update_running_message),
                        style = typography().s.copy(color = colorPalette().textSecondary)
                    )
                }
            }
        }
    }
}

/** Clears update APKs on the caller's data dispatcher. */
internal suspend fun clearUpdateCache(context: Context): Boolean =
    UpdateDownloadManager.clearCache(context)
