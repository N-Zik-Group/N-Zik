package app.n_zik.android.core.settings

import android.content.SharedPreferences
import app.n_zik.android.utils.proxyPortKey

/** The port shown before a custom value is saved must also be used on activation. */
internal const val defaultProxyPort = 1080

internal fun SharedPreferences.readProxyPort(): Int = getInt(proxyPortKey, defaultProxyPort)
