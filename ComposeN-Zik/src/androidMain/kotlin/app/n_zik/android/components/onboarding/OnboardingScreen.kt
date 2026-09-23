package app.n_zik.android.components.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.hasPermission
import app.it.fast4x.rimusic.utils.isIgnoringBatteryOptimizations
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.DataStoreUtils
import timber.log.Timber

/**
 * First-launch onboarding screen (RiPlay port): app icon, greeting, one card per
 * optional permission and an un-gated start button.
 *
 * Every permission stays optional — the start button only advances the flow, it never
 * checks the statuses (spec: onboarding + username choice). Card states refresh on
 * launcher results and on `ON_RESUME` (system settings are a common detour).
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }
    // System dialogs the user has already seen, so a denial without a remaining
    // rationale maps to PERMANENTLY_DENIED (feed for [permissionStatus]).
    // Persisted (comma-joined names) so the mapping survives activity recreation —
    // a rotation right after a denial must keep the "Settings" path, not degrade
    // to a re-askable "Grant".
    var requestedPermissions by remember {
        mutableStateOf(
            DataStoreUtils.getString(context, DataStoreUtils.KEY_ONBOARDING_PERMISSIONS_REQUESTED)
                .split(',')
                .filter { it.isNotBlank() }
                .toSet()
        )
    }

    LaunchedEffect(requestedPermissions) {
        DataStoreUtils.saveString(
            context,
            DataStoreUtils.KEY_ONBOARDING_PERMISSIONS_REQUESTED,
            requestedPermissions.joinToString(",")
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Granted or denied: the card state changes either way
        refreshTrigger++
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshTrigger++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun launchPermission(permission: String) {
        requestedPermissions = requestedPermissions + permission
        permissionLauncher.launch(permission)
    }

    fun openAppSettings() {
        runCatching {
            settingsLauncher.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            )
        }.onFailure {
            Timber.tag("Onboarding").e(it, "Failed to open the app details settings")
        }
    }

    fun openBatterySettings() {
        runCatching {
            settingsLauncher.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            )
        }.recoverCatching {
            Timber.tag("Onboarding").w("No battery-optimization request dialog, opening the settings page")
            settingsLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }.onFailure {
            Timber.tag("Onboarding").e(it, "Failed to open the battery optimization settings")
        }
    }

    // PERMANENTLY_DENIED cards open the settings page, every other state re-asks the dialog
    fun permissionAction(permission: String): () -> Unit = {
        if (permission in requestedPermissions &&
            !context.shouldShowRationale(permission)
        ) {
            openAppSettings()
        } else {
            launchPermission(permission)
        }
    }

    // stringResource is @Composable: the card texts are hoisted out of the remember block
    val notificationsTitle = stringResource(R.string.onboard_perm_notifications)
    val notificationsDesc = stringResource(R.string.onboard_perm_notifications_desc)
    val mediaTitle = stringResource(R.string.onboard_perm_media)
    val mediaDesc = stringResource(R.string.onboard_perm_media_desc)
    val bluetoothTitle = stringResource(R.string.onboard_perm_bluetooth)
    val bluetoothDesc = stringResource(R.string.onboard_perm_bluetooth_desc)
    val micTitle = stringResource(R.string.onboard_perm_mic)
    val micDesc = stringResource(R.string.onboard_perm_mic_desc)
    val batteryTitle = stringResource(R.string.onboard_perm_battery)
    val batteryDesc = stringResource(R.string.onboard_perm_battery_desc)

    val cards = remember(refreshTrigger) {
        buildList {
            val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    OnboardingItem(
                        id = "notifications",
                        title = notificationsTitle,
                        description = notificationsDesc,
                        icon = R.drawable.notification1,
                        status = permissionStatus(
                            isGranted = context.hasPermission(notificationPermission),
                            shouldShowRationale = context.shouldShowRationale(notificationPermission),
                            hasBeenRequested = notificationPermission in requestedPermissions
                        ),
                        onRequest = permissionAction(notificationPermission)
                    )
                )
            }

            val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            add(
                OnboardingItem(
                    id = "media",
                    title = mediaTitle,
                    description = mediaDesc,
                    icon = R.drawable.folder,
                    status = permissionStatus(
                        isGranted = context.hasPermission(mediaPermission),
                        shouldShowRationale = context.shouldShowRationale(mediaPermission),
                        hasBeenRequested = mediaPermission in requestedPermissions
                    ),
                    onRequest = permissionAction(mediaPermission)
                )
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val bluetoothPermission = Manifest.permission.BLUETOOTH_CONNECT
                add(
                    OnboardingItem(
                        id = "bluetooth",
                        title = bluetoothTitle,
                        description = bluetoothDesc,
                        icon = R.drawable.bluetooth,
                        status = permissionStatus(
                            isGranted = context.hasPermission(bluetoothPermission),
                            shouldShowRationale = context.shouldShowRationale(bluetoothPermission),
                            hasBeenRequested = bluetoothPermission in requestedPermissions
                        ),
                        onRequest = permissionAction(bluetoothPermission)
                    )
                )
            }

            val microphonePermission = Manifest.permission.RECORD_AUDIO
            add(
                OnboardingItem(
                    id = "microphone",
                    title = micTitle,
                    description = micDesc,
                    icon = R.drawable.microphone,
                    status = permissionStatus(
                        isGranted = context.hasPermission(microphonePermission),
                        shouldShowRationale = context.shouldShowRationale(microphonePermission),
                        hasBeenRequested = microphonePermission in requestedPermissions
                    ),
                    onRequest = permissionAction(microphonePermission)
                )
            )

            add(
                OnboardingItem(
                    id = "battery",
                    title = batteryTitle,
                    description = batteryDesc,
                    icon = R.drawable.battery_opti,
                    // No system dialog to track for battery optimization
                    status = if (context.isIgnoringBatteryOptimizations) PermissionStatus.GRANTED
                    else PermissionStatus.NOT_REQUESTED,
                    onRequest = { openBatterySettings() }
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            // The flow replaces AppNavigation here: keep the app's edge-to-edge
            // behavior — background full-bleed, content clear of the system bars
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            // drawable (PNG) instead of mipmap: the adaptive-icon XML wins on device and
            // Compose painterResource only supports vectors/rasters
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            tint = null,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboard_greeting),
            style = typography().m,
            color = colorPalette().text,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OnboardingSectionCard(
            title = stringResource(R.string.onboard_permissions_section)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = cards,
                key = { it.id },
                contentType = { "onboarding_permission" }
            ) { card ->
                OnboardingPermissionCard(card)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onComplete() },
            colors = ButtonDefaults.buttonColors(
                containerColor = colorPalette().accent,
                contentColor = colorPalette().textSecondary
            ),
            shape = uiRoundnessShape(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboard_start))
        }
    }
}

/**
 * Whether the platform would still show a rationale for [permission] (API M+).
 * LocalContext here is the hosting activity; when it is not an activity the answer
 * defaults to "yes" so a denial keeps the re-ask path (never jumps straight to settings).
 */
private fun Context.shouldShowRationale(permission: String): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) false
    else (this as? Activity)?.shouldShowRequestPermissionRationale(permission) ?: true

@Composable
private fun OnboardingSectionCard(title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(containerColor = colorPalette().background1)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = typography().s,
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().accent
            )
        }
    }
}

@Composable
private fun OnboardingPermissionCard(item: OnboardingItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(containerColor = colorPalette().background1)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colorPalette().accent.copy(alpha = 0.1f), shape = uiRoundnessShape()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(item.icon),
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = typography().s,
                    fontWeight = FontWeight.SemiBold,
                    color = colorPalette().text
                )
                Text(
                    text = item.description,
                    style = typography().xxs,
                    color = colorPalette().textSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OnboardingActionButton(status = item.status, onClick = item.onRequest)
        }
    }
}

@Composable
private fun OnboardingActionButton(status: PermissionStatus, onClick: () -> Unit) {
    when (status) {
        PermissionStatus.GRANTED -> Icon(
            painter = painterResource(R.drawable.checkmark),
            contentDescription = stringResource(R.string.onboard_permission_granted),
            tint = colorPalette().accent,
            modifier = Modifier.size(28.dp)
        )

        PermissionStatus.NOT_REQUESTED, PermissionStatus.DENIED -> OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colorPalette().text
            ),
            border = BorderStroke(
                1.dp,
                colorPalette().textSecondary
            )
        ) {
            Text(stringResource(R.string.onboard_permission_grant))
        }

        PermissionStatus.PERMANENTLY_DENIED -> TextButton(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(contentColor = colorPalette().text)
        ) {
            Text(stringResource(R.string.onboard_permission_settings))
        }
    }
}
