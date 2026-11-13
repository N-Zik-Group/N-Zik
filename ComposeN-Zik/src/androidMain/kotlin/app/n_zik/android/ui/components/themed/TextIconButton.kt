package app.n_zik.android.ui.components.themed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.utils.semiBold
import app.n_zik.android.typography
import app.n_zik.android.ui.components.navigation.nav.LocalIsManyButtons

class TextIconButton(
    val text: String,
    iconId: Int,
    color: Color,
    padding: Dp,
    size: Dp,
    forceWidth: Dp = Dp.Unspecified,
    val textSpacing: Dp = 5.dp,
    val isCompact: Boolean = false,
    modifier: Modifier = Modifier
): Button( iconId, color, padding, size, forceWidth, modifier ) {

    @Composable
    override fun Draw() {
        val isMany = LocalIsManyButtons.current
        val paddingScale = if (isMany) 0.75f else 1f

        Column (
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isCompact) Arrangement.Center else Arrangement.SpaceAround,
            modifier = Modifier.padding( all = (if (isCompact) 6.dp else 5.dp) * paddingScale )
                               .fillMaxSize()
        ){
            super.Draw()
            Spacer( modifier = Modifier.height( textSpacing ) )

            BasicText(
                text = text,
                style =  TextStyle(
                    fontSize = when {
                        isMany -> typography().xxxs.semiBold.fontSize
                        isCompact -> typography().xxs.semiBold.fontSize
                        else -> typography().xs.semiBold.fontSize
                    },
                    fontWeight = typography().xs.semiBold.fontWeight,
                    fontFamily = typography().xs.semiBold.fontFamily,
                    color = color,
                ),
                maxLines = if (isCompact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}



