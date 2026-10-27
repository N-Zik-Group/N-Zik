package app.n_zik.android.privacy

import android.app.Activity
import android.content.SharedPreferences
import android.view.WindowManager
import app.it.fast4x.rimusic.utils.preferences
import java.io.Closeable

const val blockScreenCaptureKey = "privacyBlockScreenCapture"

/** Applies the active profile's policy before the Activity becomes visible and observes changes. */
class ScreenCapturePolicy private constructor(private val activity: Activity) : Closeable {
    private val prefs = activity.preferences
    private val initiallySecure = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    private var closed = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == blockScreenCaptureKey || key == null) activity.runOnUiThread { apply() }
    }
    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        apply()
    }
    private fun apply() {
        if (closed) return
        if (initiallySecure || prefs.getBoolean(blockScreenCaptureKey, false))
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun close() {
        closed = true
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
    companion object {
        fun bind(activity: Activity): ScreenCapturePolicy = ScreenCapturePolicy(activity)
    }
}
