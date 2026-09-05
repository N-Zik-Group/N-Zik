package app.it.fast4x.rimusic.ui.components.themed

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.kreate.android.me.knighthat.utils.Toaster

@SuppressLint("ComposableNaming")
@Composable
fun PlayNext(
    onClick: () -> Unit
): MenuIcon = object: MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.play_skip_forward
    override val messageId: Int = R.string.play_next
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun Enqueue(
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.enqueue
    override val messageId: Int = R.string.enqueue
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun Synchronize(
    onClick: () -> Unit
): MenuIcon = object: MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.sync
    override val messageId: Int = R.string.sync
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
        Toaster.done()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun ListenOnYouTube(
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.play
    override val messageId: Int = R.string.listen_on_youtube
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun ThumbnailPicker(
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.image
    override val messageId: Int = R.string.edit_thumbnail
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun Bookmark(
    isBookmarked: Boolean = true,
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive, Clickable {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = if (isBookmarked) R.drawable.bookmark else R.drawable.bookmark_outline
    override val messageId: Int = R.string.bookmark
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
    override fun onLongClick() {}
}

@SuppressLint("ComposableNaming")
@Composable
fun ResetThumbnail(
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.image
    override val messageId: Int = R.string.reset_thumbnail
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}


