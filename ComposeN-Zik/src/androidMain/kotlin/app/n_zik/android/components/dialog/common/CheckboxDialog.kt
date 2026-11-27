package app.n_zik.android.components.dialog.common

import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
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
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

abstract class CheckboxDialog(activeState: MutableState<Boolean>): ConfirmDialog {

    val items: MutableList<Item> = ArrayList()

    override var isActive: Boolean by activeState

    override val bodyPadding: PaddingValues get() = PaddingValues(0.dp)

    @Composable
    protected open fun visibleItems(): List<Item> = items

    override fun showDialog() {
        if (!isActive) items.forEach { it.selected = false }
        super.showDialog()
    }

    @Composable
    override fun DialogBody() {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            val visible = visibleItems()
            visible.forEach { it.ToolBarButton() }
            if (visible.isNotEmpty()) {
                val allSelected = visible.all { it.selected }
                CheckboxRow(stringResource(R.string.common_option_all), allSelected) {
                    visible.forEach { it.selected = !allSelected }
                }
            }
        }
    }

    abstract class Item: MenuIcon {

        abstract val id: String
        override val iconId: Int = -1

        var selected: Boolean by mutableStateOf(false)

        override fun onShortClick() {
            selected = !selected
        }

        @Composable
        override fun ToolBarButton() = CheckboxRow(menuIconTitle, selected, ::onShortClick)
    }

    companion object {
        @Composable
        private fun CheckboxRow(label: String, selected: Boolean, onClick: () -> Unit) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                                   .padding( vertical = 3.dp )
                                   .clickable(
                                       interactionSource = remember { MutableInteractionSource() },
                                       indication = glassGlobalIndication(null),
                                       onClick = onClick
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
                    text = label,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                    style = typography().xs.copy( color = colorPalette().text )
                )
            }
        }
    }
}




