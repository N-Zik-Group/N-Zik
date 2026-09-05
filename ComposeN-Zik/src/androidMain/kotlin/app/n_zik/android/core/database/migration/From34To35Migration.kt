package app.n_zik.android.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class From34To35Migration : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Song ADD COLUMN isYoutubeSong INTEGER NOT NULL DEFAULT 0")
    }
}
