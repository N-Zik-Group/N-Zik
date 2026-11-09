package app.n_zik.android.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import app.n_zik.android.core.database.LIBRARY_SCHEMA_VERSION
import app.n_zik.android.core.database.LibraryBackupSchema
import java.io.ByteArrayOutputStream
import java.io.File

internal fun openBackupFixture(
    context: Context,
    file: File,
    version: Int = LIBRARY_SCHEMA_VERSION,
    namespace: String = LibraryBackupSchema.CURRENT,
): SQLiteDatabase {
    val schema = checkNotNull(LibraryBackupSchema.read(context, namespace, version))
    return SQLiteDatabase.openOrCreateDatabase(file, null).apply {
        LibraryBackupSchema.createTables(this, schema)
        val setup = schema.getJSONArray("setupQueries")
        repeat(setup.length()) { execSQL(setup.getString(it)) }
        this.version = version
    }
}

internal fun backupJpeg(color: Int, width: Int = 8): ByteArray {
    val bitmap = Bitmap.createBitmap(width, 8, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
            it.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}
