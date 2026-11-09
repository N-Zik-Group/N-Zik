package app.n_zik.android.components.image

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import timber.log.Timber

/** Emulates Android's replace-on-rename semantics when Robolectric runs on Windows. */
@Implements(AtomicFile::class)
class PosixAtomicFileShadow {
    companion object {
        @JvmStatic
        @Implementation(minSdk = 29)
        fun rename(source: File, target: File) {
            if (target.isDirectory) target.delete()
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: IOException) {
                // Match AtomicFile's logged, non-throwing rename failure so production checks remain exercised.
                Timber.tag("PosixAtomicFileShadow").w(error, "Rename failed")
            }
        }
    }
}
