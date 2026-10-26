package app.it.fast4x.rimusic.ui.screens.search

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.compose.persist.PersistMapCleanup
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.SearchType
import app.it.fast4x.rimusic.ui.components.Skeleton

@OptIn(ExperimentalAnimationApi::class)
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun SearchTypeScreen(
    navController: NavController,
    searchType: SearchType,
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val initialTextInput = ""

    val (tabIndex, onTabChanged) = remember {
        mutableIntStateOf(searchType.index)
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

    PersistMapCleanup(tagPrefix = "search/")

    Skeleton(
        navController,
        tabIndex,
        onTabChanged,
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
                    onSearch = { navController.navigate("${NavRoutes.searchResults.name}/$it") },
                    actionBubbles = emptyList()
                )

                1 -> LocalSongSearch(
                    navController = navController,
                    textFieldValue = textFieldValue,
                    onTextFieldValueChanged = onTextFieldValueChanged,
                    actionBubbles = emptyList(),
                    onAction1 = { onTabChanged(0) },
                    onAction2 = { onTabChanged(1) },
                    onAction3 = { onTabChanged(2) },
                    onAction4 = { navController.navigate(NavRoutes.home.name) }
                )

                2 -> GoToLink(
                    navController = navController,
                    textFieldValue = textFieldValue,
                    onTextFieldValueChanged = onTextFieldValueChanged,
                    onAction1 = { onTabChanged(0) },
                    onAction2 = { onTabChanged(1) },
                    onAction3 = { onTabChanged(2) },
                    onAction4 = { navController.navigate(NavRoutes.home.name) }
                )
            }
        }
    }
}



