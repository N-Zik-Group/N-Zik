package app.n_zik.android.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val From36To37Migration = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Artist ADD COLUMN dislikedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN dislikedAt INTEGER DEFAULT NULL")
    }
}
