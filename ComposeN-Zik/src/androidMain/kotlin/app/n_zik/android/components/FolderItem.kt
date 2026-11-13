package app.n_zik.android.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.uiRoundnessShape

@Composable
fun FolderItem(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorPalette().background0,
    onClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy( 12.dp ),
        modifier = modifier.fillMaxWidth()
                           .clip( uiRoundnessShape() )
                           .background( backgroundColor )
                           .clip(uiRoundnessShape()).clickable( onClick = onClick )
                           .padding(
                               vertical = Dimensions.itemsVerticalPadding,
                               horizontal = 16.dp
                           )
    )
 {
        Box(
            Modifier.size( Dimensions.thumbnails.song )
        ) {
            Icon(
                painter = painterResource( R.drawable.folder ),
                tint = colorPalette().text,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        BasicText(
            text = text,
            style = typography().m.copy( colorPalette().text )
        )
    }
}




