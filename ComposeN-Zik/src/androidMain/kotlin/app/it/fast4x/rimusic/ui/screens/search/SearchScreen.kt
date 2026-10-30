package app.it.fast4x.rimusic.ui.screens.search

import app.n_zik.android.components.navigation.rememberTopContentInsetProvider

import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.compose.persist.PersistMapCleanup
import app.n_zik.android.R
import app.n_zik.android.components.VoiceSearchUtils
import app.n_zik.android.components.search.SearchBarBubble
import app.n_zik.android.core.network.OfflineMode
import app.it.fast4x.rimusic.utils.VoiceSearchState
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.VoiceSearchOverlay
import app.n_zik.android.components.ui.screens.find.MusicAudioSearchOverlay
import app.kreate.android.me.knighthat.utils.Toaster
import androidx.core.content.ContextCompat

@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun SearchScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    initialTextInput: String,
    onSearch: (String) -> Unit,
    onViewPlaylist: (String) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val (tabIndex, onTabChanged) = rememberSaveable {
        mutableStateOf(0)
    }

    val (textFieldValue, onTextFieldValueChanged) = rememberSaveable(
        initialTextInput,
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(
            TextFieldValue(
                text = initialTextInput,
                selection = TextRange(initialTextInput.length)
            )
        )
    }

    val (isListening, setIsListening) = rememberSaveable { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCancelling by remember { mutableStateOf(false) }
    var isFindActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Full offline mode (spec-full-offline-mode): search collapses to the local
    // library tab â€” online search and Go-to-link are removed, not just failed.
    // The nav bar hides itself with a single item, so no grayed-out tabs exist.
    val offlineMode by OfflineMode.rememberEnabled()
    LaunchedEffect(offlineMode) {
        if (offlineMode && tabIndex != 1) onTabChanged(1)
    }

    val voiceSearchUtils = remember {
        VoiceSearchUtils(
            context = context,
            onResult = { result ->
                onTextFieldValueChanged(
                    TextFieldValue(
                        text = result,
                        selection = TextRange(result.length)
                    )
                )
                onSearch(result)
            },
            onPartialResult = { partial ->
                onTextFieldValueChanged(
                    TextFieldValue(
                        text = partial,
                        selection = TextRange(partial.length)
                    )
                )
            },
            onError = {
                errorMessage = context.getString(R.string.voice_search_no_match)
                Toaster.e(errorMessage ?: "")
            },
            onListeningStateChanged = { listening ->
                setIsListening(listening)
                if (!listening) {
                    isSpeaking = false
                }
            },
            onSpeechDetected = {
                isSpeaking = true
                errorMessage = null
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        PermissionUsageJournal.result(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO, isGranted)
        if (isGranted) {
            voiceSearchUtils.startListening()
        } else {
            Toaster.e(context.getString(R.string.voice_search_error_permissions))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceSearchUtils.destroy()
        }
    }

    PersistMapCleanup(tagPrefix = "search/")

    VoiceSearchState.isActive = (!isCancelling && (isListening || errorMessage != null)) || isFindActive

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full offline mode (spec-full-offline-mode): voice search and audio
            // search are online-only - the bubbles are removed, not grayed out
            // (the overlays behind them become unreachable). The music search
            // bubble is online-only as well: it opens the audio search overlay,
            // which is useless against the local library (the mic stays in both
            // tabs).
            val micBubble = SearchBarBubble(
                iconRes = R.drawable.microphone,
                contentDescriptionRes = R.string.voice_search,
                active = isListening,
                onClick = {
                    if (isListening) {
                        voiceSearchUtils.stopListening()
                    } else {
                        isCancelling = false
                        errorMessage = null
                        keyboardController?.hide()
                        if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            voiceSearchUtils.startListening()
                        } else {
                            PermissionUsageJournal.request(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO)
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
            )
            val musicSearchBubble = SearchBarBubble(
                iconRes = R.drawable.sound_effect,
                contentDescriptionRes = R.string.music_search,
                active = isFindActive,
                onClick = {
                    keyboardController?.hide()
                    isFindActive = true
                },
            )
            val onlineActionBubbles = if (offlineMode) emptyList() else listOf(micBubble, musicSearchBubble)
            val localActionBubbles = if (offlineMode) emptyList() else listOf(micBubble)

            val topContentInset = rememberTopContentInsetProvider(hasNavBar = !offlineMode && !app.it.fast4x.rimusic.utils.hideBarsInLandscapeMobile())

            Skeleton(
                navController,
                tabIndex,
                onTabChanged,
                miniPlayer,
                topInsetHandledByContent = true,
                topContentInsetProvider = topContentInset,
                navBarContent = { item ->
                    if (offlineMode)
                        item(1, stringResource(R.string.library), R.drawable.library)
                    else {
                        item(0, stringResource(R.string.online), R.drawable.globe)
                        item(1, stringResource(R.string.library), R.drawable.library)
                        item(2, stringResource(R.string.go_to_link), R.drawable.link)
                    }
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                    // Full offline mode: the content is always the local library tab,
                    // regardless of the raw index (the removed tabs must never render).
                    when (if (offlineMode) 1 else currentTabIndex) {
                        0 -> OnlineSearch(
                            topContentInsetProvider = topContentInset,
                            navController = navController,
                            textFieldValue = textFieldValue,
                            onTextFieldValueChanged = onTextFieldValueChanged,
                            onSearch = onSearch,
                            actionBubbles = onlineActionBubbles
                        )

                        1 -> LocalSongSearch(
                            topContentInsetProvider = topContentInset,
                            navController = navController,
                            textFieldValue = textFieldValue,
                            onTextFieldValueChanged = onTextFieldValueChanged,
                            actionBubbles = localActionBubbles,
                            onAction1 = { onTabChanged(0) },
                            onAction2 = { onTabChanged(1) },
                            onAction3 = { onTabChanged(2) },
                            onAction4 = {
                                //onGoToHome()
                                //pop()
                            },
                        )

                        2 -> GoToLink(
                            topContentInsetProvider = topContentInset,
                            navController = navController,
                            textFieldValue = textFieldValue,
                            onTextFieldValueChanged = onTextFieldValueChanged,
                            onAction1 = { onTabChanged(0) },
                            onAction2 = { onTabChanged(1) },
                            onAction3 = { onTabChanged(2) },
                            onAction4 = {
                                //onGoToHome()
                                //pop()
                            },
                        )


                    }
                }
            }
        }

        VoiceSearchOverlay(
            isVisible = !isCancelling && (isListening || errorMessage != null),
            recognizedText = textFieldValue.text,
            isSpeaking = isSpeaking,
            errorMessage = errorMessage,
            onRetry = {
                isCancelling = false
                errorMessage = null
                voiceSearchUtils.startListening()
            },
            onCancel = {
                isCancelling = true
                isSpeaking = false
                setIsListening(false)
                errorMessage = null
                voiceSearchUtils.stopListening()
                VoiceSearchState.isActive = false
            },
            modifier = Modifier.zIndex(1000f)
        )

        AnimatedVisibility(
            visible = isFindActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(1000f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f))
            ) {
                app.n_zik.android.components.ui.screens.find.MusicAudioSearchOverlay(
                    onDismiss = { isFindActive = false },
                    onOpenSearch = { query ->
                        onTextFieldValueChanged(TextFieldValue(query))
                        onSearch(query)
                        isFindActive = false
                    }
                )
            }
        }


    }
    }



