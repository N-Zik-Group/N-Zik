package app.n_zik.android.core.utils

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography

object PathUtils {

    private val primaryStorageRoot = Regex(
        "^/(?:storage/emulated/[0-9]+|storage/self/primary|mnt/sdcard|sdcard)(?=/|$)"
    )
    private val removableStorageRoot = Regex("^/(?:mnt/media_rw|storage)/([^/]+)(?=/|$)")
    private val removableVolumeName = Regex(
        "(?i)(?:[0-9a-f]{4}-[0-9a-f]{4}|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})"
    )

    /** Use the same volume-relative folder identity for saved paths and MediaStore paths. */
    internal fun normalizeFolder(path: String): String {
        val segments = path.replace('\\', '/').split('/')
        val normalized = mutableListOf<String>()
        for (segment in segments) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
                else -> normalized.add(segment)
            }
        }
        val joined = normalized.joinToString("/")
        return if (path.startsWith('/') || path.startsWith('\\')) {
            val absolutePath = "/$joined"
            val removableRoot = removableStorageRoot.find(absolutePath)
                ?.takeIf { removableVolumeName.matches(it.groupValues[1]) }
            if (removableRoot != null) {
                removableRoot.groupValues[1].lowercase() + absolutePath.substring(removableRoot.value.length)
            } else {
                primaryStorageRoot.replaceFirst(absolutePath, "").trimStart('/')
            }
        } else {
            joined
        }
    }

    internal fun normalizeExclusion(path: String): String? {
        val trimmed = path.trim()
        val normalized = normalizeFolder(trimmed)
        return normalized.takeIf {
            it.isNotEmpty() || (trimmed.isNotEmpty() && trimmed.all { char -> char == '/' || char == '\\' })
        }
    }

    internal fun mediaFolder(path: String?, isFilePath: Boolean, volumeName: String? = null): String {
        val rawPath = path.orEmpty().replace('\\', '/')
        val folder = if (isFilePath) rawPath.substringBeforeLast('/', "") else rawPath
        val relativeFolder = normalizeFolder(folder)
        return if (volumeName != null && removableVolumeName.matches(volumeName)) {
            listOf(volumeName.lowercase(), relativeFolder).filter(String::isNotEmpty).joinToString("/")
        } else relativeFolder
    }

    internal fun isAtOrUnder(path: String, folder: String): Boolean =
        folder.isEmpty() || path == folder || path.startsWith("$folder/")

    // Existing volume-relative exclusions applied to MediaStore paths on every volume.
    internal fun isExcluded(path: String, blockedFolder: String): Boolean =
        isAtOrUnder(path, blockedFolder) ||
            (removableVolumeName.matches(path.substringBefore('/')) &&
                isAtOrUnder(path.substringAfter('/', ""), blockedFolder))

    internal fun resolveFolder(paths: Collection<String>, currentFolder: String?, startFolder: String): String {
        var folder = currentFolder ?: normalizeFolder(startFolder.trim())
        while (folder.isNotEmpty() && paths.none { isAtOrUnder(it, folder) }) {
            folder = folder.substringBeforeLast('/', "")
        }
        return folder
    }

    internal fun childPath(folder: String, child: String): String =
        if (folder.isEmpty()) child else "$folder/$child"

    fun getAvailablePaths(paths: Collection<String>, currentPath: String): List<String> =
        paths.asSequence()
            .filter { it != currentPath && isAtOrUnder(it, currentPath) }
            .map { it.drop(if (currentPath.isEmpty()) 0 else currentPath.length + 1).substringBefore('/') }
            .distinct()
            .sorted()
            .toList()

    /**
     * Draw an interactive address bar starting with the library root icon.
     * Between folders there's [R.drawable.chevron_forward].
     *
     * Each path is clickable. When clicked, open folder at that exact location.
     *
     * @param currentPath where user is at
     * @param onSpecificAddressClick action happens when user clicks 1 of the path
     */
    @Composable
    fun AddressBar(
        currentPath: String,
        onSpecificAddressClick: (String) -> Unit
    ) = Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding( bottom = 10.dp )
                           .padding( horizontal = 20.dp )
    ) {

        Box(
            modifier = Modifier.size(32.dp).clip(uiRoundnessShape()).clickable {
                onSpecificAddressClick("")
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.floating_dock_home),
                tint = colorPalette().textDisabled,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }

        val breadcrumbs = generateSequence(currentPath) { it.substringBeforeLast('/', "") }
            .takeWhile(String::isNotEmpty)
            .take(3)
            .toList()
            .asReversed()
        for (path in breadcrumbs) {

            Icon(
                painter = painterResource(R.drawable.chevron_forward),
                tint = colorPalette().textDisabled,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )

            BasicText(
                text = path.substringAfterLast('/'),
                style = typography().xs.copy(
                    color = colorPalette().text,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.clip(uiRoundnessShape()).clickable { onSpecificAddressClick(path) }
            )
        }
    }
}





