package app.n_zik.android.components.settings.common

import android.content.Context
import android.net.Uri

internal fun SettingsCardRegistryEntry.categoryRoute(context: Context): String {
    val target = Uri.encode(settingsCardTargetId(context, tab, titleRes))
    return "settingsCategory/${categoryId()}?target=$target"
}

internal fun settingsShortcutRoute(context: Context, tab: Int, focus: String): String? {
    if (isGlassSettingsLink(tab, focus)) return SETTINGS_GLASS_ROUTE
    if (focus.isEmpty()) return null
    if (tab == 7 && focus == "debug") {
        return AllSettingsEntryTargets.firstOrNull { it.id == "embedded.maintenance.enable_log_debug" }
            ?.destinationRoute(context)
    }
    return SettingsCards.firstOrNull { it.tab == tab && it.focusId == focus }?.categoryRoute(context)
}
