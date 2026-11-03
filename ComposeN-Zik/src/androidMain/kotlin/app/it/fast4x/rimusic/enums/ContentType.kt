package app.it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R

enum class ContentType {
    All,
    Official,
    UserGenerated;

    val textName: String
        @Composable
        get() = when( this ) {
            All -> stringResource(R.string.search_content_type_all)
            Official -> stringResource(R.string.search_content_type_official)
            UserGenerated -> stringResource(R.string.search_content_type_user_generated)
        }

    val icon: Int
        @Composable
        get() = when( this ) {
            All -> R.drawable.discover
            Official -> R.drawable.star_brilliant
            UserGenerated -> R.drawable.person
        }
}



