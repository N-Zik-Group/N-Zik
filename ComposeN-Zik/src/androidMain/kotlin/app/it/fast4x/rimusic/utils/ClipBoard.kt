package app.it.fast4x.rimusic.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber


fun textCopyToClipboard(textCopied:String, context: Context) {
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", textCopied))
    }.onFailure {
        Timber.tag("ClipBoard").e(it.stackTraceToString())
        Toaster.e( R.string.common_clipboard_copy_failed )
    }
    // Only show a toast for Android 12 and lower.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
        Toaster.s( R.string.common_clipboard_copy_success )
}

@Composable
fun textCopyFromClipboard(context: Context): String {
    var textCopied by remember { mutableStateOf("") }
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val item = clip.getItemAt(0)
            if (item.text != null) {
                textCopied = item.text.toString()
            } else {
                Timber.tag("ClipBoard").w("Failed to copy text to clipboard, try again")
                Toaster.e(R.string.common_clipboard_copy_failed)
            }
        }
    }.onFailure {
        Timber.tag("ClipBoard").e(it.stackTraceToString())
        Toaster.e( R.string.common_clipboard_copy_failed )
    }
    // Only show a toast for Android 12 and lower.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && textCopied.isNotEmpty())
        Toaster.i( R.string.common_clipboard_copy_success )

    return textCopied
}


