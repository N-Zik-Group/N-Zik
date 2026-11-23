package app.n_zik.android.core.rescue

import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import android.os.Build
import android.system.Os
import org.w3c.dom.Element
import org.xml.sax.InputSource
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.core.backup.SettingsPreferencesBridge
import app.n_zik.android.core.profiles.ProfileCacheLocationMigration
import app.n_zik.android.utils.DEFAULT_PROFILE_ID

/** Handles the settings-only XML snapshots used by Rescue reset and restore. */
internal object RescueSettingsSnapshot {
    fun fileNames(profile: String): List<String> {
        val suffix = if (profile == DEFAULT_PROFILE_ID) "" else "_$profile"
        return listOf("preferences$suffix.xml", "secure_preferences$suffix.xml",
            "${GlassProfilePreferences.fileName(profile)}.xml",
            "github_account_profile_$profile.xml", "github_account_profile_$profile.xml.bak")
    }

    fun isStorageKey(key: String): Boolean =
        SettingsPreferencesBridge.isStorageSetting(key) || ProfileCacheLocationMigration.isRequestKey(key)

    /** Settings-only snapshots never change the live paths or quota of the retained media. */
    fun preserveStorage(live: File, backup: File) {
        val builder = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        fun read(file: File) = if (file.exists()) {
            builder.parse(file).also { check(it.doctype == null && it.documentElement.tagName == "map") }
        } else {
            builder.newDocument().apply { appendChild(createElement("map")) }
        }
        fun storageEntries(root: Element): List<Element> =
            (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
                .filter { isStorageKey(it.getAttribute("name")) }

        val current = storageEntries(read(live).documentElement)
        val restored = read(backup)
        val target = restored.documentElement
        val old = storageEntries(target)
        if (current.isEmpty() && old.isEmpty()) return
        old.forEach(target::removeChild)
        current.forEach { target.appendChild(restored.importNode(it, true)) }
        val stage = File.createTempFile(".settings-storage-", ".xml", backup.parentFile)
        try {
            FileOutputStream(stage).use { output ->
                TransformerFactory.newInstance().newTransformer().transform(DOMSource(restored), StreamResult(output))
                output.fd.sync()
            }
            if (Build.VERSION.SDK_INT == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.move(stage.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Os.rename(stage.path, backup.path)
            }
        } finally {
            stage.delete()
        }
    }

    /** Replaces the backup and removes stale copies of absent settings files. */
    fun backup(sharedPrefsDir: File, backupDir: File, profile: String) {
        backupDir.mkdirs()
        fileNames(profile).forEach { name ->
            val source = File(sharedPrefsDir, name)
            val backup = File(backupDir, name)
            if (source.exists()) source.copyTo(backup, overwrite = true) else backup.delete()
        }
    }

    /** Parks each live file before restoring its backup, so the previous settings can be restored again. */
    fun swap(
        sharedPrefsDir: File,
        backupDir: File,
        profile: String,
    ) {
        sharedPrefsDir.mkdirs()
        fileNames(profile).forEach { name ->
            val live = File(sharedPrefsDir, name)
            val backup = File(backupDir, name)
            val parked = File(backupDir, "$name.swap")

            parked.delete()
            if (live.exists()) moveRescueFileReplacing(live, parked)
            if (backup.exists()) moveRescueFileReplacing(backup, live)
            if (parked.exists()) moveRescueFileReplacing(parked, backup)
        }
    }
}
