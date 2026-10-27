@file:UnstableApi

package app.it.fast4x.rimusic.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.DownloadService.sendAddDownload
import androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload
import app.n_zik.android.core.network.utils.isMetered

inline fun <reified T> Context.intent(): Intent =
    Intent(this, T::class.java)

inline fun <reified T : BroadcastReceiver> Context.broadCastPendingIntent(
    requestCode: Int = 0,
    flags: Int = if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0,
): PendingIntent =
    PendingIntent.getBroadcast(this, requestCode, intent<T>(), flags)

inline fun <reified T : Activity> Context.activityPendingIntent(
    requestCode: Int = 0,
    flags: Int = 0,
    block: Intent.() -> Unit = {},
): PendingIntent =
    PendingIntent.getActivity(
        this,
        requestCode,
        intent<T>().apply(block),
        (if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0) or flags
    )

val Context.isIgnoringBatteryOptimizations: Boolean
    get() = if (isAtLeastAndroid6) {
        getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) ?: true
    } else {
        true
    }

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
fun Context.toastLong(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

fun Context.hasPermission(permission: String) = ContextCompat.checkSelfPermission(
    applicationContext,
    permission
) == PackageManager.PERMISSION_GRANTED


@OptIn(UnstableApi::class)
inline fun <reified T : DownloadService> Context.download(request: DownloadRequest) = runCatching {
    sendAddDownload(
        /* context         = */ this,
        /* clazz           = */ T::class.java,
        /* downloadRequest = */ request,
        /* foreground      = */ true
    )
}.recoverCatching {
    sendAddDownload(
        /* context         = */ this,
        /* clazz           = */ T::class.java,
        /* downloadRequest = */ request,
        /* foreground      = */ false
    )
}

@OptIn(UnstableApi::class)
inline fun <reified T : DownloadService> Context.removeDownload(mediaId: String) = runCatching {
    sendRemoveDownload(
        /* context         = */ this,
        /* clazz           = */ T::class.java,
        /* id              = */ mediaId,
        /* foreground      = */ false
    )
}

fun Context.isConnectionMetered(): Boolean {
    return this.isMetered
}

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("Should be called in the context of an Activity")
}


