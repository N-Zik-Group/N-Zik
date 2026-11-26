package app.n_zik.android.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Stores verified source credits and the user's display name on their saved playlist. */
val From43To44Migration = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Playlist` ADD COLUMN `authorBrowseId` TEXT")
        db.execSQL("ALTER TABLE `Playlist` ADD COLUMN `authorName` TEXT")
        db.execSQL("ALTER TABLE `Playlist` ADD COLUMN `authorNameOverride` TEXT")
    }
}
