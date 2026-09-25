package app.it.fast4x.rimusic.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

const val activeProfileKey = "activeProfile"

val Context.profilePreferences: SharedPreferences
    get() = getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)

fun getActiveProfile(context: Context): String {
    val prefs = context.profilePreferences
    return prefs.getString(activeProfileKey, "default") ?: "default"
}


fun setActiveProfile(profileName: String, context: Context) {
    val prefs = context.profilePreferences
    prefs.edit { putString(activeProfileKey, profileName).commit() }
}