package app.it.fast4x.rimusic.utils

import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

@Composable
fun getLikeState(mediaId: String): Int {
    val songLikeState by remember( mediaId ) {
        Database.songTable
                .likeState( mediaId )
                .distinctUntilChanged()
    }.collectAsState( false, Dispatchers.IO )

    return when( songLikeState ) {
        false -> getDislikedIcon()
        null -> getUnlikedIcon()
        true -> getLikedIcon()
    }
}

fun setLikeState(likedAt: Long?): Long? {
    val current =
     when (likedAt) {
        -1L -> null
        null -> System.currentTimeMillis()
        else -> -1L
    }
    //Timber.d("mediaItem setLikeState: $current")
    return current

}

fun setDisLikeState(likedAt: Long?): Long? {
    return when (likedAt) {
        -1L -> null        // dislike -> neutral
        null -> -1L        // neutral -> dislike
        else -> -1L        // like -> dislike
    }
}




