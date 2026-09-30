package app.n_zik.android.components.ui.screens.bridge

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.R
import app.n_zik.android.bridge.BridgeServerController
import app.n_zik.android.bridge.BridgeState
import app.n_zik.android.bridge.pairing.OfferResult
import app.n_zik.android.bridge.pairing.PairedDevice
import app.n_zik.android.bridge.pairing.PairingCode
import app.n_zik.android.bridge.pairing.PairingQrPayload
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import timber.log.Timber

private const val TAG = "BridgePairingUi"
private const val COUNTDOWN_TICK_MS = 1_000L

/** Result line shown under the pairing card. */
private sealed interface PairingMessage {
    data object Sending : PairingMessage
    data class OfferSent(val pcName: String) : PairingMessage
    data object OfferExpired : PairingMessage
    data object Unreachable : PairingMessage
    data object NotPairingQr : PairingMessage
    data object UnsupportedVersion : PairingMessage
    data object InvalidQr : PairingMessage
    data object CameraDenied : PairingMessage
}

/**
 * "Pair a PC" card (contract §4): the active code with its countdown, Regenerate, the QR
 * scan (camera permission asked at that moment only) with an explicit confirmation, and the
 * phone's address for the manual path. Closed, no code is active.
 */
@Composable
internal fun PairPcCard(running: BridgeState.Running, code: PairingCode?) {
    val context = LocalContext.current
    val palette = colorPalette()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<PairingMessage?>(null) }
    var pendingPayload by remember { mutableStateOf<PairingQrPayload?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        when (val parsed = PairingQrPayload.parse(contents)) {
            is PairingQrPayload.ParseResult.Valid -> {
                message = null
                pendingPayload = parsed.payload
            }
            PairingQrPayload.ParseResult.NotPairingQr -> message = PairingMessage.NotPairingQr
            PairingQrPayload.ParseResult.UnsupportedVersion -> message = PairingMessage.UnsupportedVersion
            PairingQrPayload.ParseResult.Invalid -> message = PairingMessage.InvalidQr
        }
    }
    val scanPrompt = stringResource(R.string.bridge_pair_scan_prompt)
    val launchScan = {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(scanPrompt)
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchScan() else message = PairingMessage.CameraDenied
    }
    val onScan: () -> Unit = {
        message = null
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) launchScan() else cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    // Pairing mode closed (e.g. after a successful pairing): the pending result line goes with it
    LaunchedEffect(code == null) {
        if (code == null) message = null
    }

    SettingsSectionCard(
        title = stringResource(R.string.bridge_pair_title),
        icon = R.drawable.link,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Pairing mode opens and folds back (Close, successful pairing) with an animation
                AnimatedContent(
                    targetState = code,
                    contentKey = { it != null },
                    transitionSpec = {
                        (fadeIn() + expandVertically(expandFrom = Alignment.Top)) togetherWith
                            (fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top))
                    },
                    label = "pairingMode",
                ) { shown ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (shown == null) {
                            PairingButton(
                                text = stringResource(R.string.bridge_pair_open),
                                containerColor = palette.accent,
                                contentColor = palette.textSecondary,
                                onClick = {
                                    message = null
                                    BridgeServerController.openPairing()
                                },
                            )
                        } else {
                            ActiveCode(shown)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.bridge_pair_manual_hint, running.ip, running.port),
                                style = typography().xs,
                                color = palette.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PairingButton(
                                text = stringResource(R.string.bridge_pair_scan),
                                containerColor = palette.accent,
                                contentColor = palette.textSecondary,
                                enabled = message != PairingMessage.Sending,
                                onClick = onScan,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                PairingButton(
                                    text = stringResource(R.string.bridge_pair_regenerate),
                                    containerColor = palette.background2,
                                    contentColor = palette.text,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        message = null
                                        BridgeServerController.regeneratePairingCode()
                                    },
                                )
                                PairingButton(
                                    text = stringResource(R.string.bridge_pair_close),
                                    containerColor = palette.background2,
                                    contentColor = palette.text,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        message = null
                                        BridgeServerController.closePairing()
                                    },
                                )
                            }
                        }
                    }
                }
                message?.let { current ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val isError = current !is PairingMessage.Sending && current !is PairingMessage.OfferSent
                    Text(
                        text = messageText(current, running, code),
                        style = typography().xs,
                        color = if (isError) palette.red else palette.text,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    )

    val payload = pendingPayload
    if (payload != null && code != null) {
        ConfirmationDialog(
            text = stringResource(R.string.bridge_pair_confirm, payload.deviceName, code.display),
            confirmText = stringResource(R.string.bridge_pair_confirm_action),
            onDismiss = { pendingPayload = null },
            onConfirm = {
                // Nothing is pushed without this explicit confirmation (contract §4.2)
                message = PairingMessage.Sending
                scope.launch {
                    message = when (BridgeServerController.sendOffer(payload)) {
                        // The PC may already have validated: pairing mode is then closed, the toast says it
                        is OfferResult.Accepted ->
                            if (BridgeServerController.pairingCode.value == null) null
                            else PairingMessage.OfferSent(payload.deviceName)
                        OfferResult.Expired -> PairingMessage.OfferExpired
                        is OfferResult.Refused, OfferResult.Unreachable -> PairingMessage.Unreachable
                    }
                }
            },
        )
    }
}

@Composable
private fun ActiveCode(code: PairingCode) {
    val palette = colorPalette()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(code) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= code.expiresAtMs) BridgeServerController.refreshPairingCode()
            delay(COUNTDOWN_TICK_MS)
        }
    }
    val remainingSeconds = ((code.expiresAtMs - now + 999) / 1_000).coerceAtLeast(0).toInt()
    Text(
        text = stringResource(R.string.bridge_pair_code_label),
        style = typography().xs,
        color = palette.textSecondary,
    )
    Text(
        text = code.display,
        style = typography().xxl.semiBold,
        color = palette.text,
    )
    Text(
        text = stringResource(R.string.bridge_pair_expires_in, remainingSeconds),
        style = typography().xs,
        color = palette.textSecondary,
    )
}

@Composable
private fun messageText(message: PairingMessage, running: BridgeState.Running, code: PairingCode?): String =
    when (message) {
        PairingMessage.Sending -> stringResource(R.string.bridge_pair_sending)
        is PairingMessage.OfferSent -> stringResource(R.string.bridge_pair_offer_sent, message.pcName)
        PairingMessage.OfferExpired -> stringResource(R.string.bridge_pair_offer_expired)
        PairingMessage.Unreachable ->
            stringResource(R.string.bridge_pair_unreachable, running.ip, running.port, code?.display.orEmpty())
        PairingMessage.NotPairingQr -> stringResource(R.string.bridge_pair_invalid_qr)
        PairingMessage.UnsupportedVersion -> stringResource(R.string.bridge_pair_unsupported_version)
        PairingMessage.InvalidQr -> stringResource(R.string.bridge_pair_invalid_payload)
        PairingMessage.CameraDenied -> stringResource(R.string.bridge_pair_camera_denied)
    }

@Immutable
internal data class PairedDevicesUi(val devices: List<PairedDevice>)

/** "Paired devices" card: name, pairing date and Revoke (with confirmation) per device. */
@Composable
internal fun PairedDevicesCard(ui: PairedDevicesUi) {
    val context = LocalContext.current
    val palette = colorPalette()
    val scope = rememberCoroutineScope()
    var toRevoke by remember { mutableStateOf<PairedDevice?>(null) }
    val dateFormat = remember(context) { DateFormat.getMediumDateFormat(context) }
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }

    SettingsSectionCard(
        title = stringResource(R.string.bridge_paired_devices),
        icon = R.drawable.devices,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (ui.devices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bridge_paired_devices_empty),
                        style = typography().xs,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ui.devices.forEach { device ->
                    val date = Date(device.pairedAtMs)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = device.deviceName, style = typography().s.semiBold, color = palette.text)
                            Text(
                                text = stringResource(
                                    R.string.bridge_paired_on,
                                    "${dateFormat.format(date)} ${timeFormat.format(date)}",
                                ),
                                style = typography().xs,
                                color = palette.textSecondary,
                            )
                        }
                        PairingButton(
                            text = stringResource(R.string.bridge_revoke),
                            containerColor = palette.red,
                            contentColor = palette.textSecondary,
                            fillWidth = false,
                            onClick = { toRevoke = device },
                        )
                    }
                }
            }
        },
    )

    toRevoke?.let { device ->
        ConfirmationDialog(
            text = stringResource(R.string.bridge_revoke_confirm, device.deviceName),
            confirmText = stringResource(R.string.bridge_revoke),
            onDismiss = { toRevoke = null },
            onConfirm = {
                scope.launch {
                    runCatching { BridgeServerController.revoke(context, device.deviceId) }
                        .onFailure { Timber.tag(TAG).e(it, "Could not revoke a paired device") }
                }
            },
        )
    }
}

@Composable
private fun PairingButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = uiRoundnessShape(),
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier).heightIn(min = 48.dp),
    ) {
        Text(text, style = typography().s.semiBold)
    }
}
