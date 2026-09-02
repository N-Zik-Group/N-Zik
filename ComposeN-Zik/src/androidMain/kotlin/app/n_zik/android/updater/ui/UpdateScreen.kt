@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.n_zik.android.updater.ui

import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.updater.services.Updater
import app.n_zik.android.updater.services.UpdateDownloadManager
import app.n_zik.android.updater.models.UpdaterConstants
import app.n_zik.android.updater.models.GithubRelease
import app.n_zik.android.updater.models.MajorUpdateConfig
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.utils.otherLanguageAppUpdateKey
import app.it.fast4x.rimusic.utils.rememberPreference
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import app.n_zik.android.core.network.client.NetworkClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.kreate.android.me.knighthat.utils.Repository
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.CheckUpdateState
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import app.it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import app.it.fast4x.rimusic.ui.components.themed.DropdownMenu
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.text.style.TextAlign
import app.n_zik.android.core.backup.BackupManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AlertDialog
import app.it.fast4x.rimusic.utils.lastUpdateCheckKey
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun UpdateScreen(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val downloadState by UpdateDownloadManager.downloadState.collectAsStateWithLifecycle()
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)

    // Update Preferences
    var checkUpdateState by rememberPreference(checkUpdateStateKey, CheckUpdateState.Enabled)
    val currentSuffix = Updater.githubRelease?.tagName?.let {
        Updater.extractVersionSuffix(it)
    } ?: Updater.extractVersionSuffix(BuildConfig.VERSION_NAME)
    var checkBetaUpdates by rememberPreference(checkBetaUpdatesKey, currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA)

    var otherLanguageApp by rememberPreference(otherLanguageAppUpdateKey, Languages.System)
    val appLang = Locale.getDefault().language
    val activeTranslateLang = remember(otherLanguageApp, appLang) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else Languages.entries.firstOrNull { it.code == appLang } ?: Languages.English
    }
    var isTranslationActive by rememberPreference("updateTranslationActive", appLang != "en")
    var showLanguageDialog by remember { mutableStateOf(false) }

    var showInstallWarningDialog by remember { mutableStateOf(false) }
    var apkPathToInstall by remember { mutableStateOf<String?>(null) }
    var isPreInstallBackupRunning by remember { mutableStateOf(false) }
    val preInstallBackupEnabled by rememberPreference(BackupManager.PREF_PRE_INSTALL, false)
    val preInstallBackupUri by rememberPreference(BackupManager.PREF_URI, "")
    val preInstallCoroutineScope = rememberCoroutineScope()

    // Handle back press during download - let download continue in background
    BackHandler(enabled = downloadState is UpdateDownloadManager.DownloadState.Downloading ||
            downloadState is UpdateDownloadManager.DownloadState.Starting) {
        Toaster.i(R.string.download_cancelled)
        UpdateDownloadManager.cancelDownload(context)
        navController.popBackStack()
    }

    // Cleanup state on exit - but NOT if download is active or completed
    DisposableEffect(Unit) {
        onDispose {
            val currentState = UpdateDownloadManager.downloadState.value
            if (currentState is UpdateDownloadManager.DownloadState.Failed) {
                UpdateDownloadManager.resetState()
            }
            // Don't reset Idle, Downloading, Starting or Completed - let download persist
        }
    }

    val newVersion = Updater.getDisplayVersion()
    val hasUpdate = Updater.githubRelease != null && Updater.isVersionNewer(Updater.githubRelease?.tagName ?: "", BuildConfig.VERSION_NAME)
    val currentVersion = BuildConfig.VERSION_NAME
    val currentBuildTypeLabel = stringResource(
        when (Updater.extractBuildType(currentVersion)) {
            UpdaterConstants.TYPE_BETA -> R.string.beta_title
            UpdaterConstants.TYPE_MINIFIED -> R.string.minified_title
            else -> R.string.stable_title
        }
    )
    val updateBuildTypeLabel = stringResource(
        when (Updater.extractBuildType(newVersion)) {
            UpdaterConstants.TYPE_BETA -> R.string.beta_title
            UpdaterConstants.TYPE_MINIFIED -> R.string.minified_title
            else -> R.string.stable_title
        }
    )
    val fileSize = try { Updater.build.readableSize } catch (_: Exception) { "" }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorPalette().background0,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Floating Action button at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorPalette().background0.copy(alpha = 0.95f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (val state = downloadState) {
                    is UpdateDownloadManager.DownloadState.Starting,
                    is UpdateDownloadManager.DownloadState.Downloading,
                    is UpdateDownloadManager.DownloadState.Completed -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                                            stringResource(R.string.downloading_update)
                                        } else {
                                            val extraInfo = if (fileSize.isNotEmpty()) "$currentBuildTypeLabel - $fileSize" else currentBuildTypeLabel
                                            stringResource(R.string.downloading_actual_version, currentVersion, extraInfo)
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
                                            text = "${(state.progress * 100).toInt()}%",
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
                                        Box(
                                            modifier = Modifier
                                                .clip(uiRoundnessShape())
                                                .clip(uiRoundnessShape()).clickable {
                                                    Toaster.i(R.string.download_cancelled)
                                                    UpdateDownloadManager.cancelDownload(context)
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            BasicText(
                                                text = stringResource(R.string.cancel),
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                maxLines = 1,
                                                style = typography().s.semiBold.copy(color = colorPalette().red)
                                            )
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
                                                text = stringResource(R.string.starting),
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
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(uiRoundnessShape()).clickable {
                                                val apkPath = state.filePath
                                                if (preInstallBackupEnabled && preInstallBackupUri.isNotEmpty() && !isPreInstallBackupRunning) {
                                                    apkPathToInstall = apkPath
                                                    isPreInstallBackupRunning = true
                                                    preInstallCoroutineScope.launch {
                                                        try {
                                                            val success = withContext(Dispatchers.IO) {
                                                                BackupManager.executePreInstallBackup(context)
                                                            }
                                                            isPreInstallBackupRunning = false
                                                            if (success) {
                                                                UpdateDownloadManager.installApk(context, apkPath)
                                                            } else {
                                                                Toaster.e(R.string.pre_install_backup_failed)
                                                            }
                                                        } catch (e: Exception) {
                                                            Timber.tag("UpdateScreen").e(e, "Pre-install backup failed with exception")
                                                            isPreInstallBackupRunning = false
                                                            Toaster.e(R.string.pre_install_backup_failed)
                                                        }
                                                    }
                                                } else if (!isPreInstallBackupRunning) {
                                                    apkPathToInstall = apkPath
                                                    showInstallWarningDialog = true
                                                }
                                            },
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
                                                text = stringResource(R.string.install),
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
                                if (hasUpdate) {
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(uiRoundnessShape()).clickable {
                                                UpdateDownloadManager.startDownload(
                                                    context,
                                                    Updater.build.downloadUrl,
                                                    newVersion
                                                )
                                            },
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
                                                        stringResource(R.string.retry)
                                                    else
                                                        stringResource(R.string.download),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.bold.copy(color = Color.White)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(uiRoundnessShape()).clickable {
                                                Toaster.i(R.string.checking_for_updates)
                                                Updater.checkForUpdate(true, checkBetaUpdates, showDialog = false)
                                            },
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
                                                    text = stringResource(R.string.check_update),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.semiBold.copy(color = colorPalette().accent)
                                                )
                                            }
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(uiRoundnessShape()).clickable {
                                            val rawVersion = if (hasUpdate) newVersion else currentVersion
                                            val cleanVersion = rawVersion.removePrefix("v")
                                            val tag = if (Updater.extractBuildType(cleanVersion) == UpdaterConstants.TYPE_BETA) {
                                                "v$cleanVersion"
                                            } else {
                                                "v${cleanVersion.substringBefore('-')}"
                                            }
                                            uriHandler.openUri("${Repository.REPO_URL}/releases/tag/$tag")
                                        },
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
                                                text = stringResource(R.string.github),
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {

                // Top Card (Up to date or Update available)
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                    Color(0xFF1A1A1A)
                                } else {
                                    colorPalette().background1
                                }
                            ),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Settings Menu positioned at TopEnd
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .clip(uiRoundnessShape())
                                            .clickable { showMenu = true }
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ellipsis_vertical),
                                            contentDescription = stringResource(R.string.menu),
                                            tint = colorPalette().text,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    val menu = DropdownMenu(
                                        expanded = showMenu,
                                        containerColor = colorPalette().background0.copy(0.90f),
                                        onDismissRequest = { showMenu = false }
                                    )

                                    val isMinified = Updater.extractVersionSuffix(BuildConfig.VERSION_NAME) == UpdaterConstants.SUFFIX_CHAR_MINIFIED
                                    if (!isMinified) {
                                        menu.add(
                                            DropdownMenu.Item(
                                                iconId = R.drawable.shield_checkmark,
                                                customText = "${stringResource(R.string.beta_updates)}: ${if (checkBetaUpdates) stringResource(R.string.on) else stringResource(R.string.off)}"
                                            ) {
                                                checkBetaUpdates = !checkBetaUpdates
                                            }
                                        )
                                    }
                                    val stateStr = when(checkUpdateState) {
                                        CheckUpdateState.Enabled -> stringResource(R.string.on)
                                        CheckUpdateState.Ask -> stringResource(R.string.ask)
                                        CheckUpdateState.Disabled -> stringResource(R.string.off)
                                    }
                                    menu.add(
                                        DropdownMenu.Item(
                                            iconId = R.drawable.update,
                                            customText = "${stringResource(R.string.enable_check_for_update)}: $stateStr"
                                        ) {
                                            checkUpdateState = when (checkUpdateState) {
                                                CheckUpdateState.Enabled -> CheckUpdateState.Ask
                                                CheckUpdateState.Ask -> CheckUpdateState.Disabled
                                                CheckUpdateState.Disabled -> CheckUpdateState.Enabled
                                            }
                                        }
                                    )
                                    val currentVersionStr = BuildConfig.VERSION_NAME
                                    val tagVersion = if (currentVersionStr.endsWith(UpdaterConstants.SUFFIX_BETA)) currentVersionStr else currentVersionStr.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
                                    val buildType = try { Updater.extractBuildType(currentVersionStr) } catch(e: Exception) { UpdaterConstants.TYPE_FULL }
                                    val apkName = "${BuildConfig.APP_NAME}-$buildType.apk"
                                    val currentDownloadUrl = "${Repository.RELEASE_DOWNLOAD_URL}$tagVersion/$apkName"
                                    
                                    menu.add(
                                        DropdownMenu.Item(
                                            iconId = R.drawable.translate,
                                            customText = "${stringResource(R.string.info_translation)}: \n${otherLanguageApp.text}"
                                        ) {
                                            showMenu = false
                                            showLanguageDialog = true
                                        }
                                    )
                                    val downloadText = "${stringResource(R.string.redownload_update)} ($currentVersionStr)"
                                    menu.add(
                                        DropdownMenu.Item(
                                            iconId = R.drawable.download,
                                            customText = downloadText
                                        ) {
                                            showMenu = false
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
                                                Toaster.w(R.string.update_not_available_yet)
                                            }
                                        }
                                    )
                                    menu.add(
                                        DropdownMenu.Item(
                                            R.drawable.trash,
                                            R.string.update_cache_cleared
                                        ) {
                                            showMenu = false
                                            UpdateDownloadManager.clearCache(context)
                                            Toaster.s(R.string.update_cache_cleared)
                                        }
                                    )
                                    menu.Draw()
                                }

                                // The translate button has been moved to the bottom right of the What's new card

                                if (showLanguageDialog) {
                                    ValueSelectorDialog(
                                        title = stringResource(R.string.info_translation),
                                        selectedValue = otherLanguageApp,
                                        onValueSelected = {
                                            otherLanguageApp = it
                                            isTranslationActive = it.translatorLanguage != Language.ENGLISH
                                            showLanguageDialog = false
                                        },
                                        valueText = { it.text },
                                        values = Languages.entries.toList(),
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
                                                .background(colorPalette().accent.copy(alpha = 0.1f)),
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
                                            text = if (isReinstalling) stringResource(R.string.reinstalling_update) else "$updateBuildTypeLabel ${stringResource(R.string.update_available)}",
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
                                                text = stringResource(R.string.update_file_size, fileSize),
                                                style = typography().xs.copy(color = colorPalette().textSecondary)
                                            )
                                        }
                                    } else if (Updater.githubRelease != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(uiRoundnessShape())
                                                .background(colorPalette().accent.copy(alpha = 0.1f)),
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
                                            text = stringResource(R.string.up_to_date),
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().l.bold.copy(color = colorPalette().text, textAlign = TextAlign.Center)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        BasicText(
                                            text = "${UpdaterConstants.PREFIX_VERSION}$currentVersion",
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().s.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center)
                                        )
                                        // No file size displayed when up to date
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(uiRoundnessShape())
                                                .background(colorPalette().accent.copy(alpha = 0.1f)),
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
                                        val stateStr = when(checkUpdateState) {
                                            CheckUpdateState.Enabled -> stringResource(R.string.auto_update_enabled)
                                            CheckUpdateState.Ask -> stringResource(R.string.auto_update_ask)
                                            CheckUpdateState.Disabled -> stringResource(R.string.auto_update_disabled)
                                        }
                                        BasicText(
                                            text = "${UpdaterConstants.PREFIX_VERSION}$currentVersion • $stateStr",
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().s.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center)
                                        )
                                    }
                                    val lastCheckTime by rememberPreference(lastUpdateCheckKey, 0L)
                                    val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                    val lastCheckStr = if (lastCheckTime > 0) sdf.format(Date(lastCheckTime)) else stringResource(R.string.never_checked)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicText(
                                        text = if (lastCheckTime > 0) stringResource(R.string.last_check, lastCheckStr) else stringResource(R.string.never_checked),
                                        style = typography().xxs.secondary.copy(color = colorPalette().textSecondary.copy(alpha = 0.7f))
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Changelog Section
                // Load cached changelog first, then fetch from network
                val isReinstalling = downloadState !is UpdateDownloadManager.DownloadState.Idle && UpdateDownloadManager.downloadingVersion == currentVersion
                LaunchedEffect(isReinstalling, hasUpdate) {
                    if (isReinstalling || !hasUpdate) {
                        // Load from cache first for instant display
                        Updater.loadCachedChangelog()
                        // Then fetch from network to get latest
                        Updater.fetchCurrentChangelog()
                    }
                }
                val currentChangelog = remember {
                    try {
                        appContext().resources
                            .openRawResource(R.raw.release_notes)
                            .bufferedReader(Charsets.UTF_8)
                            .readText()
                    } catch (e: Exception) { "" }
                }
                val changelogTextToDisplay = if (isReinstalling || !hasUpdate) {
                    Updater.currentChangelog?.takeIf { it.isNotBlank() }
                        ?: currentChangelog
                } else {
                    Updater.latestChangelog?.takeIf { it.isNotBlank() }
                        ?: Updater.githubRelease?.body?.takeIf { it.isNotBlank() }
                        ?: currentChangelog
                }

                val translator = remember { Translator(NetworkClientFactory.getTranslatorClient()) }
                var translatedText by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(changelogTextToDisplay, isTranslationActive, activeTranslateLang) {
                    if (isTranslationActive && changelogTextToDisplay.isNotBlank()) {
                        val destLanguage = activeTranslateLang.translatorLanguage
                        if (destLanguage != Language.ENGLISH) {
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    translator.translate(changelogTextToDisplay, destLanguage, Language.ENGLISH).translatedText
                                }
                                translatedText = res
                            } catch (e: Exception) {
                                Timber.tag("UpdateScreen").e(e, "Translation failed")
                            }
                        } else {
                            translatedText = changelogTextToDisplay
                        }
                    }
                }
                
                // We don't replace the display text here. We pass both to ChangelogCard.
                if (changelogTextToDisplay.isNotBlank() || Updater.isFetchingChangelog) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                            animationSpec = tween(800),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                    Color(0xFF1A1A1A)
                                } else {
                                    colorPalette().background1
                                }
                            ),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.sparkles),
                                        contentDescription = null,
                                        tint = colorPalette().accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicText(
                                        text = stringResource(R.string.whats_new_in, if (hasUpdate && !isReinstalling) newVersion else "${UpdaterConstants.PREFIX_VERSION}$currentVersion"),
                                        modifier = Modifier.weight(1f).padding(end = 8.dp).basicMarquee(iterations = Int.MAX_VALUE),
                                        maxLines = 1,
                                        style = typography().m.bold.copy(color = colorPalette().text)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(uiRoundnessShape())
                                            .combinedClickable(
                                                onClick = { isTranslationActive = !isTranslationActive },
                                                onLongClick = { showLanguageDialog = true }
                                            )
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.translate),
                                            contentDescription = stringResource(R.string.translate),
                                            tint = if (isTranslationActive) colorPalette().accent else colorPalette().textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                if (Updater.isFetchingChangelog) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = colorPalette().accent,
                                        )
                                    }
                                } else {
                                    ChangelogCard(
                                        rawText = changelogTextToDisplay,
                                        translatedText = if (isTranslationActive) translatedText else null,
                                        colorPaletteMode = colorPaletteMode
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        if (showInstallWarningDialog && apkPathToInstall != null) {
            ConfirmationDialog(
                text = stringResource(R.string.install_warning),
                onDismiss = { showInstallWarningDialog = false },
                onConfirm = {
                    val apkPath = apkPathToInstall
                    showInstallWarningDialog = false
                    if (apkPath != null) {
                        UpdateDownloadManager.installApk(context, apkPath)
                    }
                },
                confirmText = stringResource(R.string.install_yes),
                cancelText = stringResource(R.string.install_no_backup)
            )
        }

        if (isPreInstallBackupRunning) {
            BackHandler {
                isPreInstallBackupRunning = false
                Toaster.w(R.string.download_cancelled)
            }
            AlertDialog(
                onDismissRequest = { isPreInstallBackupRunning = false },
                confirmButton = {},
                title = {
                    BasicText(
                        text = stringResource(R.string.pre_install_backup_running),
                        style = typography().m.bold.copy(color = colorPalette().text)
                    )
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colorPalette().accent,
                        )
                        BasicText(
                            text = stringResource(R.string.pre_install_backup_running_description),
                            style = typography().s.copy(color = colorPalette().textSecondary)
                        )
                    }
                },
                containerColor = colorPalette().background0,
                shape = uiRoundnessShape()
            )
        }
    }
}

fun parseChangelogText(text: String): List<Pair<String, List<String>>> {
    val sections = mutableListOf<Pair<String, List<String>>>()
    var currentTitle: String? = null
    val currentChanges = mutableListOf<String>()

    fun packSection() {
        val title = currentTitle
        if (title != null) {
            sections.add(title to currentChanges.toList())
            currentChanges.clear()
        }
    }

    text.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.endsWith(":") || trimmed.endsWith(" :") -> {
                packSection()
                currentTitle = trimmed.substringBeforeLast(":").trim()
            }
            trimmed.startsWith("-") -> {
                val change = trimmed.removePrefix("-").trim()
                if (change.isNotBlank()) {
                    currentChanges.add(change)
                }
            }
        }
    }
    packSection()

    if (sections.isEmpty() && currentChanges.isNotEmpty()) {
        val otherTitle = appContext().getString(R.string.other)
        sections.add(otherTitle to currentChanges.toList())
    }

    return sections
}

@Composable
fun ChangelogCard(rawText: String, translatedText: String?, colorPaletteMode: ColorPaletteMode) {
    val rawSections = remember(rawText) { parseChangelogText(rawText) }
    
    val displaySections = remember(rawText, translatedText) {
        val parsedTranslated = translatedText?.let { parseChangelogText(it) }
        
        if (parsedTranslated != null && parsedTranslated.size == rawSections.size) {
            // Zip them: Triple(Raw Title, Display Title, Display Changes)
            rawSections.mapIndexed { index, rawSection ->
                Triple(rawSection.first, parsedTranslated[index].first, parsedTranslated[index].second)
            }
        } else {
            // Fallback: no translation or structure broke
            val fallbackSections = parsedTranslated ?: rawSections
            fallbackSections.map { Triple(it.first, it.first, it.second) }
        }
    }
    
    if (displaySections.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        displaySections.forEach { section ->
            var expanded by remember { mutableStateOf(true) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(uiRoundnessShape()).clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                shape = uiRoundnessShape(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(
                                when (section.first.lowercase()) {
                                    UpdaterConstants.CHANGELOG_ADDED -> R.drawable.add
                                    UpdaterConstants.CHANGELOG_CHANGED -> R.drawable.pencil
                                    UpdaterConstants.CHANGELOG_IMPROVED -> R.drawable.refresh_circle
                                    UpdaterConstants.CHANGELOG_FIXED -> R.drawable.alert
                                    UpdaterConstants.CHANGELOG_DEV -> R.drawable.sparkles
                                    else -> R.drawable.information
                                }
                            ),
                            contentDescription = null,
                            tint = colorPalette().accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = section.second, // Use Display Title
                            style = typography().s.semiBold.copy(color = colorPalette().text)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(if (expanded) R.drawable.chevron_up else R.drawable.chevron_down),
                            contentDescription = null,
                            tint = colorPalette().textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            section.third.forEach { change ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(6.dp)
                                            .clip(uiRoundnessShape())
                                            .background(
                                                when (section.first.lowercase()) {
                                                    UpdaterConstants.CHANGELOG_ADDED -> Color(0xFF4CAF50)
                                                    UpdaterConstants.CHANGELOG_CHANGED -> Color(0xFFFF9800)
                                                    UpdaterConstants.CHANGELOG_IMPROVED -> Color(0xFF2196F3)
                                                    UpdaterConstants.CHANGELOG_FIXED -> Color(0xFFF44336)
                                                    UpdaterConstants.CHANGELOG_DEV -> Color(0xFFFFEB3B)
                                                    else -> colorPalette().accent
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    BasicText(
                                        text = change, // Display Change (prefix already removed by new parser)
                                        style = typography().xs.copy(color = colorPalette().text),
                                        modifier = Modifier.weight(1f)
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




