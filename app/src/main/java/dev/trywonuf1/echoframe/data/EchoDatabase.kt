package dev.trywonuf1.echoframe.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val cacheKey: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationMs: Long,
    val syncedLyrics: String,
    val plainLyrics: String,
    val instrumental: Boolean,
    val syncKind: String,
    val savedAtMs: Long
)

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun find(key: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE savedAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM lyrics_cache")
    suspend fun count(): Int
}

@Database(entities = [LyricsCacheEntity::class], version = 1, exportSchema = false)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun lyricsCache(): LyricsCacheDao

    companion object {
        @Volatile private var INSTANCE: EchoDatabase? = null

        fun get(context: Context): EchoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                EchoDatabase::class.java,
                "echoframe-local.db"
            ).build().also { INSTANCE = it }
        }
    }
}
