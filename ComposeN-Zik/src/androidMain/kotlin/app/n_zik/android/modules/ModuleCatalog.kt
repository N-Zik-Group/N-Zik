package app.n_zik.android.modules

/**
 * Catalog of every extension module the app knows about (the Marketplace tab
 * lists it, spec-ffmpeg-extension-module). Adding a module = one
 * [ModuleDefinition] here: the manager, the download, the native loader and
 * the notification are already generic over [ModuleDefinition].
 */
object ModuleCatalog {

    val all: List<ModuleDefinition> = listOf(FfmpegModule.definition)
}
