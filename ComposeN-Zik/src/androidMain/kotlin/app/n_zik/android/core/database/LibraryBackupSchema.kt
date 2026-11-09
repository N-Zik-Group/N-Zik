package app.n_zik.android.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject

internal const val LIBRARY_SCHEMA_VERSION = 43

/** Checks the staged database against exported Room schemas without starting Room in rescue. */
internal object LibraryBackupSchema {
    const val CURRENT = "app.n_zik.android.core.database.DatabaseInitializer"
    const val LEGACY = "app.it.fast4x.rimusic.DatabaseInitializer"
    private val optionalTables = setOf(
        "android_metadata", "room_master_table", "sqlite_sequence",
        "sqlite_stat1", "sqlite_stat2", "sqlite_stat3", "sqlite_stat4",
    )

    fun validate(context: Context, database: SQLiteDatabase) {
        val version = database.version
        require(version in 1..LIBRARY_SCHEMA_VERSION) { "Unsupported library database version" }
        val namespaces = if (version == LIBRARY_SCHEMA_VERSION) listOf(CURRENT) else listOf(CURRENT, LEGACY)
        require(namespaces.any { namespace ->
            val schema = read(context, namespace, version) ?: return@any false
            SQLiteDatabase.create(null).use { reference ->
                createTables(reference, schema)
                matches(reference, database, schema, version)
            }
        }) { "Incompatible library database schema" }
    }

    private fun matches(
        reference: SQLiteDatabase,
        database: SQLiteDatabase,
        schema: JSONObject,
        version: Int,
    ): Boolean {
        if (catalog(reference) != catalog(database)) return false
        if (database.rows(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'"
            ).isNotEmpty()
        ) {
            reference.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            if (!columnsMatch(reference, database, "room_master_table")) return false
            if (version == LIBRARY_SCHEMA_VERSION) {
                val hash = database.rows("SELECT identity_hash FROM room_master_table WHERE id=42").singleOrNull()?.singleOrNull()
                // Room also accepts the legacy hash generated for schema version 43.
                val legacyHash = version == 43 && hash == "f21af622e3b03c607afda6ad0a721ec9"
                if (hash != schema.getString("identityHash") && !legacyHash) return false
            }
        }
        for (entity in schema.getJSONArray("entities").objects()) {
            val table = entity.getString("tableName")
            if (!columnsMatch(reference, database, table) ||
                foreignKeys(reference, table) != foreignKeys(database, table) ||
                indexes(reference, table) != indexes(database, table)
            ) return false
        }
        for (view in schema.optJSONArray("views")?.objects().orEmpty()) {
            val name = view.getString("viewName")
            val query = "SELECT sql FROM sqlite_master WHERE type='view' AND name=?"
            if (reference.rows(query, arrayOf(name)) != database.rows(query, arrayOf(name))) return false
        }
        return true
    }

    // Room checks its own tables but ignores unrelated objects. An extra object can collide
    // with a future migration or execute during the staged rewrite of cover references.
    private fun catalog(db: SQLiteDatabase): Set<List<String?>> =
        db.rows("SELECT type, name, tbl_name FROM sqlite_master").filterNot { (type, name, table) ->
            type == "table" && name == table && name in optionalTables
        }.toSet()

    private fun columnsMatch(reference: SQLiteDatabase, database: SQLiteDatabase, table: String): Boolean {
        val query = "PRAGMA table_info(${quote(table)})"
        val expected = reference.rows(query).associateBy { it[1] }
        val actual = database.rows(query).associateBy { it[1] }
        return expected.keys == actual.keys && expected.all { (name, column) ->
            val found = actual.getValue(name)
            affinity(column[2]) == affinity(found[2]) && column[3] == found[3] &&
                column[5] == found[5] && (column[4] == null ||
                column[4]?.removeSurrounding("(", ")") == found[4]?.removeSurrounding("(", ")"))
        }
    }

    private fun affinity(type: String?): String {
        val declared = type.orEmpty().uppercase()
        return when {
            "INT" in declared -> "INTEGER"
            listOf("CHAR", "CLOB", "TEXT").any(declared::contains) -> "TEXT"
            declared.isEmpty() || "BLOB" in declared -> "BLOB"
            listOf("REAL", "FLOA", "DOUB").any(declared::contains) -> "REAL"
            else -> "NUMERIC"
        }
    }

    private fun foreignKeys(db: SQLiteDatabase, table: String): Set<List<List<String?>>> =
        db.rows("PRAGMA foreign_key_list(${quote(table)})")
            .groupBy { it.first() }.values
            .map { key -> key.sortedBy { checkNotNull(it[1]).toInt() }.map { it.drop(2) } }.toSet()

    private fun indexes(db: SQLiteDatabase, table: String): Set<Pair<List<String?>, List<List<String?>>>> =
        db.rows("PRAGMA index_list(${quote(table)})").filter { it.getOrNull(3) == "c" }.map { row ->
            val name = checkNotNull(row[1])
            row.drop(1) to db.rows("PRAGMA index_xinfo(${quote(name)})")
                .filter { checkNotNull(it[1]).toInt() >= 0 }
                .map { listOf(it[2], it[3]) }
        }.toSet()

    private fun quote(name: String): String = "\"${name.replace("\"", "\"\"")}\""

    private fun SQLiteDatabase.rows(query: String, args: Array<String>? = null): List<List<String?>> =
        rawQuery(query, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(List(cursor.columnCount, cursor::getString))
            }
        }

    internal fun read(context: Context, namespace: String, version: Int): JSONObject? =
        try {
            context.assets.open("$namespace/$version.json").bufferedReader().use {
                JSONObject(it.readText()).getJSONObject("database")
            }
        } catch (_: FileNotFoundException) {
            null
        }

    internal fun createTables(database: SQLiteDatabase, schema: JSONObject) {
        for (entity in schema.getJSONArray("entities").objects()) {
            val table = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            for (index in entity.optJSONArray("indices")?.objects().orEmpty()) {
                database.execSQL(index.getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        for (view in schema.optJSONArray("views")?.objects().orEmpty()) {
            database.execSQL(view.getString("createSql").replace("\${VIEW_NAME}", view.getString("viewName")))
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length(), ::getJSONObject)
}
