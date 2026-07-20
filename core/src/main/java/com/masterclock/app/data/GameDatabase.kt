package com.masterclock.app.data

import android.content.Context
import androidx.room.*
import com.masterclock.app.logic.ChessClockSettings
import kotlinx.serialization.json.Json

@Dao
interface GameLogDao {
    @Query("SELECT * FROM game_logs ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<GameLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: GameLogEntity)

    @Query("DELETE FROM game_logs WHERE id NOT IN (SELECT id FROM game_logs ORDER BY startTime DESC LIMIT :limit)")
    suspend fun trimLogs(limit: Int)

    @Transaction
    suspend fun saveLogWithTrim(log: GameLogEntity, limit: Int) {
        insertLog(log)
        trimLogs(limit)
    }

    @Query("DELETE FROM game_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM game_logs WHERE startTime < :timestamp")
    suspend fun deleteLogsOlderThan(timestamp: Long)

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
    val initialStatesJson: String? = null // New column
)

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    fun toGameLog(entity: GameLogEntity): com.masterclock.app.logic.GameLog {
        return try {
            val settings = json.decodeFromString<ChessClockSettings>(entity.settingsJson)
            val events = json.decodeFromString<List<com.masterclock.app.logic.GameEvent>>(entity.eventsJson)
            val initialStates: List<com.masterclock.app.logic.PlayerStateProxy> = if (entity.initialStatesJson.isNullOrBlank()) emptyList() else json.decodeFromString(entity.initialStatesJson)
            
            com.masterclock.app.logic.GameLog(
                id = entity.id,
                startTime = entity.startTime,
                settings = settings,
                events = events,
                initialPlayerStates = initialStates
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

    fun fromGameLog(log: com.masterclock.app.logic.GameLog): GameLogEntity {
        return GameLogEntity(
            id = log.id,
            startTime = log.startTime,
            settingsJson = json.encodeToString(log.settings),
            eventsJson = json.encodeToString(log.events),
            initialStatesJson = json.encodeToString(log.initialPlayerStates)
        )
    }
}

@Database(entities = [GameLogEntity::class, SavedClockEntity::class], version = 3, exportSchema = true)
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
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
