package app.n_zik.android.components.extensions

import android.content.Context
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.components.glass.SampledMenuSheet
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_SIZE_NORMAL
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_STROKE_WIDTH
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.modules.FfmpegModule
import app.n_zik.android.modules.ModuleCatalog
import app.n_zik.android.modules.ModuleDefinition
import app.n_zik.android.modules.ModuleManager
import app.n_zik.android.modules.ModuleRemoteProbe
import app.n_zik.android.modules.ModuleState
import app.n_zik.android.modules.ModuleUpdateStatus
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Extensions sheet (spec-ffmpeg-extension-module): the app's extension store.
 * ListMenu.Menu chrome (drag handle + title + tabs) around settings cards: each
 * module renders as a [SettingsSectionCard] (the same card as the Misc settings
 * screen), with its actions as [OtherSettingsEntry] rows. Two tabs: Installed
 * (the modules actually installed on this device) and Marketplace (the
 * [ModuleCatalog] listing, download included). Opened from the hamburger
 * "Extensions" item ([app.n_zik.android.components.menu.header.ExtensionsMenuItem])
 * and the Misc settings card
 * ([app.n_zik.android.components.settings.ExtensionsSettingsCard]).
 *
 * @param showSheet whether the sheet is open
 * @param onDismissRequest called when the sheet is dismissed (swipe / outside tap)
 */
private const val TAG = "ExtensionsSheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = FfmpegModule.manager
    // Fire-and-forget (deliberately never cancelled): a download keeps running
    // after the sheet leaves the composition
    val scope = remember { NzikDispatchers.fireAndForget(NzikDispatchers.UI) }
    val states by manager.states.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)

    // Fallback for modules the manager never reported (fresh process with a
    // verified module on disk): computed once, off Main. Map.plus gives the
    // SECOND operand precedence, so the live states must come last: with
    // "states + initial" the stale sheet-open snapshot would shadow every
    // download/uninstall until the sheet is reopened.
    var initial by remember { mutableStateOf<Map<String, ModuleState>>(emptyMap()) }
    LaunchedEffect(manager) {
        initial = withContext(NzikDispatchers.DATA) {
            ModuleCatalog.all.associate { def -> def.id to manager.stateOf(def) }
        }
    }
    val effectiveStates: Map<String, ModuleState> = initial + states

    var selectedTab by remember { mutableIntStateOf(0) }
    var uninstallTarget by remember { mutableStateOf<ModuleDefinition?>(null) }
    var infoTarget by remember { mutableStateOf<ModuleDefinition?>(null) }
    // The Info source follows the tab the entry was tapped in, not the module
    // state: Marketplace is always the online catalog view (HEAD probe), the
    // Installed tab always the local module banner
    var infoFromMarketplace by remember { mutableStateOf(false) }
    var infoText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(infoTarget) {
        val def = infoTarget ?: return@LaunchedEffect
        infoText = if (infoFromMarketplace) {
            remoteModuleInfo(context, def)
        } else {
            runCatching { FfmpegModule.fetchVersionInfo() }
                .onFailure { Timber.tag(TAG).e(it, "Could not fetch the module version banner") }
                .getOrElse { context.getString(R.string.module_load_failed) }
        }
    }

    DialogPresence(target = infoTarget?.let { def -> infoText?.let { def to it } }) { (infoDef, text) ->
            ModuleInfoDialog(
                title = context.getString(infoDef.displayNameRes),
                content = text,
                onDismiss = {
                    infoTarget = null
                    infoText = null
                },
            )
    }

    DialogPresence(target = uninstallTarget) { uninstallPayload ->
        ConfirmationDialog(
            title = stringResource(R.string.module_uninstall),
            confirmText = stringResource(R.string.module_uninstall),
            text = stringResource(R.string.module_uninstall_confirm),
            onDismiss = { uninstallTarget = null },
            onConfirm = {
                val def = uninstallPayload
                uninstallTarget = null
                def?.let { scope.launch { manager.uninstall(it) } }
            },
        )
    }

    // Opens exactly like the Maintenance sheet: transparent container, top-only
    // shape, statusBarsPadding, skipPartiallyExpanded = false
    SampledMenuSheet(
        showSheet = showSheet,
        onDismissRequest = onDismissRequest,
        modifier = modifier.statusBarsPadding(),
        containerColor = Color.Transparent,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        partiallyExpanded = true,
        shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
            RoundedCornerShape(
                topStart = it.topStart,
                topEnd = it.topEnd,
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
        } ?: uiRoundnessShape(),
        dragHandle = {},
    ) {
        ListMenu.Menu(title = stringResource(R.string.modules_title)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {},
                edgePadding = 8.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = colorPalette().accent,
                        )
                    }
                },
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.module_tab_installed)) },
                    selectedContentColor = colorPalette().accent,
                    unselectedContentColor = colorPalette().textSecondary,
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.module_tab_marketplace)) },
                    selectedContentColor = colorPalette().accent,
                    unselectedContentColor = colorPalette().textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> InstalledTab(
                    states = effectiveStates,
                    onUninstall = { def -> uninstallTarget = def },
                    onInfo = { def ->
                        infoFromMarketplace = false
                        infoTarget = def
                    },
                )

                else -> MarketplaceTab(
                    states = effectiveStates,
                    scope = scope,
                    manager = manager,
                    onInfo = { def ->
                        infoFromMarketplace = true
                        infoTarget = def
                    },
                )
            }
        }
    }
}

/**
 * Installed tab: the modules actually installed on this device, each in its own
 * [SettingsSectionCard]. Not installed is empty: downloads live in the
 * Marketplace tab.
 */
@Composable
private fun InstalledTab(
    states: Map<String, ModuleState>,
    onUninstall: (ModuleDefinition) -> Unit,
    onInfo: (ModuleDefinition) -> Unit,
) {
    val palette = colorPalette()
    val installed = ModuleCatalog.all.filter { def -> states[def.id] == ModuleState.READY }

    if (installed.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = stringResource(R.string.module_nothing_installed),
                style = typography().xs.copy(color = palette.textSecondary),
            )
        }
        return
    }

    installed.forEach { def ->
        InstalledModuleCard(def, onUninstall, onInfo)
    }
}

/** One installed module: the settings card with its version, size and actions. */
@Composable
private fun InstalledModuleCard(
    def: ModuleDefinition,
    onUninstall: (ModuleDefinition) -> Unit,
    onInfo: (ModuleDefinition) -> Unit,
) {
    val context = LocalContext.current
    val module = rememberModuleUi(def)

    SettingsSectionCard(
        title = context.getString(def.displayNameRes),
        icon = R.drawable.extension,
        description = context.getString(def.descriptionRes),
        content = {
            ModuleInfoRow(stringResource(R.string.module_version), def.version)
            ModuleInfoRow(stringResource(R.string.module_size), module.sizeText)
            (module.update as? ModuleUpdateStatus.UpdateAvailable)?.let { updateStatus ->
                OtherSettingsEntry(
                    title = stringResource(R.string.module_update),
                    text = stringResource(
                        R.string.module_update_available_body,
                        updateStatus.installedVersion,
                        def.version,
                    ),
                    icon = R.drawable.update,
                    onClick = module.startDownload,
                )
            }
            OtherSettingsEntry(
                title = stringResource(R.string.module_info),
                text = "",
                icon = R.drawable.information,
                onClick = { onInfo(def) },
            )
            OtherSettingsEntry(
                title = stringResource(R.string.module_uninstall),
                text = "",
                icon = R.drawable.trash,
                onClick = { onUninstall(def) },
            )
        },
    )
}

/**
 * Marketplace tab: the [ModuleCatalog] listing, each module in its own
 * [SettingsSectionCard]: the Info entry on top, the action below (download when
 * not installed, retry on failure, a dimmed "already installed" placeholder
 * when installed - uninstalling lives in the Installed tab).
 */
@Composable
private fun MarketplaceTab(
    states: Map<String, ModuleState>,
    scope: CoroutineScope,
    manager: ModuleManager,
    onInfo: (ModuleDefinition) -> Unit,
) {
    ModuleCatalog.all.forEach { def ->
        MarketplaceModuleCard(def, states[def.id], scope, manager, onInfo)
    }
}

@Composable
private fun MarketplaceModuleCard(
    def: ModuleDefinition,
    state: ModuleState?,
    scope: CoroutineScope,
    manager: ModuleManager,
    onInfo: (ModuleDefinition) -> Unit,
) {
    val context = LocalContext.current
    val available = Build.SUPPORTED_ABIS.any { it in def.abis }
    // Real remote size from a lightweight HEAD probe (a few bytes), falling back
    // to the pinned catalog size when the URL is unreachable
    val remoteSize by produceState<Long?>(null) {
        value = ModuleRemoteProbe.remoteSizeBytes(def.downloadUrl)
    }

    SettingsSectionCard(
        title = context.getString(def.displayNameRes),
        icon = R.drawable.extension,
        description = context.getString(def.descriptionRes),
        content = {
            ModuleInfoRow(stringResource(R.string.module_version), def.version)
            ModuleInfoRow(stringResource(R.string.module_size), formatBytes(context, remoteSize ?: def.downloadSizeBytes))

            OtherSettingsEntry(
                title = stringResource(R.string.module_info),
                text = "",
                icon = R.drawable.information,
                onClick = { onInfo(def) },
            )

            when (state) {
                ModuleState.NOT_DOWNLOADED, null -> OtherSettingsEntry(
                    title = stringResource(R.string.module_download),
                    text = if (available) "" else stringResource(R.string.module_not_available_arch),
                    icon = R.drawable.download,
                    onClick = { scope.launch { manager.ensureReady(def) } },
                )

                is ModuleState.FAILED -> OtherSettingsEntry(
                    title = stringResource(R.string.module_retry),
                    text = state.message,
                    icon = R.drawable.refresh,
                    onClick = { scope.launch { manager.ensureReady(def) } },
                )

                ModuleState.READY -> DisabledSettingsEntry(
                    title = stringResource(R.string.module_already_installed),
                    text = "",
                    icon = R.drawable.download,
                )

                is ModuleState.DOWNLOADING -> DownloadingSettingsEntry(
                    title = stringResource(R.string.module_downloading),
                    progress = state.progress,
                    onCancel = { manager.cancelDownload(def.id) },
                )

                ModuleState.UPDATING -> DownloadingSettingsEntry(
                    title = stringResource(R.string.module_updating),
                    progress = null,
                    onCancel = { manager.cancelDownload(def.id) },
                )
            }
        },
    )
}

/** Live per-module update state + actions for the Installed card (off-Main collection, see [FfmpegModule] KDoc). */
class ModuleUi(
    val update: ModuleUpdateStatus,
    val sizeText: String,
    val startDownload: () -> Unit,
)

@Composable
fun rememberModuleUi(def: ModuleDefinition): ModuleUi {
    val context = LocalContext.current
    val manager = FfmpegModule.manager
    // Fire-and-forget (deliberately never cancelled): a download keeps running
    // after the sheet leaves the composition
    val scope = remember { NzikDispatchers.fireAndForget(NzikDispatchers.UI) }

    val states by manager.states.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)

    // Disk checks off Main (re-evaluated when the module state changes)
    val update by produceState<ModuleUpdateStatus>(ModuleUpdateStatus.NotInstalled, states) {
        value = withContext(NzikDispatchers.DATA) { manager.checkForUpdate(def) }
    }
    val installedSize by produceState<Long?>(null, states) {
        value = withContext(NzikDispatchers.DATA) { manager.installedSize(def) }
    }

    return ModuleUi(
        update = update,
        sizeText = formatBytes(context, installedSize ?: def.downloadSizeBytes),
        startDownload = { scope.launch { manager.ensureReady(def) } },
    )
}

/**
 * Module card entry while a download or update is running (SongItem pattern):
 * the download icon becomes a live progress ring, the label carries the
 * progress percent when known, and tapping the row cancels the transfer.
 */
@Composable
private fun DownloadingSettingsEntry(
    title: String,
    progress: Float?,
    onCancel: () -> Unit,
) {
    val palette = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clickable(onClick = onCancel),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .iconBadgeBackground(
                        color = palette.accent.copy(alpha = 0.1f),
                        shape = uiRoundnessShape(),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (progress != null) {
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        color = palette.accent,
                        trackColor = palette.textDisabled,
                        modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp),
                        stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                        trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                    )
                } else {
                    CircularWavyProgressIndicator(
                        color = palette.accent,
                        trackColor = palette.textDisabled,
                        modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp),
                        stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                        trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    style = typography().s.semiBold.copy(color = palette.text),
                )
                if (progress != null && progress > 0.01f) {
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = "${(progress * 100).toInt()}%",
                        style = typography().xs.copy(color = palette.textSecondary),
                    )
                }
            }
        }
    }
}

/**
 * Non-interactive card entry, dimmed, mirroring the [OtherSettingsEntry] layout
 * exactly (same paddings, icon box and text styles) without the click affordance.
 */
@Composable
private fun DisabledSettingsEntry(
    title: String,
    text: String,
    icon: Int,
) {
    val palette = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .iconBadgeBackground(
                        color = palette.accent.copy(alpha = 0.1f),
                        shape = uiRoundnessShape(),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    tint = palette.textDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    style = typography().s.semiBold.copy(color = palette.textDisabled),
                )
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = text,
                        style = typography().xs.copy(color = palette.textDisabled),
                    )
                }
            }
        }
    }
}

/** Label + value info row inside a module card. */
@Composable
private fun ModuleInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = label,
            style = typography().xs.copy(fontWeight = FontWeight.SemiBold, color = colorPalette().textSecondary),
            modifier = Modifier.padding(end = 8.dp),
        )
        BasicText(
            text = value,
            style = typography().xs.copy(fontWeight = FontWeight.SemiBold, color = colorPalette().text),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatBytes(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes)

/**
 * Catalog info for a module that is not ready yet (no download triggered): the
 * pinned size is replaced by the exact remote size from a lightweight HTTP HEAD
 * probe ([ModuleRemoteProbe]) when the URL is reachable, kept otherwise.
 */
private suspend fun remoteModuleInfo(context: Context, def: ModuleDefinition): String {
    val availableAbis = Build.SUPPORTED_ABIS.filter { it in def.abis }
    val abi = availableAbis.firstOrNull() ?: def.abis.first()
    val remoteSize = ModuleRemoteProbe.remoteSizeBytes(def.downloadUrl)
    return buildList {
        add("${context.getString(R.string.module_version)}: ${def.version}")
        add("${context.getString(R.string.module_size)}: ${formatBytes(context, remoteSize ?: def.downloadSizeBytes)}")
        add("${context.getString(R.string.module_abi)}: ${def.abis.joinToString(", ")}")
        if (availableAbis.isEmpty()) {
            add(context.getString(R.string.module_not_available_arch))
        }
        add(context.getString(R.string.module_bundled_libraries))
        def.soFiles.getValue(abi).forEach { add("  $it") }
    }.joinToString("\n")
}
