package app.n_zik.android.components.extensions

import app.n_zik.android.utils.percentLabel
import android.content.Context
import android.os.Build
import android.text.format.Formatter
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import app.n_zik.android.utils.DOWNLOAD_INDICATOR_SIZE_NORMAL
import app.n_zik.android.utils.DOWNLOAD_INDICATOR_STROKE_WIDTH
import app.n_zik.android.utils.semiBold
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
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
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

private data class ModuleInfoRequest(
    val definition: ModuleDefinition,
    val marketplace: Boolean,
    val installedVersion: String? = null,
)

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
    // The Info source follows the tab where the entry was tapped.
    var infoTarget by remember { mutableStateOf<ModuleInfoRequest?>(null) }
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
    ) {
        DialogPresence(target = infoTarget) { request ->
            key(request) {
                val text by produceState<String?>(null) {
                    value = try {
                        withContext(NzikDispatchers.DATA) {
                            val installedVersion = request.installedVersion
                            when {
                                request.marketplace -> remoteModuleInfo(context, request.definition)
                                installedVersion != null && installedVersion != request.definition.version ->
                                    "${context.getString(R.string.modules_version)}: $installedVersion"
                                else -> FfmpegModule.fetchVersionInfo()
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.tag(TAG).e(error, "Could not fetch the module information")
                        context.getString(R.string.modules_load_failed)
                    }
                }
                ModuleInfoDialog(
                    title = stringResource(request.definition.displayNameRes),
                    content = text,
                    loadingText = if (request.marketplace && text == null) {
                        formatModuleInfo(context, request.definition, request.definition.downloadSizeBytes)
                    } else null,
                    onDismiss = { infoTarget = null },
                )
            }
        }

        DialogPresence(target = uninstallTarget) { uninstallPayload ->
            ConfirmationDialog(
                title = stringResource(R.string.modules_uninstall),
                confirmText = stringResource(R.string.modules_uninstall),
                text = stringResource(R.string.modules_uninstall_confirm),
                onDismiss = { uninstallTarget = null },
                onConfirm = {
                    val def = uninstallPayload
                    uninstallTarget = null
                    def?.let { scope.launch { manager.uninstall(it) } }
                },
            )
        }

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
                    text = { Text(stringResource(R.string.modules_tab_installed)) },
                    selectedContentColor = colorPalette().accent,
                    unselectedContentColor = colorPalette().textSecondary,
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.modules_tab_marketplace)) },
                    selectedContentColor = colorPalette().accent,
                    unselectedContentColor = colorPalette().textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> InstalledTab(
                    states = effectiveStates,
                    onCancelUpdate = manager::cancelDownload,
                    onUninstall = { def -> uninstallTarget = def },
                    onInfo = { def, version -> infoTarget = ModuleInfoRequest(def, marketplace = false, installedVersion = version) },
                )

                else -> MarketplaceTab(
                    states = effectiveStates,
                    scope = scope,
                    manager = manager,
                    onInfo = { def -> infoTarget = ModuleInfoRequest(def, marketplace = true) },
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
    onCancelUpdate: (String) -> Unit,
    onUninstall: (ModuleDefinition) -> Unit,
    onInfo: (ModuleDefinition, String) -> Unit,
) {
    val palette = colorPalette()
    val modules = ModuleCatalog.all.map { def ->
        key(def.id) { def to rememberModuleUi(def) }
    }
    val installed = modules.filter { (def, module) ->
        when (states[def.id]) {
            ModuleState.READY, is ModuleState.UPDATING -> true
            ModuleState.NOT_DOWNLOADED, is ModuleState.FAILED ->
                module.update != null && module.update != ModuleUpdateStatus.NotInstalled
            else -> false
        }
    }

    if (installed.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (modules.any { (def, module) -> def.id !in states || module.update == null }) {
                ItemTextPlaceholder(null, typography().xs, width = 0.44f)
            } else {
                BasicText(
                    text = stringResource(R.string.modules_nothing_installed),
                    style = typography().xs.copy(color = palette.textSecondary),
                )
            }
        }
        return
    }

    installed.forEach { (def, module) ->
        key(def.id) {
            InstalledModuleCard(def, module, states[def.id], onCancelUpdate, onUninstall, onInfo)
        }
    }
}

/** One installed module: the settings card with its version, size and actions. */
@Composable
private fun InstalledModuleCard(
    def: ModuleDefinition,
    module: ModuleUi,
    state: ModuleState?,
    onCancelUpdate: (String) -> Unit,
    onUninstall: (ModuleDefinition) -> Unit,
    onInfo: (ModuleDefinition, String) -> Unit,
) {
    val context = LocalContext.current
    val update = module.update as? ModuleUpdateStatus.UpdateAvailable
    val installedVersion = update?.installedVersion ?: def.version

    SettingsSectionCard(
        title = context.getString(def.displayNameRes),
        icon = R.drawable.extension,
        description = context.getString(def.descriptionRes),
        content = {
            ModuleInfoRow(stringResource(R.string.modules_version), installedVersion)
            ModuleInfoRow(stringResource(R.string.modules_size), module.sizeText)
            if (state is ModuleState.UPDATING) {
                DownloadingSettingsEntry(
                    title = stringResource(R.string.modules_updating),
                    progress = state.progress,
                    onCancel = { onCancelUpdate(def.id) },
                )
            } else if (update != null) {
                val failed = state as? ModuleState.FAILED
                if (failed != null) Text(failed.message)
                OtherSettingsEntry(
                    title = stringResource(if (failed != null) R.string.modules_retry else R.string.common_action_update),
                    text = stringResource(
                        R.string.modules_update_available_body,
                        update.installedVersion,
                        def.version,
                    ),
                    icon = R.drawable.update,
                    onClick = module.startDownload,
                )
            }
            OtherSettingsEntry(
                title = stringResource(R.string.modules_info),
                text = "",
                icon = R.drawable.information,
                onClick = { onInfo(def, installedVersion) },
            )
            if (state !is ModuleState.UPDATING) {
                OtherSettingsEntry(
                    title = stringResource(R.string.modules_uninstall),
                    text = "",
                    icon = R.drawable.trash,
                    onClick = { onUninstall(def) },
                )
            }
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
            ModuleInfoRow(stringResource(R.string.modules_version), def.version)
            ModuleInfoRow(stringResource(R.string.modules_size), formatBytes(context, remoteSize ?: def.downloadSizeBytes))

            OtherSettingsEntry(
                title = stringResource(R.string.modules_info),
                text = "",
                icon = R.drawable.information,
                onClick = { onInfo(def) },
            )

            if (!available) {
                DisabledSettingsEntry(
                    title = stringResource(R.string.modules_download),
                    text = stringResource(R.string.modules_not_available_arch),
                    icon = R.drawable.download,
                )
                return@SettingsSectionCard
            }
            when (state) {
                null -> OtherSettingsEntry(
                    title = stringResource(R.string.modules_download),
                    text = "",
                    icon = R.drawable.download,
                    shimmer = true,
                    onClick = {},
                )

                ModuleState.NOT_DOWNLOADED -> OtherSettingsEntry(
                    title = stringResource(R.string.modules_download),
                    text = "",
                    icon = R.drawable.download,
                    onClick = { scope.launch { manager.ensureReady(def) } },
                )

                is ModuleState.FAILED -> {
                    Text(state.message)
                    app.n_zik.android.components.ui.UiButton(
                        onClick = { scope.launch { manager.ensureReady(def) } },
                    ) {
                        Text(stringResource(R.string.modules_retry))
                    }
                }

                ModuleState.READY -> DisabledSettingsEntry(
                    title = stringResource(R.string.modules_already_installed),
                    text = "",
                    icon = R.drawable.download,
                )

                is ModuleState.DOWNLOADING -> DownloadingSettingsEntry(
                    title = stringResource(R.string.modules_downloading),
                    progress = state.progress,
                    onCancel = { manager.cancelDownload(def.id) },
                )

                is ModuleState.UPDATING -> DownloadingSettingsEntry(
                    title = stringResource(R.string.modules_updating),
                    progress = state.progress,
                    onCancel = { manager.cancelDownload(def.id) },
                )
            }
        },
    )
}

/** Live per-module update state + actions for the Installed card (off-Main collection, see [FfmpegModule] KDoc). */
class ModuleUi(
    val update: ModuleUpdateStatus?,
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

    // Byte progress does not change the installed files.
    val phase = states[def.id]?.javaClass
    val update by produceState<ModuleUpdateStatus?>(null, def, phase) {
        value = withContext(NzikDispatchers.DATA) { manager.checkForUpdate(def) }
    }
    val installedSize by produceState<Long?>(null, def, phase) {
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
 * The download icon shows live progress and the cancel button stops the transfer.
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
            .clip(uiRoundnessShape()),
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
                        text = percentLabel((progress * 100).toInt()),
                        style = typography().xs.copy(color = palette.textSecondary),
                    )
                }
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_action_cancel))
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
private suspend fun remoteModuleInfo(context: Context, def: ModuleDefinition): String =
    formatModuleInfo(context, def, ModuleRemoteProbe.remoteSizeBytes(def.downloadUrl) ?: def.downloadSizeBytes)

private fun formatModuleInfo(context: Context, def: ModuleDefinition, size: Long): String {
    val availableAbis = Build.SUPPORTED_ABIS.filter { it in def.abis }
    val abi = availableAbis.firstOrNull() ?: def.abis.first()
    return buildList {
        add("${context.getString(R.string.modules_version)}: ${def.version}")
        add("${context.getString(R.string.modules_size)}: ${formatBytes(context, size)}")
        add("${context.getString(R.string.modules_abi)}: ${def.abis.joinToString(", ")}")
        if (availableAbis.isEmpty()) {
            add(context.getString(R.string.modules_not_available_arch))
        }
        add(context.getString(R.string.modules_bundled_libraries))
        def.soFiles.getValue(abi).forEach { add("  $it") }
    }.joinToString("\n")
}
