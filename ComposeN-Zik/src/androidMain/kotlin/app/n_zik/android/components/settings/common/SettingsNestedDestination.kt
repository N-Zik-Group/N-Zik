package app.n_zik.android.components.settings.common

/** A setting inside a configuration dialog, identified without changing its value. */
data class SettingsNestedDestination(
    val openerId: String,
    val dialogId: String,
    val tabIndex: Int = 0,
    val itemId: String,
    val sectionTitleRes: Int? = null,
)

/** Source-owned metadata shared by dialog rendering and settings search. */
data class SettingsNestedOption(
    val itemId: String,
    val labelRes: Int,
    val tabIndex: Int = 0,
    val sectionTitleRes: Int? = null,
    val descriptionRes: Int? = null,
)
