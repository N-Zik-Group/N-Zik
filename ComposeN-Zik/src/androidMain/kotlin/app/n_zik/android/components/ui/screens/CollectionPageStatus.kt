package app.n_zik.android.components.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding

/** Keeps loading and error feedback in the same scrollable safe area as collection rows. */
@Composable
internal fun CollectionPageStatus(
    contentPadding: PaddingValues = rememberPageChromeContentPadding(
        PaddingValues(bottom = Dimensions.bottomSpacer)
    ),
    content: @Composable BoxScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "collection-status", contentType = "collection-status") {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}
