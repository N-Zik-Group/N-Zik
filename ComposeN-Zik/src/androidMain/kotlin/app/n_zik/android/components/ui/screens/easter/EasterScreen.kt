package app.n_zik.android.components.ui.screens.easter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavController
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.easter.EasterAssetManager
import app.n_zik.android.easter.EasterEgg
import java.io.File

@Composable
fun EasterScreen(
    navController: NavController,
    egg: EasterEgg
) {
    val assetManager = remember { EasterAssetManager(appContext().filesDir) }
    // The file to display plus a stamp (its lastModified). The stamp changes when the file is
    // (re)downloaded, forcing both a recomposition and a Coil re-read of the in-place replaced
    // file: a bare file:// URI is string-equal across rewrites, so Coil would otherwise keep
    // serving the previously decoded bitmap.
    var display by remember(egg) { mutableStateOf<EggImage?>(null) }

    LaunchedEffect(egg) {
        val file = assetManager.imageFile(egg)
        // An existing copy is shown immediately, without waiting for the ETag revalidation
        file.takeIf(File::exists)?.let { display = EggImage(it, it.lastModified()) }
        // View-time sync (download when absent, ETag revalidation when present); the transfer
        // outlives this coroutine, so leaving the screen never cancels it
        assetManager.sync(egg)?.let { display = EggImage(it, it.lastModified()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { navController.popBackStack() },
        contentAlignment = Alignment.Center
    ) {
        ImageCacheFactory.LocalAsyncImage(
            model = display?.file,
            cacheKey = display?.let { "${egg.name}@${it.stamp}" },
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private data class EggImage(val file: File, val stamp: Long)
