package app.n_zik.android.modules

/**
 * Loads a module's native tree (spec-ffmpeg-extension-module, spike-verified).
 *
 * [loadTree] dlopens every .so of [directory] in [orderedNames]' order by full
 * path, into this helper's own namespace - the classloader namespace where the
 * module's Java library runs its `System.loadLibrary` on static init (see
 * `src/androidMain/jni/module_loader.c`). Returns the helper's report; every
 * line is `dlopen(<name>): OK` or `dlopen(<name>): FAILED (<error>)` - the
 * caller decides on failure.
 */
object ModuleNativeLoader {

    private var helperLoaded = false

    @Synchronized
    private fun ensureHelperLoaded() {
        if (helperLoaded) return
        System.loadLibrary("module_loader")
        helperLoaded = true
    }

    fun loadTree(directory: String, orderedNames: List<String>): String {
        ensureHelperLoaded()
        return nativeLoadTree(directory, orderedNames.joinToString(":"))
    }

    private external fun nativeLoadTree(directory: String, order: String): String
}
