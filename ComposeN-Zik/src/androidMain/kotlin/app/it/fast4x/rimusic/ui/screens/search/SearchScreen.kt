package app.it.fast4x.rimusic.ui.screens.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.compose.persist.PersistMapCleanup
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.n_zik.android.components.VoiceSearchUtils
import app.it.fast4x.rimusic.utils.VoiceSearchState
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.n_zik.android.components.VoiceSearchOverlay
import app.n_zik.android.components.ui.screens.find.MusicAudioSearchOverlay
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
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
            val decorationBox: @Composable (@Composable () -> Unit) -> Unit = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .padding(start = 2.dp)
                ) {
                    IconButton(
                        onClick = {},
                        icon = R.drawable.search,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(25.dp)
                    )
                }
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .padding(start = 30.dp, end = 100.dp)
                ) {
                    AnimatedVisibility(
                        visible = textFieldValue.text.isEmpty(),
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        modifier = Modifier
                            .align(Alignment.Center)
                    ) {
                        BasicText(
                            text = stringResource(R.string.search),
                            maxLines = 1,
                            style = typography().l,
                        )
                    }
                    innerTextField()
                }
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .padding(end = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
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
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            icon = R.drawable.microphone,
                            color = if (isListening) colorPalette().text else colorPalette().favoritesIcon,
                            modifier = Modifier
                                .size(25.dp)
                        )
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                isFindActive = true
                            },
                            icon = R.drawable.sound_effect,
                            color = if (isFindActive) colorPalette().text else colorPalette().favoritesIcon,
                            modifier = Modifier
                                .size(25.dp)
                        )

                        AnimatedVisibility(
                            visible = textFieldValue.text.isNotEmpty(),
                            enter = fadeIn(tween(220)) +
                                    expandHorizontally(tween(220)),
                            exit = fadeOut(tween(180)) +
                                    shrinkHorizontally(tween(180))
                        ) {
                            IconButton(
                                onClick = { onTextFieldValueChanged(TextFieldValue("")) },
                                icon = R.drawable.close,
                                color = colorPalette().favoritesIcon,
                                modifier = Modifier
                                    .size(25.dp)
                            )
                        }
                    }
                }
            }

            Skeleton(
                navController,
                tabIndex,
                onTabChanged,
                miniPlayer,
                navBarContent = { item ->
                    item(0, stringResource(R.string.online), R.drawable.globe)
                    item(1, stringResource(R.string.library), R.drawable.library)
                    item(2, stringResource(R.string.go_to_link), R.drawable.link)

                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> OnlineSearch(
                            navController = navController,
                            textFieldValue = textFieldValue,
                            onTextFieldValueChanged = onTextFieldValueChanged,
                            onSearch = onSearch,
                            decorationBox = decorationBox
                        )

                        1 -> LocalSongSearch(
                            navController = navController,
                            textFieldValue = textFieldValue,
                            onTextFieldValueChanged = onTextFieldValueChanged,
                            decorationBox = decorationBox,
                            onAction1 = { onTabChanged(0) },
                            onAction2 = { onTabChanged(1) },
                            onAction3 = { onTabChanged(2) },
                            onAction4 = {
                                //onGoToHome()
                                //pop()
                            },
                        )

                        2 -> GoToLink(
                            navController = navController,
                            textFieldValue = textFieldValue,
                            onTextFieldValueChanged = onTextFieldValueChanged,
                            decorationBox = decorationBox,
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



