package app.n_zik.android.components.dialog.common

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon

abstract class CheckboxDialog(activeState: MutableState<Boolean>): ConfirmDialog {

    val items: MutableList<Item> = ArrayList()

    override var isActive: Boolean by activeState

    @Composable
    override fun DialogBody() {
        Column(
            Modifier.fillMaxWidth( .8f )
        ) {
            items.forEach { it.ToolBarButton() }
        }
    }

    abstract class Item: MenuIcon {

        companion object {
            val SELECT_ALL: Item by lazy {
                object: Item() {
                    override val id: String = "select_all"
                    override val menuIconTitle: String
                        @Composable
                        get() = stringResource(R.string.all)

                    override fun onShortClick() {
                        // Disable uncheck
                        if( selected ) return
                        super.onShortClick()
                    }
                }
            }
        }

        abstract val id: String
        override val iconId: Int = -1

        var selected: Boolean by mutableStateOf(false)

        override fun onShortClick() {
            if( selected )
                SELECT_ALL.selected = false

            selected = !selected
        }

        @Composable
        override fun ToolBarButton() {
            LaunchedEffect( SELECT_ALL.selected ) {
                if( SELECT_ALL.selected )
                    selected = true
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                                   .padding( vertical = 3.dp )
                                   .clickable(
                                       interactionSource = remember { MutableInteractionSource() },
                                       indication = null,
                                       onClick = ::onShortClick
                                   )
            ) {
                Icon(
                    painter = painterResource(if (selected) R.drawable.checked_filled else R.drawable.unchecked_outline),
                    contentDescription = null,
                    tint = if (selected) colorPalette().accent else colorPalette().textDisabled,
                    modifier = Modifier.size( 20.dp )
                )

                Spacer( Modifier.width( 5.dp ) )

                BasicText(
                    text = menuIconTitle,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                    style = typography().xs.copy( color = colorPalette().text )
                )
            }
        }
    }
}




