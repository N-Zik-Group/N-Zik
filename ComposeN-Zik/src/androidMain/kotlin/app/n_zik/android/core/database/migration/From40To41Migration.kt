package app.n_zik.android.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 40 to version 41.
 *
 * Adds the nullable Lyrics `lastFetchedAt` column backing the lyrics fetch TTL, and rebuilds
 * the six tables whose entities declare column defaults that the deployed v40 schema never
 * had. Room verifies column defaults after a migration and SQLite cannot set a default with
 * ALTER TABLE, so each table is recreated with its v41 definition, its rows are copied over,
 * the old table is dropped (which removes its indexes), and the indexes are recreated.
 *
 * Parents (Song, Playlist) are rebuilt before their children (Format, SongPlaylistMap) so
 * that the foreign key targets exist while the child rows are copied.
 */
val From40To41Migration = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Lyrics ADD COLUMN lastFetchedAt INTEGER")

        // The view reads SongPlaylistMap: since SQLite 3.26, ALTER TABLE ... RENAME fails with
        // "error in view" while the view points at the dropped table, so it is dropped here and
//
        // recreated (same definition as schemas/.../41.json) after the last rebuild.
        db.execSQL("DROP VIEW IF EXISTS SortedSongPlaylistMap")

        rebuildTable(
            db,
            "Song",
            "CREATE TABLE Song_41 (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artistsText` TEXT, " +
                "`durationText` TEXT, `thumbnailUrl` TEXT, `likedAt` INTEGER, " +
                "`totalPlayTimeMs` INTEGER NOT NULL, `position` INTEGER NOT NULL DEFAULT -1, " +
                "`isYoutubeSong` INTEGER NOT NULL DEFAULT 0, `playCount` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`id`))",
            "id, title, artistsText, durationText, thumbnailUrl, likedAt, totalPlayTimeMs, " +
                "position, isYoutubeSong, playCount"
        )
        rebuildTable(
            db,
            "Playlist",
            "CREATE TABLE Playlist_41 (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `browseId` TEXT, `isEditable` INTEGER NOT NULL, " +
                "`isYoutubePlaylist` INTEGER NOT NULL DEFAULT 0, `isAutoSync` INTEGER NOT NULL DEFAULT 0, " +
                "`position` INTEGER NOT NULL DEFAULT -1)",
            "id, name, browseId, isEditable, isYoutubePlaylist, isAutoSync, position"
        )
        rebuildTable(
            db,
            "Album",
            "CREATE TABLE Album_41 (`id` TEXT NOT NULL, `title` TEXT, `thumbnailUrl` TEXT, `year` TEXT, " +
                "`authorsText` TEXT, `shareUrl` TEXT, `timestamp` INTEGER, `bookmarkedAt` INTEGER, " +
                "`isYoutubeAlbum` INTEGER NOT NULL DEFAULT 0, `position` INTEGER NOT NULL DEFAULT -1, " +
                "`lastFetch` INTEGER DEFAULT NULL, `dislikedAt` INTEGER DEFAULT NULL, " +
                "`genres` TEXT DEFAULT NULL, `originalYear` INTEGER DEFAULT NULL, " +
                "`albumType` TEXT DEFAULT NULL, `tags` TEXT DEFAULT NULL, `rating` REAL DEFAULT NULL, " +
                "`ratingVotes` INTEGER DEFAULT NULL, `wikipediaUrl` TEXT DEFAULT NULL, " +
                "`wikipediaInfo` TEXT DEFAULT NULL, `description` TEXT DEFAULT NULL, " +
                "`links` TEXT DEFAULT NULL, `mbId` TEXT DEFAULT NULL, " +
                "`youtubeAlbumId` TEXT DEFAULT NULL, `mbLastFetch` INTEGER DEFAULT NULL, " +
                "PRIMARY KEY(`id`))",
            "id, title, thumbnailUrl, year, authorsText, shareUrl, timestamp, bookmarkedAt, " +
                "isYoutubeAlbum, position, lastFetch, dislikedAt, genres, originalYear, albumType, " +
                "tags, rating, ratingVotes, wikipediaUrl, wikipediaInfo, description, links, mbId, " +
                "youtubeAlbumId, mbLastFetch"
        )
        rebuildTable(
            db,
            "Artist",
            "CREATE TABLE Artist_41 (`id` TEXT NOT NULL, `name` TEXT, `thumbnailUrl` TEXT, " +
                "`timestamp` INTEGER, `bookmarkedAt` INTEGER, `isYoutubeArtist` INTEGER NOT NULL DEFAULT 0, " +
                "`position` INTEGER NOT NULL DEFAULT -1, `lastFetch` INTEGER DEFAULT NULL, " +
                "`dislikedAt` INTEGER DEFAULT NULL, `genres` TEXT DEFAULT NULL, " +
                "`artistType` TEXT DEFAULT NULL, `countryCode` TEXT DEFAULT NULL, " +
                "`beginYear` INTEGER DEFAULT NULL, `tags` TEXT DEFAULT NULL, `rating` REAL DEFAULT NULL, " +
                "`ratingVotes` INTEGER DEFAULT NULL, `wikipediaUrl` TEXT DEFAULT NULL, " +
                "`wikipediaBio` TEXT DEFAULT NULL, `description` TEXT DEFAULT NULL, " +
                "`disambiguation` TEXT DEFAULT NULL, `links` TEXT DEFAULT NULL, `mbId` TEXT DEFAULT NULL, " +
                "`youtubeChannelId` TEXT DEFAULT NULL, `mbLastFetch` INTEGER DEFAULT NULL, " +
                "PRIMARY KEY(`id`))",
            "id, name, thumbnailUrl, timestamp, bookmarkedAt, isYoutubeArtist, position, lastFetch, " +
                "dislikedAt, genres, artistType, countryCode, beginYear, tags, rating, ratingVotes, " +
                "wikipediaUrl, wikipediaBio, description, disambiguation, links, mbId, youtubeChannelId, " +
                "mbLastFetch"
        )
        rebuildTable(
            db,
            "Format",
            "CREATE TABLE Format_41 (`songId` TEXT NOT NULL, `itag` INTEGER, `mimeType` TEXT, " +
                "`bitrate` INTEGER, `contentLength` INTEGER, `lastModified` INTEGER, " +
                "`loudnessDb` REAL, `codecs` TEXT DEFAULT NULL, `sampleRate` INTEGER DEFAULT NULL, " +
                "`perceptualLoudnessDb` REAL DEFAULT NULL, `audioChannels` INTEGER DEFAULT NULL, " +
                "`playbackUrl` TEXT DEFAULT NULL, PRIMARY KEY(`songId`), " +
                "FOREIGN KEY(`songId`) REFERENCES `Song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "songId, itag, mimeType, bitrate, contentLength, lastModified, loudnessDb, codecs, " +
                "sampleRate, perceptualLoudnessDb, audioChannels, playbackUrl"
        )
        rebuildTable(
            db,
            "SongPlaylistMap",
            "CREATE TABLE SongPlaylistMap_41 (`songId` TEXT NOT NULL, `playlistId` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `setVideoId` TEXT, `dateAdded` INTEGER DEFAULT NULL, " +
                "PRIMARY KEY(`songId`, `playlistId`), " +
                "FOREIGN KEY(`songId`) REFERENCES `Song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`playlistId`) REFERENCES `Playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "songId, playlistId, position, setVideoId, dateAdded",
            listOf(
                "CREATE INDEX IF NOT EXISTS `index_SongPlaylistMap_songId` ON `SongPlaylistMap` (`songId`)",
                "CREATE INDEX IF NOT EXISTS `index_SongPlaylistMap_playlistId` ON `SongPlaylistMap` (`playlistId`)"
            )
        )

        db.execSQL("CREATE VIEW `SortedSongPlaylistMap` AS SELECT * FROM SongPlaylistMap ORDER BY position")
    }

    private fun rebuildTable(
        db: SupportSQLiteDatabase,
        table: String,
        createSql: String,
        columnList: String,
        indexSqls: List<String> = emptyList()
    ) {
        db.execSQL(createSql)
        db.execSQL("INSERT INTO ${table}_41 ($columnList) SELECT $columnList FROM $table")
        db.execSQL("DROP TABLE $table")
        db.execSQL("ALTER TABLE ${table}_41 RENAME TO $table")
        indexSqls.forEach { db.execSQL(it) }
    }
}
