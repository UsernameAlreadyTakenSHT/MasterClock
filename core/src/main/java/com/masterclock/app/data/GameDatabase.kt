package com.masterclock.app.data

import android.content.Context
import androidx.room.*
import com.masterclock.app.logic.ChessClockSettings
import kotlinx.serialization.json.Json

@Dao
interface GameLogDao {
    @Query("SELECT * FROM game_logs ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<GameLogEntity>

    /** [getRecentLogs] without the rows too large to read; see [MAX_READABLE_ROW_BYTES]. */
    @Query(
        "SELECT * FROM game_logs WHERE length(CAST(settingsJson AS BLOB)) + length(CAST(eventsJson AS BLOB)) + " +
            "length(CAST(COALESCE(initialStatesJson, '') AS BLOB)) <= :maxBytes ORDER BY startTime DESC LIMIT :limit"
    )
    suspend fun getRecentLogsUpTo(limit: Int, maxBytes: Int): List<GameLogEntity>

    @Query("SELECT * FROM game_logs WHERE id = :id")
    suspend fun getLog(id: String): GameLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: GameLogEntity)

    // Imported games go first, the oldest of them first, and only then the player's own. Trimming
    // by date alone let a friend's hundred newer games, imported under the default limit of 100,
    // delete every game played on this device.
    @Query("DELETE FROM game_logs WHERE id NOT IN (SELECT id FROM game_logs ORDER BY imported ASC, startTime DESC LIMIT :limit)")
    suspend fun trimLogs(limit: Int)

    @Transaction
    suspend fun saveLogWithTrim(log: GameLogEntity, limit: Int) {
        insertLog(log)
        trimLogs(limit)
    }

    @Query("DELETE FROM game_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM game_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("DELETE FROM game_logs WHERE startTime < :timestamp AND id != :keepId")
    suspend fun deleteLogsOlderThan(timestamp: Long, keepId: String)

    @Query("SELECT * FROM saved_clocks LIMIT 1")
    suspend fun getSavedClock(): SavedClockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveClock(clock: SavedClockEntity)

    @Query("DELETE FROM saved_clocks")
    suspend fun clearSavedClock()
}

@Entity(tableName = "saved_clocks")
data class SavedClockEntity(
    @PrimaryKey val id: Int = 1, // We only keep one saved clock for now
    val timestamp: Long = System.currentTimeMillis(),
    val settingsJson: String,
    val stateJson: String
)

@Entity(tableName = "game_logs")
data class GameLogEntity(
    @PrimaryKey val id: String,
    val startTime: Long,
    val settingsJson: String,
    val eventsJson: String,
    val initialStatesJson: String? = null, // New column
    /** Arrived in an import rather than played here; see trimLogs. */
    @ColumnInfo(defaultValue = "0") val imported: Boolean = false,
)

/**
 * The most a game's events may take in the database, in bytes. Room reads a row through a
 * CursorWindow of about 2 MB and fails on one that does not fit. No game comes near it: 2,000 moves
 * of ordinary text are under 400 KB.
 */
internal const val MAX_STORED_EVENTS_BYTES = 1_000_000

/** Below the CursorWindow's 2 MB, with room for what it stores beside a row. */
internal const val MAX_READABLE_ROW_BYTES = 1_500_000

/**
 * As many of [events] as fit in [maxBytes] once [encode]d, from the start of the game.
 *
 * The caps on an imported event count characters, and it is bytes that have to fit. JSON writes a
 * control character as six bytes and UTF-8 others as three, so 2,000 events at their caps came to
 * more than 4 MB: Room then failed to read the history -- straight after the import, and at every
 * launch from then on, until the app's data was cleared.
 */
internal fun fitEventsToRow(
    events: List<com.masterclock.app.logic.GameEvent>,
    maxBytes: Int = MAX_STORED_EVENTS_BYTES,
    encode: (List<com.masterclock.app.logic.GameEvent>) -> String,
): List<com.masterclock.app.logic.GameEvent> {
    var kept = events
    while (kept.isNotEmpty()) {
        val size = encode(kept).toByteArray().size
        if (size <= maxBytes) break
        kept = kept.take((kept.size.toLong() * maxBytes / size).toInt().coerceIn(0, kept.size - 1))
    }
    return kept
}

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    fun toGameLog(entity: GameLogEntity): com.masterclock.app.logic.GameLog {
        return try {
            // Read without the notebook, for games recorded before they stopped carrying it.
            val settings = json.decodeFromString<ChessClockSettings>(entity.settingsJson).copy(notebookNotes = emptyList())
            val events = json.decodeFromString<List<com.masterclock.app.logic.GameEvent>>(entity.eventsJson)
            val initialStates: List<com.masterclock.app.logic.PlayerStateProxy> = if (entity.initialStatesJson.isNullOrBlank()) emptyList() else json.decodeFromString(entity.initialStatesJson)
            
            com.masterclock.app.logic.GameLog(
                id = entity.id,
                startTime = entity.startTime,
                settings = settings,
                events = events,
                initialPlayerStates = initialStates,
                imported = entity.imported,
            )
        } catch (_: Exception) {
            com.masterclock.app.logic.GameLog(
                id = entity.id,
                startTime = entity.startTime,
                settings = ChessClockSettings(),
                events = emptyList()
            )
        }
    }

    /** [fromGameLog] for a game from outside the app, held to what the database can read back. */
    fun fromImportedGameLog(log: com.masterclock.app.logic.GameLog): GameLogEntity =
        fromGameLog(log.copy(events = fitEventsToRow(log.events) { json.encodeToString(it) }))

    fun fromGameLog(log: com.masterclock.app.logic.GameLog): GameLogEntity {
        return GameLogEntity(
            id = log.id,
            startTime = log.startTime,
            settingsJson = json.encodeToString(log.settings.copy(notebookNotes = emptyList())),
            eventsJson = json.encodeToString(log.events),
            initialStatesJson = json.encodeToString(log.initialPlayerStates),
            imported = log.imported,
        )
    }
}

/**
 * v4 marks imported games, which the trim removes first, and takes the notebook out of every game
 * recorded before games stopped carrying it: each held the whole notebook, drawings and media paths
 * included, which went out with every share "with games" and made rows that a large notebook could
 * push past the roughly 2 MB Room reads a row through.
 */
internal val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game_logs ADD COLUMN imported INTEGER NOT NULL DEFAULT 0")
        val json = Json { ignoreUnknownKeys = true }
        val ids = mutableListOf<String>()
        db.query("SELECT id FROM game_logs").use { c -> while (c.moveToNext()) ids += c.getString(0) }
        for (id in ids) {
            // One row at a time, and one that cannot be read or rewritten is left as it was.
            try {
                val old = db.query("SELECT settingsJson FROM game_logs WHERE id = ?", arrayOf(id)).use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: continue
                if (!old.contains("notebookNotes")) continue
                val stripped = json.encodeToString(json.decodeFromString<ChessClockSettings>(old).copy(notebookNotes = emptyList()))
                db.execSQL("UPDATE game_logs SET settingsJson = ? WHERE id = ?", arrayOf(stripped, id))
            } catch (_: Exception) {
            }
        }
    }
}

@Database(entities = [GameLogEntity::class, SavedClockEntity::class], version = 4, exportSchema = true)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameLogDao(): GameLogDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "game_database"
                )
                // No schema history was ever exported (exportSchema was false through v3), so there's
                // no ground truth to write accurate Migrations from for existing installs. game_logs /
                // saved_clocks are a local cache (not synced, no account data), so destructive fallback
                // just clears that local cache instead of hard-crashing the app on the next version
                // bump. exportSchema=true above means real Migrations can be written from here on.
                // The history is the player's; from v4 on a version step is a real migration, and the
                // destructive fallback is left only for downgrades and versions without one.
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
