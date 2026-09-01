package app.it.fast4x.rimusic.ui.components.themed

import app.n_zik.android.core.database.*
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.ui.sliders.Slider
import app.n_zik.android.components.ui.sliders.SliderControl

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.resize
import app.n_zik.android.core.coil.thumbnail
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ValidationType
import app.n_zik.android.isBassBoostEnabled
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Info
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.ui.styling.shimmer
import app.it.fast4x.rimusic.utils.VinylSizeKey
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.bassboostLevelKey
import app.it.fast4x.rimusic.utils.blurDarkenFactorKey
import app.it.fast4x.rimusic.utils.blurStrengthKey
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.drawCircle
import app.it.fast4x.rimusic.utils.expandedplayerKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.getDeviceVolume
import app.it.fast4x.rimusic.utils.getLikeState
import app.it.fast4x.rimusic.utils.isExplicit
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.isValidIP
import app.it.fast4x.rimusic.utils.lyricsSizeKey
import app.it.fast4x.rimusic.utils.lyricsSizeLKey
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.playbackDeviceVolumeKey
import app.it.fast4x.rimusic.utils.playbackDurationKey
import app.it.fast4x.rimusic.utils.playbackPitchKey
import app.it.fast4x.rimusic.utils.playbackSpeedKey
import app.it.fast4x.rimusic.utils.playbackVolumeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.it.fast4x.rimusic.utils.volumeBoostLevelKey
import app.it.fast4x.rimusic.utils.removeYTSongFromPlaylist
import app.n_zik.android.uiRoundnessShape

import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.setDeviceVolume
import app.it.fast4x.rimusic.utils.setGlobalVolume
import app.it.fast4x.rimusic.utils.showCoverThumbnailAnimationKey
import app.n_zik.android.uiRoundnessShape

import app.it.fast4x.rimusic.utils.thumbnailFadeExKey
import app.it.fast4x.rimusic.utils.thumbnailFadeKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingLKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.thumbnailShape
import app.n_zik.android.artistThumbnailShape


@Composable
fun textFieldColors( colorPalette: ColorPalette, errorText: String ) =
    TextFieldDefaults.colors(
        focusedPlaceholderColor = colorPalette.textDisabled,
        unfocusedPlaceholderColor = colorPalette.textDisabled,
        cursorColor = colorPalette.text,
        focusedTextColor = colorPalette.text,
        unfocusedTextColor = colorPalette.text,
        focusedContainerColor = if ( errorText.isEmpty() ) colorPalette.background1 else colorPalette.red,
        unfocusedContainerColor = if ( errorText.isEmpty() ) colorPalette.background1 else colorPalette.red,
        focusedIndicatorColor = colorPalette.accent,
        unfocusedIndicatorColor = colorPalette.textDisabled
    )

@Composable
fun TextFieldDialog(
    hintText: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    doneText: String = stringResource(R.string.done),
    initialTextInput: String = "",
    singleLine: Boolean = true,
    maxLines: Int = 1,
    onCancel: () -> Unit = onDismiss,
    isTextInputValid: (String) -> Boolean = { it.isNotEmpty() }
) {
    val focusRequester = remember {
        FocusRequester()
    }

    var textFieldValue by rememberSaveable(initialTextInput, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = initialTextInput,
                selection = TextRange(initialTextInput.length)
            )
        )
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier

    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            textStyle = typography().xs.semiBold.center,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.None),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isTextInputValid(textFieldValue.text)) {
                        onDismiss()
                        onDone(textFieldValue.text)
                    }
                }
            ),
            cursorBrush = SolidColor(colorPalette().text),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = textFieldValue.text.isEmpty(),
                        enter = fadeIn(tween(100)),
                        exit = fadeOut(tween(100)),
                    ) {
                        BasicText(
                            text = hintText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = typography().xs.semiBold.secondary,
                        )
                    }

                    innerTextField()
                }
            },
            modifier = Modifier
                .padding(all = 16.dp)
                .weight(weight = 1f, fill = false)
                .focusRequester(focusRequester)

        )

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            DialogTextButton(
                text = cancelText,
                onClick = onCancel
            )

            DialogTextButton(
                primary = true,
                text = doneText,
                onClick = {
                    if (isTextInputValid(textFieldValue.text)) {
                        onDismiss()
                        onDone(textFieldValue.text)
                    }
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
}

@Composable
fun ConfirmationDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onCancel: () -> Unit = onDismiss,
    cancelBackgroundPrimary: Boolean = false,
    confirmBackgroundPrimary: Boolean = true
) {
    val colorPalette = colorPalette()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = colorPalette.background1
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Message text
                    BasicText(
                        text = text,
                        style = typography().s.copy(
                            color = colorPalette.text
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )

                    // Action buttons — Row with weight, no conflicting fillMaxWidth
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(colorPalette.background2, uiRoundnessShape())
                                .clip(uiRoundnessShape())
                                .clickable(onClick = onCancel)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = cancelText,
                                style = typography().xs
                                    .medium
                                    .copy(
                                        color = colorPalette.text,
                                        textAlign = TextAlign.Center
                                    )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(colorPalette.accent, uiRoundnessShape())
                                .clip(uiRoundnessShape())
                                .clickable {
                                    onConfirm()
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = confirmText,
                                style = typography().xs
                                    .medium
                                    .copy(
                                        color = colorPalette.onAccent,
                                        textAlign = TextAlign.Center
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
inline fun DefaultDialog(
    noinline onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    crossinline content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 600.dp)
                    .padding(all = 10.dp)
                    .background(
                        color = colorPalette().background1,
                        shape = uiRoundnessShape()
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                content = content
            )
        }
    }
}

@Composable
fun <T> ValueSelectorDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    title: String,
    titleSecondary: String? = null,
    selectedValue: T,
    values: List<T>,
    onValueSelected: (T) -> Unit,
    valueText: @Composable (T) -> String = { it.toString() }
) {
    val colorPalette = colorPalette()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = colorPalette.background1
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            BasicText(
                                text = title,
                                style = typography().l.semiBold.copy(
                                    color = colorPalette.text
                                )
                            )
                            if (titleSecondary != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicText(
                                    text = titleSecondary,
                                    style = typography().s.copy(
                                        color = colorPalette.textSecondary
                                    )
                                )
                            }
                        }
                    
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Values list
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .weight(1f, false)
                    ) {
                        values.forEach { value ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                modifier = Modifier
                                    .clip(uiRoundnessShape()).clickable(
                                        onClick = {
                                            onDismiss()
                                            onValueSelected(value)
                                        }
                                    )
                                    .padding(vertical = 0.dp, horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .clip(uiRoundnessShape())
                            ) {
                                RadioButton(
                                    selected = selectedValue == value,
                                    onClick = {
                                        onDismiss()
                                        onValueSelected(value)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colorPalette.accent,
                                        unselectedColor = colorPalette.textSecondary
                                    )
                                )

                                BasicText(
                                    text = valueText(value),
                                    style = typography().s.copy(
                                        color = colorPalette.text
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cancel button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorPalette.background2,
                            contentColor = colorPalette.text
                        ),
                        shape = uiRoundnessShape()
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = typography().s.semiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
inline fun SelectorDialog(
    noinline onDismiss: () -> Unit,
    title: String,
    values: List<Info>?,
    crossinline onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    showItemsIcon: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .padding(all = 10.dp)
                .background(color = colorPalette().background1, shape = uiRoundnessShape())
                .padding(vertical = 16.dp)
        ) {
            BasicText(
                text = title,
                style = typography().s.semiBold,
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 24.dp)
            )

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {

                values?.distinct()?.forEach { value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .clip(uiRoundnessShape()).clickable(
                                onClick = {
                                    onDismiss()
                                    onValueSelected(value.id)
                                }
                            )
                            .padding(vertical = 12.dp, horizontal = 24.dp)
                            .fillMaxWidth()
                    ) {
                        if (showItemsIcon)
                            IconButton(
                                onClick = {},
                                icon = R.drawable.playlist,
                                color = colorPalette().text,
                                modifier = Modifier
                                    .size(18.dp)
                            )

                        BasicText(
                            text = value.name ?: stringResource(R.string.not_selectable),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = typography().xs.medium
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 24.dp)
            ) {
                DialogTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
inline fun SelectorArtistsDialog(
    noinline onDismiss: () -> Unit,
    title: String,
    values: List<Info>?,
    crossinline onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    //showItemsIcon: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = modifier
                .requiredSize(if (isLandscape) (0.85 * screenHeight) else (0.85 * screenWidth))
                .clip(artistThumbnailShape())
                .background(color = colorPalette().background1)
        ) {
            if (values != null) {
                val pagerState = rememberPagerState(pageCount = { values.size })
                val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)

                Box {
                    HorizontalPager(state = pagerState) { idArtist ->
                        val browseId = values[idArtist].id
                        val artist by remember( browseId ) {
                            Database.artistTable.findById( browseId )
                        }.collectAsState( null, Dispatchers.IO )

                        LaunchedEffect(Unit) {
                            if (artist?.thumbnailUrl == null) {
                                withContext(Dispatchers.IO) {
                                    YtMusic.getArtistPage(browseId = browseId.removePrefix(MODIFIED_PREFIX))
                                           .onSuccess { currentArtistPage ->
                                               artist?.copy(
                                                   thumbnailUrl = currentArtistPage.artist.thumbnail?.url
                                               )?.let( Database.artistTable::update )
                                           }
                                }
                            }
                        }

                        Box {
                            ImageCacheFactory.SubcomposeImage(
                                thumbnailUrl = artist?.thumbnailUrl?.resize(1000, 1000),
                                contentDescription = "",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .requiredSize(if (isLandscape) (0.85 * screenHeight) else (0.85 * screenWidth))
                                    .clickable(
                                        onClick = {
                                            onDismiss()
                                            onValueSelected(browseId)
                                        }
                                    )
                                    .align(Alignment.Center)
                            )
                            if (artist?.isYoutubeArtist == true) {
                                Image(
                                    painter = painterResource(R.drawable.ytmusic),
                                    colorFilter = ColorFilter.tint(
                                        Color.Red.copy(0.75f).compositeOver(Color.White)
                                    ),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(all = 5.dp)
                                        .offset(10.dp,10.dp),
                                    contentDescription = stringResource(R.string.cd_background_image),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            values[idArtist].name?.let { it1 ->
                                BasicText(
                                    text = cleanPrefix(it1),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = typography().xs.medium,
                                    modifier = Modifier
                                        .padding(bottom = 20.dp)
                                        .align(Alignment.BottomCenter)
                                )
                                BasicText(
                                    text = cleanPrefix(it1),
                                    style = typography().xs.medium.merge(TextStyle(
                                        drawStyle = Stroke(width = 1.0f, join = StrokeJoin.Round),
                                        color = if (colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))) Color.White.copy(0.5f)
                                        else Color.Black
                                    )),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(bottom = 20.dp)
                                        .align(Alignment.BottomCenter)
                                )
                            }
                        }

                    }
                    Row(
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(values.size) { iteration ->
                            val color = if (pagerState.currentPage == iteration) colorPalette().text else colorPalette().text.copy(alpha = 0.5f)
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(10.dp)

                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
inline fun InputNumericDialog(
    noinline onDismiss: () -> Unit,
    title: String,
    value: String,
    valueMin: String,
    valueMax: String,
    placeholder: String,
    crossinline setValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val txtFieldError = remember { mutableStateOf("") }
    val txtField = remember { mutableStateOf(value) }
    val value_cannot_empty = stringResource(R.string.value_cannot_be_empty)
    val value_must_be_greater = stringResource(R.string.value_must_be_greater_than)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .padding(all = 10.dp)
                .background(color = colorPalette().background1, shape = uiRoundnessShape())
                .padding(vertical = 16.dp)
                .requiredHeight(190.dp)
        ) {
            BasicText(
                text = title,
                style = typography().s.semiBold,
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 24.dp)
            )

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                TextField(
                    modifier = Modifier
                        //.padding(horizontal = 30.dp)
                        .fillMaxWidth(0.7f),
                    /*
                    .border(
                        BorderStroke(
                            width = 1.dp,
                            color = if (txtFieldError.value.isEmpty()) colorPalette.textDisabled else colorPalette.red
                        ),

                        shape = thumbnailShape
                    ),
                     */
                    colors = textFieldColors( colorPalette(), txtFieldError.value ),
                    leadingIcon = {
/*
                        Image(
                            painter = painterResource(R.drawable.app_icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.background0),
                            modifier = Modifier
                                .width(30.dp)
                                .height(30.dp)
                                .clip(uiRoundnessShape()).clickable(
                                    indication = rememberRipple(bounded = false),
                                    interactionSource = remember { MutableInteractionSource() },
                                    enabled = true,
                                    onClick = { onDismiss() }
                                )
                        )

 */


                    },
                    placeholder = { Text(text = placeholder) },
                    value = txtField.value,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = {
                        txtField.value = it.take(10)
                    })
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                BasicText(
                    text = if (txtFieldError.value.isNotEmpty()) txtFieldError.value else "---",
                    style = typography().xs.medium,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 24.dp)
                )
            }


            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                DialogTextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        if (txtField.value.isEmpty()) {
                            txtFieldError.value = value_cannot_empty
                            return@DialogTextButton
                        }
                        if (txtField.value.isNotEmpty() && txtField.value.toInt() < valueMin.toInt()) {
                            txtFieldError.value = value_must_be_greater + valueMin
                            return@DialogTextButton
                        }
                        setValue(txtField.value)
                    }
                )

                DialogTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier
                )
            }

        }
    }

}



@Composable
inline fun StringListDialog(
    title: String,
    addTitle: String,
    addPlaceholder: String,
    removeTitle: String,
    conflictTitle: String,
    list: List<String>,
    crossinline add: (String) -> Unit,
    crossinline remove: (String) -> Unit,
    noinline onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showStringAddDialog by remember {
        mutableStateOf(false)
    }
    var showStringRemoveDialog by remember {
        mutableStateOf(false)
    }
    var removingItem by remember { mutableStateOf("") }
    var errorDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = uiRoundnessShape(),
            colors = CardDefaults.cardColors(
                containerColor = colorPalette().background1
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicText(
                        text = title,
                        style = typography().l.semiBold.copy(
                            color = colorPalette().text
                        )
                    )
                    Button(
                        onClick = { showStringAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorPalette().accent,
                            contentColor = colorPalette().textSecondary
                        ),
                        shape = uiRoundnessShape()
                    ) {
                        Text(
                            text = addTitle,
                            style = typography().s.semiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List items
                if (list.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .weight(1f, false),
                                                 verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        list.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(uiRoundnessShape())
                                    .clip(uiRoundnessShape()).clickable { }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                BasicText(
                                    text = item,
                                    style = typography().s.copy(
                                        color = colorPalette().text
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        removingItem = item
                                        showStringRemoveDialog = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.trash),
                                        contentDescription = stringResource(R.string.delete),
                                        tint = colorPalette().red,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = stringResource(R.string.no_items),
                            style = typography().s.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cancel button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().background2,
                        contentColor = colorPalette().text
                    ),
                    shape = uiRoundnessShape()
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = typography().s.semiBold
                    )
                }
            }
        }
    }

    if (showStringAddDialog) {
        SettingsInputDialog(
            title = addTitle,
            initialValue = "",
            placeholder = addPlaceholder,
            onDismiss = { showStringAddDialog = false },
            onSetValue = {
                if (it !in list) {
                    add(it)
                } else {
                    errorDialog = true
                }
            }
        ).apply {
            showDialog()
            Render()
        }
    }

    if (showStringRemoveDialog) {
        ConfirmationDialog(
            text = removeTitle,
            onDismiss = { showStringRemoveDialog = false },
            onConfirm = {
                remove(removingItem)
            }
        )
    }

    if (errorDialog) {
        ConfirmationDialog(
            text = conflictTitle,
            onDismiss = { errorDialog = false },
            onConfirm = { errorDialog = false },
            confirmText = stringResource(R.string.confirm)
        )
    }

}



@Composable
inline fun GenericDialog(
    modifier: Modifier = Modifier,
    noinline onDismiss: () -> Unit,
    title: String,
    textButton: String = stringResource(R.string.cancel),
    crossinline content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .padding(all = 48.dp)
                .background(color = colorPalette().background1, shape = uiRoundnessShape())
                .padding(vertical = 16.dp)
        ) {
            BasicText(
                text = title,
                style = typography().s.bold,
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 24.dp)
            )

            content()

            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 24.dp)
            ) {
                DialogTextButton(
                    text = textButton,
                    onClick = onDismiss,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
fun NewVersionDialog (
    updatedProductName: String,
    updatedVersionName: String,
    updatedVersionCode: Int,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    DefaultDialog(
        onDismiss = { onDismiss() },
        content = {
            BasicText(
                text = stringResource(R.string.update_available),
                style = typography().s.bold.copy(color = colorPalette().text),
            )
            Spacer(modifier = Modifier.height(10.dp))
            BasicText(
                text = String.format(stringResource(R.string.app_update_dialog_version),updatedVersionName),
                style = typography().xs.semiBold.copy(color = colorPalette().text),
            )
            Spacer(modifier = Modifier.height(10.dp))
            BasicText(
                text = stringResource(R.string.actions_you_can_do),
                style = typography().xs.semiBold.copy(color = colorPalette().textSecondary),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .fillMaxWidth()
            ) {
                BasicText(
                    text = stringResource(R.string.open_the_github_releases_web_page_and_download_latest_version),
                    style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Image(
                    painter = painterResource(R.drawable.globe),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette().shimmer),
                    modifier = Modifier
                        .size(30.dp)
                        .clip(uiRoundnessShape()).clickable {
                            onDismiss()
                            uriHandler.openUri("https://github.com/fast4x/RiMusic/releases/latest")
                        }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .fillMaxWidth()
            ) {
                BasicText(
                    text = stringResource(R.string.download_latest_version_from_github_you_will_find_the_file_in_the_notification_area_and_you_can_install_by_clicking_on_it),
                    style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Image(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette().shimmer),
                    modifier = Modifier
                        .size(30.dp)
                        .clip(uiRoundnessShape()).clickable {
                            onDismiss()
                            uriHandler.openUri("https://github.com/fast4x/RiMusic/releases/download/$updatedVersionName/app-foss-release.apk")
                        }
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .fillMaxWidth()
            ) {
                BasicText(
                    text = stringResource(R.string.f_droid_users_can_wait_for_the_update_info),
                    style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlurParamsDialog(
    onDismiss: () -> Unit,
    scaleValue: (Float) -> Unit,
    darkenFactorValue: (Float) -> Unit
) {
    val defaultStrength = 25f
    //val defaultStrength2 = 30f
    val defaultDarkenFactor = 0.2f
    var blurStrength  by rememberPreference(blurStrengthKey, defaultStrength)
    //var blurStrength2  by rememberPreference(blurStrength2Key, defaultStrength2)
    var blurDarkenFactor  by rememberPreference(blurDarkenFactorKey, defaultDarkenFactor)

    /*
    var isShowingLyrics by rememberSaveable {
        mutableStateOf(false)
    }
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)

     */

  //if (!isShowingLyrics || (isShowingLyrics && showlyricsthumbnail))
    DefaultDialog(
        onDismiss = {
            scaleValue(blurStrength)
            darkenFactorValue(blurDarkenFactor)
            onDismiss()
        }
    ) {

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    blurStrength = defaultStrength
                },
                icon = R.drawable.droplet,
                color = colorPalette().favoritesIcon,
                modifier = Modifier
                    .size(24.dp)
            )

            SliderControl(
                state = blurStrength,
                onSlide = { blurStrength = it },
                onSlideComplete = {},
                toDisplay = { "%.0f".format(it) },
                range = 0f..100f,
                stepSize = 1f
            )
        }
    }
}
    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    fun ThumbnailOffsetDialog(
        onDismiss: () -> Unit,
        spacingValue: (Float) -> Unit,
        spacingValueL: (Float) -> Unit,
        fadeValue: (Float) -> Unit,
        fadeValueEx: (Float) -> Unit,
        imageCoverSizeValue: (Float) -> Unit
    ) {
        val defaultFade = 5f
        val defaultSpacing = 0f
        val defaultImageCoverSize = 50f
        var thumbnailSpacing by rememberPreference(thumbnailSpacingKey, defaultSpacing)
        var thumbnailSpacingL by rememberPreference(thumbnailSpacingLKey, defaultSpacing)
        var thumbnailFade by rememberPreference(thumbnailFadeKey, defaultFade)
        var thumbnailFadeEx by rememberPreference(thumbnailFadeExKey, defaultFade)
        var fadingedge by rememberPreference(fadingedgeKey, false)
        var imageCoverSize by rememberPreference(VinylSizeKey, defaultImageCoverSize)
        val showCoverThumbnailAnimation by rememberPreference(showCoverThumbnailAnimationKey, false)
        val expandedplayer by rememberPreference(expandedplayerKey, false)
        DefaultDialog(
            onDismiss = {
                spacingValue(thumbnailSpacing)
                spacingValueL(thumbnailSpacingL)
                fadeValue(thumbnailFade)
                fadeValueEx(thumbnailFadeEx)
                imageCoverSizeValue(imageCoverSize)
                onDismiss()
            }
        ) {
            if (showCoverThumbnailAnimation) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            imageCoverSize = defaultImageCoverSize
                        },
                        icon = R.drawable.album,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                    )

                    SliderControl(
                        state = imageCoverSize,
                        onSlide = { imageCoverSize = it },
                        onSlideComplete = {
                            imageCoverSizeValue(imageCoverSize)
                        },
                        stepSize = 5f,
                        range = 50f..100f
                    )
                }
            }

            if(fadingedge && !isLandscape) {
                if (expandedplayer) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                thumbnailFadeEx = defaultFade
                            },
                            icon = R.drawable.droplet,
                            color = colorPalette().favoritesIcon,
                            modifier = Modifier
                                .size(24.dp)
                        )

                        SliderControl(
                            state = thumbnailFadeEx,
                            onSlide = { thumbnailFadeEx = it },
                            onSlideComplete = {},
                            toDisplay = { "%.0f".format(it) },
                            stepSize = 1f,
                            range = 0f..10f
                        )

                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                thumbnailFade = defaultFade
                            },
                            icon = R.drawable.droplet,
                            color = colorPalette().favoritesIcon,
                            modifier = Modifier
                                .size(24.dp)
                        )

                        SliderControl(
                            state = thumbnailFade,
                            onSlide = { thumbnailFade = it },
                            onSlideComplete = {},
                            toDisplay = { "%.0f".format(it) },
                            stepSize = 1f,
                            range = 0f..10f
                        )
                    }
                }
            }
            if (expandedplayer && !isLandscape) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            thumbnailSpacing = defaultSpacing
                        },
                        icon = R.drawable.burger,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                    )

                    SliderControl(
                        state = thumbnailSpacing,
                        onSlide = { thumbnailSpacing = it },
                        onSlideComplete = {},
                        toDisplay = { "%.0f".format(it) },
                        range = -50f..50f,
                        stepSize = 1f
                    )
                }
            }
            if (isLandscape) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            thumbnailSpacingL = defaultSpacing
                        },
                        icon = R.drawable.burger,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(90f)
                    )

                    SliderControl(
                        state = thumbnailSpacingL,
                        onSlide = { thumbnailSpacingL = it },
                        onSlideComplete = {},
                        toDisplay = { "%.0f".format(it) },
                        range = -50f..50f,
                        stepSize = 1f
                    )
                }
            }
        }
    }



@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AppearancePresetDialog(
    onDismiss: () -> Unit,
    onClick0: () -> Unit,
    onClick1: () -> Unit,
    onClick2: () -> Unit,
    onClick3: () -> Unit,
    onClick4: () -> Unit,
    onClick5: () -> Unit
) {
    val images = listOf(R.drawable.preset0,R.drawable.preset1,R.drawable.preset2,R.drawable.preset3,R.drawable.preset4,R.drawable.preset5)
    val pagerStateAppearance = rememberPagerState(pageCount = { images.size })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(color = colorPalette().background1)
        ){
            Box(
                modifier = Modifier
            ){
                HorizontalPager(
                    state = pagerStateAppearance,
                    pageSize = PageSize.Fill,
                    modifier = Modifier
                        .fillMaxSize()
                ) { index ->
                    Image(
                        painter = painterResource(images[index]),
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        colorFilter = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(bottom = 30.dp)
                        .padding(end = 15.dp)
                        .background(colorPalette().accent, CircleShape)
                        .align(Alignment.BottomEnd),
                ) {
                    IconButton(
                        icon = R.drawable.checkmark,
                        color = colorPalette().background0,
                        indication = ripple(false),
                        onClick = {
                            when (pagerStateAppearance.currentPage) {
                                0 -> onClick0()
                                1 -> onClick1()
                                2 -> onClick2()
                                3 -> onClick3()
                                4 -> onClick4()
                                else -> onClick5()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(30.dp)
                    )
                }
                Row(
                    Modifier
                        .height(20.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(images.size) { iteration ->
                        val lineWidth = animateDpAsState(
                            targetValue = if (pagerStateAppearance.currentPage == iteration) {15.dp} else {
                                if (iteration < pagerStateAppearance.currentPage) {5.dp} else {10.dp}
                            }, label = "width", animationSpec = tween(300, easing = EaseInOut)
                        )
                        val color = if (pagerStateAppearance.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(width = lineWidth.value, height = 5.dp)
                                .clip(uiRoundnessShape())
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LyricsSizeDialog(
    onDismiss: () -> Unit,
    sizeValue: (Float) -> Unit,
    sizeValueL: (Float) -> Unit,
) {
    var lyricsSize by rememberPreference(lyricsSizeKey, 20f)
    var lyricsSizeL by rememberPreference(lyricsSizeLKey, 20f)
    DefaultDialog(
        onDismiss = {
            sizeValue(lyricsSize)
            sizeValueL(lyricsSizeL)
            onDismiss()
        }
    ) {

        if (!isLandscape) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        lyricsSize = 20f
                    },
                    icon = R.drawable.text,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier
                        .size(24.dp)
                )

                SliderControl(
                    state = lyricsSize,
                    onSlide = { lyricsSize = it },
                    onSlideComplete = {},
                    toDisplay = { "%.0f".format(it) },
                    stepSize = 1f,
                    range = 18f..100f
                )
            }
        }
        if (isLandscape) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        lyricsSizeL = 20f
                    },
                    icon = R.drawable.text,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier
                        .size(24.dp)
                )

                SliderControl(
                    state = lyricsSizeL,
                    onSlide = { lyricsSizeL = it },
                    onSlideComplete = {},
                    toDisplay = { "%.0f".format(it) },
                    range = 18f..100f,
                    stepSize = 1f
                )
            }
        }
    }
}

@Composable
fun InProgressDialog(
    total : Int,
    done : Int,
    text : String,
    onDismiss: (() -> Unit)? = null,
) {
    val colorPalette = colorPalette()
    DefaultDialog(
        onDismiss = {if (onDismiss != null) {onDismiss()}},
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.3f else 0.8f)
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                textAlign = TextAlign.Center,
                fontSize = typography().l.bold.fontSize,
                fontWeight = typography().l.bold.fontWeight,
                color = colorPalette.text
            ),
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        val progress = if (total > 0) done.toFloat() / total.toFloat() else 0f
        if (total > 0) {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                color = colorPalette.accent,
                trackColor = colorPalette.background2
            )
        } else {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                color = colorPalette.accent,
                trackColor = colorPalette.background2
            )
        }
        if (total > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = "$done / $total",
                style = TextStyle(
                    textAlign = TextAlign.Center,
                    fontStyle = typography().xs.semiBold.fontStyle,
                    color = colorPalette.text
                ),
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDismiss != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette.background2,
                    contentColor = colorPalette.text
                ),
                shape = uiRoundnessShape(),
                modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally)
            ) {
                BasicText(
                    text = stringResource(R.string.cancel),
                    style = TextStyle(
                        textAlign = TextAlign.Center,
                        fontStyle = typography().s.semiBold.fontStyle,
                        color = colorPalette.text
                    )
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongMatchingDialog(
    songToRematch : Song,
    playlistId : Long,
    position : Int,
    playlist : Playlist?,
    onDismiss: (() -> Unit)
) {
    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.5f else 0.9f)
                .fillMaxHeight(if (isLandscape) 0.9f else 0.7f)
                .background(color = colorPalette().background1,shape = uiRoundnessShape())
        ) {
            fun filteredText(text : String): String{
                val filteredText = text
                    .lowercase()
                    .replace("(", " ")
                    .replace(")", " ")
                    .replace("-", " ")
                    .replace("lyrics", "")
                    .replace("vevo", "")
                    .replace(" hd", "")
                    .replace("official video", "")
                    .filter {it.isLetterOrDigit() || it.isWhitespace() || it == '\'' || it == ',' }
                    .replace(Regex("\\s+"), " ")
                return filteredText
            }
            var songsList by remember { mutableStateOf<List<Innertube.SongItem?>>(emptyList()) }
            var searchText by remember {mutableStateOf(filteredText("${cleanPrefix(songToRematch.cleanTitle())} ${songToRematch.cleanArtistsText()}"))}
            var startSearch by remember { mutableStateOf(false) }

            LaunchedEffect(Unit,startSearch) {
                runBlocking(Dispatchers.IO) {
                    val searchQuery = Innertube.searchPage(
                        body = SearchBody(
                            query = searchText,
                            params = Innertube.SearchFilter.Song.value
                        ),
                        fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                    )

                    songsList = searchQuery?.getOrNull()?.items ?: emptyList()
                    startSearch = false
                }
            }
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colorPalette().text, shape = uiRoundnessShape())
                    .padding(horizontal = 5.dp)
                    .padding(vertical = 10.dp)
            ) {
                Box {
                    ImageCacheFactory.AsyncImage(
                        thumbnailUrl = songToRematch.asMediaItem.mediaMetadata.artworkUri.thumbnail(
                            Dimensions.thumbnails.song.px / 2
                        )?.toString(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .clip(uiRoundnessShape())
                            .size(40.dp)
                    )
                    if (songToRematch.likedAt != null) {
                        HeaderIconButton(
                            onClick = {},
                            icon = getLikeState(songToRematch.asMediaItem.mediaId),
                            color = colorPalette().favoritesIcon,
                            iconSize = 12.dp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .absoluteOffset((-8).dp, 0.dp)
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    ) {
                        if (songToRematch.asMediaItem.isExplicit) {
                            IconButton(
                                icon = R.drawable.explicit,
                                color = colorPalette().text,
                                enabled = true,
                                onClick = {},
                                modifier = Modifier
                                    .size(18.dp)
                            )
                            Spacer(
                                modifier = Modifier
                                    .width(5.dp)
                            )
                        }
                        BasicText(
                            text = cleanPrefix(songToRematch.title),
                            style = typography().xs.semiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        BasicText(
                            text = songToRematch.cleanArtistsText(),
                            style = typography().s.semiBold.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        BasicText(
                            text = songToRematch.durationText ?: "",
                            style = typography().xs.secondary.medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(end = 5.dp)
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchText,
                    onValueChange = { it ->
                        searchText = it
                    },
                    singleLine = true,
                    colors = textFieldColors( colorPalette(), "" ),
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .weight(1f)
                )
                IconButton(
                    icon = R.drawable.search,
                    color = Color.Black,
                    onClick = {
                        startSearch = true
                    },
                    modifier = Modifier
                        .background(shape = uiRoundnessShape(),color = Color.White)
                        .padding(all = 4.dp)
                        .size(24.dp)
                        .align(Alignment.CenterVertically)
                        .weight(0.1f)
                )
            }
            if (songsList.isNotEmpty()) {
                LazyColumn {
                    itemsIndexed(songsList) { _, song ->
                        val artistNameString = song?.asMediaItem?.mediaMetadata?.artist?.toString() ?: ""
                        if (song != null) {
                            Row(horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                                    .padding(vertical = 10.dp)
                                    .clip(uiRoundnessShape()).clickable(onClick = {
                                        Database.asyncTransaction {
                                            if (isYouTubeSyncEnabled() && playlist?.isYoutubePlaylist == true && playlist.isEditable){
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    if (removeYTSongFromPlaylist(songToRematch.id, playlist.browseId ?: "", playlistId))
                                                        songPlaylistMapTable.deleteBySongId( songToRematch.id, playlistId )
                                                }
                                            } else
                                                songPlaylistMapTable.deleteBySongId( songToRematch.id, playlistId )

                                            val asMediaItem = song.asMediaItem

                                            playlist?.let { mapIgnore( it, asMediaItem ) }
                                            song.album
                                                ?.endpoint
                                                ?.browseId
                                                ?.let {
                                                    Album(
                                                        id = it,
                                                        title = asMediaItem.mediaMetadata.albumTitle.toString(),
                                                        thumbnailUrl = song.thumbnail?.url
                                                    )
                                                }
                                                ?.let { mapIgnore( it, asMediaItem ) }
                                            song.authors
                                                ?.mapNotNull {
                                                    if( it.name == null || it.endpoint?.browseId == null )
                                                        return@mapNotNull null

                                                    Artist(
                                                        id = it.endpoint!!.browseId!!,
                                                        name = it.name!!
                                                    )
                                                }
                                                ?.forEach { mapIgnore( it, asMediaItem ) }
                                            songTable.updateArtists( song.asMediaItem.mediaId, artistNameString )

                                            CoroutineScope(Dispatchers.IO).launch {
                                                if (isYouTubeSyncEnabled() && playlist?.isYoutubePlaylist == true && playlist.isEditable){
                                                    YtMusic.addToPlaylist(playlist.browseId ?: "", song.asMediaItem.mediaId)
                                                }
                                            }
                                        }
                                        onDismiss()
                                    }
                                    )
                            ) {
                                Box {
                                    ImageCacheFactory.AsyncImage(
                                        thumbnailUrl = song.asMediaItem.mediaMetadata.artworkUri.thumbnail(
                                            Dimensions.thumbnails.song.px / 2
                                        )?.toString(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .padding(end = 5.dp)
                                            .clip(uiRoundnessShape())
                                            .size(30.dp)
                                    )
                                    if (song.asSong.likedAt != null) {
                                        HeaderIconButton(
                                            onClick = {},
                                            icon = getLikeState(song.asMediaItem.mediaId),
                                            color = colorPalette().favoritesIcon,
                                            iconSize = 9.dp,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .absoluteOffset((-6.75).dp, 0.dp)
                                        )
                                    }
                                }
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .basicMarquee(iterations = Int.MAX_VALUE)
                                    ) {
                                        if (song.asMediaItem.isExplicit) {
                                            IconButton(
                                                icon = R.drawable.explicit,
                                                color = colorPalette().text,
                                                enabled = true,
                                                onClick = {},
                                                modifier = Modifier
                                                    .size(18.dp)
                                            )
                                            Spacer(
                                                modifier = Modifier
                                                    .width(5.dp)
                                            )
                                        }
                                        BasicText(
                                            text = cleanPrefix(song.title ?: ""),
                                            style = typography().xs.semiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    ) {
                                        BasicText(
                                            text = song.asSong.cleanArtistsText(),
                                            style = typography().xs.semiBold.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier
                                                .weight(1f)
                                                .basicMarquee(iterations = Int.MAX_VALUE)
                                        )
                                        BasicText(
                                            text = song.durationText ?: "",
                                            style = typography().xxs.secondary.medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .padding(end = 5.dp)
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
}


@Composable
fun <T> ValueSelectorDialogBody(
    onDismiss: () -> Unit,
    title: String,
    selectedValue: T?,
    values: List<T>,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    valueText: @Composable (T) -> String = { it.toString() }
) = Column(modifier = modifier) {
    val colorPalette = colorPalette()

    BasicText(
        text = title,
        style = typography().s.semiBold,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        values.forEach { value ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .clip(uiRoundnessShape()).clickable(
                        onClick = {
                            onDismiss()
                            onValueSelected(value)
                        }
                    )
                    .padding(vertical = 12.dp, horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                if (selectedValue == value) Canvas(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            color = colorPalette.accent,
                            shape = CircleShape
                        )
                ) {
                    drawCircle(
                        color = colorPalette.onAccent,
                        radius = 4.dp.toPx(),
                        center = size.center,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            blurRadius = 4.dp.toPx(),
                            offset = Offset(x = 0f, y = 1.dp.toPx())
                        )
                    )
                } else Spacer(
                    modifier = Modifier
                        .size(18.dp)
                        .border(
                            width = 1.dp,
                            color = colorPalette.textDisabled,
                            shape = CircleShape
                        )
                )

                BasicText(
                    text = valueText(value),
                    style = typography().xs.medium
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.End)
            .padding(end = 24.dp)
    ) {
        DialogTextButton(
            text = stringResource(R.string.cancel),
            onClick = onDismiss
        )
    }
}

