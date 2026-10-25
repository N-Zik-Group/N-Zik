package app.n_zik.android.modules

/**
 * Static description of a downloadable extension module (spec-ffmpeg-extension-module).
 *
 * [downloadUrl] + [sha256] pin the exact archive artifact; [soFiles] is the ordered
 * `System.load` list per ABI (dependency order - leaf libraries first);
 * [requiredFreeSpaceBytes] is the free-space gate checked before a download starts.
 */
data class ModuleDefinition(
    /** Stable unique id; modules live under `filesDir/modules/{id}/`. */
    val id: String,
    /** Display name (string resource). */
    val displayNameRes: Int,
    /** Marketplace description (string resource). */
    val descriptionRes: Int,
    /** Version pinned by the app; the installed directory is named `{version}-{abi}`. */
    val version: String,
    /** ABIs the module ships; devices without one of them cannot install the module. */
    val abis: Set<String>,
    /** Archive (AAR/zip) download URL. */
    val downloadUrl: String,
    /** Lowercase hex SHA-256 of the whole archive file. */
    val sha256: String,
    /** Ordered native library file names per ABI - `System.load` runs in this order. */
    val soFiles: Map<String, List<String>>,
    /** Free space required before a download starts (bytes). */
    val requiredFreeSpaceBytes: Long,
    /** Approximate download size, for UI display (bytes). */
    val downloadSizeBytes: Long,
)
