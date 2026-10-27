package app.n_zik.android.extensions.nextvisualizer.views

import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import app.it.fast4x.rimusic.utils.hasPermission
import app.it.fast4x.rimusic.utils.isCompositionLaunched
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showvisthumbnailKey
import app.it.fast4x.rimusic.utils.blackBackgroundForVisThumbnailKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.n_zik.android.extensions.nextvisualizer.NextVisualizer

@UnstableApi
@Composable
fun NextVisualizer(
    isDisplayed: Boolean
) {
    val context = LocalContext.current
    val visualizerEnabled by rememberPreference(visualizerEnabledKey, false)
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    var blackBackgroundForVisThumbnail by rememberPreference(blackBackgroundForVisThumbnailKey, true)
    
    val binder = LocalPlayerServiceBinder.current
    var isPlaying by remember(binder) { mutableStateOf(binder?.player?.isPlaying == true) }

    DisposableEffect(binder) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }
        }
        binder?.player?.addListener(listener)
        onDispose {
            binder?.player?.removeListener(listener)
        }
    }

    if (visualizerEnabled) {
        val permission = Manifest.permission.RECORD_AUDIO

        var relaunchPermission by remember {
            mutableStateOf(false)
        }

        var hasPermission by remember(isCompositionLaunched()) {
            mutableStateOf(context.applicationContext.hasPermission(permission))
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {
                hasPermission = it
                PermissionUsageJournal.record(context, PermissionFeature.VISUALIZER, JournalPermission.MICROPHONE,
                    if (it) PermissionEventKind.GRANTED else PermissionEventKind.DENIED)
            }
        )

        if (!hasPermission) {
            LaunchedEffect(Unit, relaunchPermission) { PermissionUsageJournal.record(context, PermissionFeature.VISUALIZER, JournalPermission.MICROPHONE, PermissionEventKind.REQUESTED); launcher.launch(permission) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (!showvisthumbnail) Color.Transparent else Color.Black.copy(0.8f)),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    text = stringResource(R.string.require_mic_permission),
                    modifier = Modifier.fillMaxWidth(0.75f),
                    style = typography().xs.semiBold
                )
                /*
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryTextButton(
                    text = stringResource(R.string.grant_permission),
                    onClick = {
                        relaunchPermission = !relaunchPermission
                    }
                )
                 */
                Spacer(modifier = Modifier.height(20.dp))
                SecondaryTextButton(
                    text = stringResource(R.string.open_permission_settings),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                setData(Uri.fromParts("package", context.packageName, null))
                            }
                        )
                    }
                )
            }
        } else {
            AnimatedVisibility(
                visible = isDisplayed,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(500)),
                modifier = Modifier.fillMaxSize()
            ) {
                val backgroundColor by animateColorAsState(
                    targetValue = if (!showvisthumbnail || !blackBackgroundForVisThumbnail || !isPlaying) Color.Transparent else Color.Black.copy(0.6f),
                    animationSpec = tween(500),
                    label = stringResource(R.string.txt_visualizerbackground)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(thumbnailShape())
                        .background(backgroundColor)
                ) {
                    NextVisualizer()
                }
            }
        }
    }
}



