package app.n_zik.android.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val From35To36Migration = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Playlist ADD COLUMN isAutoSync INTEGER NOT NULL DEFAULT 0")
    }
}
