package app.it.fast4x.rimusic.ui.components.navigation.header

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.components.ui.header.DebugLogsBadge
import app.n_zik.android.components.ui.header.HeaderVersionBadge
import app.kreate.android.drawable.APP_ICON_IMAGE_BITMAP
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.uiRoundnessShape

private fun appIconClickAction(
    navController: NavController,
    countToReveal: MutableIntState,
    context: Context
) {
    countToReveal.intValue++

    val message: String =
        when (countToReveal.intValue) {
            10 -> {
                countToReveal.intValue = 0
                navController.navigate(NavRoutes.gamePacman.name)
                ""
            }

            3 -> context.getString(R.string.easter_egg_click_message)
            6 -> context.getString(R.string.easter_egg_keep_going)
            9 -> context.getString(R.string.easter_egg_number_one)
            else -> ""
        }
    if (message.isNotEmpty())
        Toaster.n(message, Toast.LENGTH_LONG)
}

private fun appIconLongClickAction(
    navController: NavController,
    context: Context
) {
    Toaster.n(context.getString(R.string.easter_egg_last), Toast.LENGTH_LONG)
    navController.navigate(NavRoutes.gameSnake.name)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppLogo(
    navController: NavController,
    context: Context
) {
    val countToReveal = remember { mutableIntStateOf(0) }
    val modifier = Modifier
        .clip(uiRoundnessShape())
        .combinedClickable(
            onClick = { appIconClickAction(navController, countToReveal, context) },
            onLongClick = { appIconLongClickAction(navController, context) }
        )

    Image(
        bitmap = APP_ICON_IMAGE_BITMAP,
        contentDescription = stringResource(R.string.cd_app_s_icon),
        modifier = modifier.size(36.dp)
    )
}

@Composable
private fun AppLogoText(navController: NavController) {
    val iconTextClick: () -> Unit = {
        if (NavRoutes.home.isNotHere(navController))
            navController.navigate(NavRoutes.home.name)
    }

    BasicText(
        text = "N-ZIK",
        style = TextStyle(
            fontSize = typography().xl.semiBold.fontSize,
            fontWeight = typography().xl.semiBold.fontWeight,
            fontFamily = typography().xl.semiBold.fontFamily,
            color = AppBar.contentColor()
        ),
        modifier = Modifier
            .clip(uiRoundnessShape())
            .clickable { iconTextClick() }
            .padding(horizontal = 8.dp)
    )
}

// START
@Composable
fun AppTitle(
    navController: NavController,
    context: Context
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLogo(navController, context)
        AppLogoText(navController)

        // Version badge (n_zik): width capped at the "DEBUG" badge width, marquee on overflow
        HeaderVersionBadge()

        if (Preference.parentalControl())
            Button(
                iconId = R.drawable.shield_checkmark,
                color = AppBar.contentColor(),
                padding = 0.dp,
                size = 20.dp
            ).Draw()

        // Reactive debug badge (n_zik): live preference state, tap toggles the debug logs
        // with the burger menu's side effects, title scrolls in a marquee
        DebugLogsBadge()
    }
// END
}




