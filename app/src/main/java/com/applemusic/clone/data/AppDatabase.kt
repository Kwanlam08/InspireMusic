package com.applemusic.clone.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entities ──────────────────────────────────────────────

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val orderIndex: Int
)

@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey val audioId: Long,
    val hasEmbeddedArt: Boolean,
    val fetchedAlbumArtUrl: String?,
    val fetchedLyricsPath: String?,
    val fetchedTrackNumber: Int?,
    val fetchedDiscNumber: Int?
)

// ── DAO ───────────────────────────────────────────────────

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getSongsInPlaylist(playlistId: Long): List<PlaylistSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(song: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongCount(playlistId: Long): Int
}

@Dao
interface MetadataDao {
    @Query("SELECT * FROM metadata")
    suspend fun getAllMetadata(): List<MetadataEntity>

    @Query("SELECT * FROM metadata WHERE audioId = :audioId")
    suspend fun getMetadata(audioId: Long): MetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: MetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metadata: List<MetadataEntity>)

    @Query("UPDATE metadata SET fetchedLyricsPath = NULL WHERE audioId = :audioId")
    suspend fun clearLyricsPath(audioId: Long)

    @Query("UPDATE metadata SET fetchedLyricsPath = NULL")
    suspend fun clearAllLyricsPaths()

    @Query("UPDATE metadata SET fetchedAlbumArtUrl = NULL WHERE audioId = :audioId")
    suspend fun clearArtworkUrl(audioId: Long)
}

// ── Database ──────────────────────────────────────────────

@Database(
    entities = [PlaylistEntity::class, PlaylistSongEntity::class, MetadataEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun metadataDao(): MetadataDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_player_db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
