package app.n_zik.android.components.dialog.artist

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.components.dialog.song.RenameDialog
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.net.Uri
import androidx.compose.ui.Modifier
import app.n_zik.android.appContext
import app.n_zik.android.core.profiles.activeCoversDirName
import app.it.fast4x.rimusic.utils.saveImageToInternalStorage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import app.n_zik.android.uiRoundnessShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import app.n_zik.android.colorPalette

class ChangeArtistCoverDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getArtist: () -> Artist?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke( getArtist: () -> Artist? ): ChangeArtistCoverDialog =
            ChangeArtistCoverDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue(cleanPrefix(getArtist()?.thumbnailUrl ?: "")) )
                },
                getArtist
            )

        /**
         * Issue #606 H9 -- decodes/scales/compresses the newly picked image into internal
         * storage. Blocking file/bitmap work; extracted so the `launcher` callback in
         * [DialogBody] can dispatch it via `withContext(NzikDispatchers.DATA)` instead of running
         * synchronously on Main (the thread an `ActivityResultLauncher` callback resumes on), and
         * so it is unit-testable without instantiating the dialog.
         */
        internal fun saveCoverArt(context: Context, uri: Uri, dirPath: String, fileName: String): Uri? =
            saveImageToInternalStorage(context, uri, dirPath, fileName)
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.cover_edit
    override val messageId: Int = R.string.update_cover
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun hideDialog() {
        super.hideDialog()
        value = TextFieldValue(cleanPrefix(getArtist()?.thumbnailUrl ?: ""))
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val artist = getArtist() ?: return
        Database.asyncTransaction {
            artistTable.insertIgnore( artist )
            artistTable.update( artist.copy(thumbnailUrl = "$MODIFIED_PREFIX$newValue") )
            Toaster.done()
        }
        hideDialog()
    }
    @Composable
    override fun DialogBody() {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            super.DialogBody()
            
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            // Issue #606 review fix: cancel any still-in-flight save before starting a new one, so
            // two rapid picks can't complete out of order and have the slower one overwrite `value`
            // with a stale result.
            val saveCoverJob = remember { mutableStateOf<Job?>(null) }
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    val artistId = getArtist()?.id ?: return@rememberLauncherForActivityResult
                    saveCoverJob.value?.cancel()
                    saveCoverJob.value = coroutineScope.launch {
                        val savedUri = withContext(NzikDispatchers.DATA) {
                            // The ACTIVE profile's custom covers folder name
                            // (spec-profile-data-separation)
                            saveCoverArt(context, uri, activeCoversDirName(context), "cover_$artistId.jpg")
                        }
                        if (savedUri != null) {
                            value = TextFieldValue(savedUri.toString())
                        }
                    }
                }
            }
            
            OutlinedButton(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = uiRoundnessShape(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorPalette().text
                ),
                border = BorderStroke(
                    1.dp,
                    colorPalette().textSecondary
                )
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                )
                Text(stringResource(R.string.pick_from_gallery))
            }
        }
    }
}
