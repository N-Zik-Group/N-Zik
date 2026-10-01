package app.n_zik.android.components.ui.screens.bridge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.bridge.BridgeServerController
import app.n_zik.android.bridge.BridgeState
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers

/**
 * "PC server" page, the "Manage server" screen: the connected PC (Disconnect), status of the
 * local PC bridge with its real address and a start/stop button, the battery optimization
 * warning, the "Pair a PC" card, the auto-stop settings and the paired devices. Same page
 * structure as the Listen Together screen (Skeleton scaffold, page header, settings-style card).
 */
@Composable
fun BridgeServerScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val state by BridgeServerController.state.collectAsStateWithLifecycle()
    val pairingCode by BridgeServerController.pairingCode.collectAsStateWithLifecycle()
    val activeDevice by BridgeServerController.activeDevice.collectAsStateWithLifecycle()
    val autoStopSettings by BridgeServerController.autoStopSettings.collectAsStateWithLifecycle()
    val pairedDevices by BridgeServerController.pairedDevices
        .collectAsStateWithLifecycle(initialValue = emptyList(), context = NzikDispatchers.DATA)
    LaunchedEffect(Unit) { BridgeServerController.loadDeviceStore(context) }
    LaunchedEffect(Unit) { BridgeServerController.loadAutoStopSettings(context) }
    LaunchedEffect(Unit) {
        BridgeServerController.pairedEvents.collect { deviceName ->
            Toaster.s(R.string.bridge_pair_success, deviceName)
        }
    }
    // Leaving the page leaves pairing mode: no code stays active (contract §4.1)
    DisposableEffect(Unit) {
        onDispose { BridgeServerController.closePairing() }
    }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    // Local network permission is asked at Start time only, never at app launch
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) BridgeServerController.start(context)
    }
    val onStart: () -> Unit = {
        val missing = BridgeServerController.missingPermission(context)
        if (missing != null) {
            permissionLauncher.launch(missing)
        } else {
            permissionDenied = false
            BridgeServerController.start(context)
        }
    }

    // Single nav item: no navigation bar is drawn, reserve only the mini-player room
    // (same computation as the Listen Together screen)
    val isFloatingNav = NavigationBarPosition.BottomFloating.isCurrent()
    val miniPlayerRoom = Dimensions.navBarBottomPadding(isFloatingNav) + if (isFloatingNav) 10.dp else 15.dp
    val bottomContentSpacer =
        if (LocalPlayerServiceBinder.current?.player?.currentMediaItem != null) {
            Dimensions.miniPlayerHeight + miniPlayerRoom
        } else {
            miniPlayerRoom
        }

    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.bridge_server), R.drawable.devices)
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomContentSpacer),
            ) {
                item(key = "header", contentType = "header") {
                    HeaderWithIcon(
                        title = stringResource(R.string.bridge_server),
                        iconId = R.drawable.devices,
                        enabled = false,
                        showIcon = true,
                        modifier = Modifier,
                        onClick = {},
                    )
                    SettingsDescription(
                        text = stringResource(R.string.bridge_server_description),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                val connected = activeDevice.takeIf { state is BridgeState.Running }
                if (connected != null) {
                    item(key = "connected", contentType = "card") {
                        ConnectedPcCard(connected)
                    }
                }
                item(key = "status", contentType = "card") {
                    BridgeStatusCard(
                        state = state,
                        permissionDenied = permissionDenied,
                        onStart = onStart,
                        onStop = { BridgeServerController.stop(context) },
                    )
                }
                item(key = "battery", contentType = "card") {
                    BatteryOptimizationWarning()
                }
                val running = state as? BridgeState.Running
                if (running != null) {
                    item(key = "pair", contentType = "card") {
                        PairPcCard(running = running, code = pairingCode)
                    }
                }
                item(key = "autoStop", contentType = "card") {
                    AutoStopCard(autoStopSettings)
                }
                item(key = "devices", contentType = "card") {
                    PairedDevicesCard(PairedDevicesUi(pairedDevices))
                }
            }
        }
    }
}

@Composable
private fun BridgeStatusCard(
    state: BridgeState,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val palette = colorPalette()
    val (label, color) = when (state) {
        is BridgeState.Running -> stringResource(R.string.bridge_server_state_running) to palette.accent
        BridgeState.Starting -> stringResource(R.string.bridge_server_starting) to palette.accent.copy(alpha = 0.6f)
        BridgeState.Stopping -> stringResource(R.string.bridge_server_state_stopping) to palette.accent.copy(alpha = 0.6f)
        BridgeState.NotOnWifi -> stringResource(R.string.bridge_server_stopped_wifi) to palette.red
        BridgeState.Failed -> stringResource(R.string.bridge_server_failed) to palette.red
        BridgeState.Stopped -> stringResource(R.string.bridge_server_stopped) to palette.textDisabled
    }
    val isActive = state is BridgeState.Running || state == BridgeState.Starting

    SettingsSectionCard(
        title = stringResource(R.string.bridge_server_status),
        icon = R.drawable.server,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = label, style = typography().s.semiBold, color = color)
                }
                if (state is BridgeState.Running) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bridge_server_running_address, state.ip, state.port),
                        style = typography().m.semiBold,
                        color = palette.text,
                    )
                    if (state.viaHotspot) {
                        Text(
                            text = stringResource(R.string.bridge_server_via_hotspot),
                            style = typography().xs,
                            color = palette.textSecondary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                BridgeActionButton(
                    text = stringResource(if (isActive) R.string.bridge_server_stop else R.string.bridge_server_start),
                    containerColor = if (isActive) palette.red else palette.accent,
                    contentColor = palette.textSecondary,
                    enabled = state != BridgeState.Stopping,
                    onClick = if (isActive) onStop else onStart,
                )
                if (permissionDenied && !isActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bridge_server_permission_denied),
                        style = typography().xs,
                        color = palette.red,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    )
}

@Composable
private fun BridgeActionButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = uiRoundnessShape(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = typography().s.semiBold)
    }
}
