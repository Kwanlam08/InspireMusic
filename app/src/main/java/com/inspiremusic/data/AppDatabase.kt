package com.inspiremusic.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/** App 内的非破坏性元数据覆盖；永远不会回写用户的音频文件。 */
@Entity(tableName = "metadata_overrides")
data class MetadataOverrideEntity(
    @PrimaryKey val audioId: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/** 每次整理保存一份之前的覆盖值，支持按批次撤销。 */
@Entity(tableName = "metadata_edit_history", indices = [Index("batchId"), Index("audioId")])
data class MetadataEditHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: String,
    val audioId: Long,
    val previousOverrideJson: String?,
    val actionLabel: String,
    val createdAt: Long = System.currentTimeMillis()
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

@Dao
interface MetadataOverrideDao {
    @Query("SELECT * FROM metadata_overrides")
    suspend fun getAll(): List<MetadataOverrideEntity>

    @Query("SELECT * FROM metadata_overrides WHERE audioId = :audioId")
    suspend fun get(audioId: Long): MetadataOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: MetadataOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<MetadataOverrideEntity>)

    @Query("DELETE FROM metadata_overrides WHERE audioId = :audioId")
    suspend fun delete(audioId: Long)

    @Insert
    suspend fun addHistory(values: List<MetadataEditHistoryEntity>)

    @Query("SELECT * FROM metadata_edit_history ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun getHistory(limit: Int = 100): List<MetadataEditHistoryEntity>

    @Query("SELECT * FROM metadata_edit_history WHERE batchId = :batchId ORDER BY id DESC")
    suspend fun getBatch(batchId: String): List<MetadataEditHistoryEntity>

    @Query("DELETE FROM metadata_edit_history WHERE batchId = :batchId")
    suspend fun deleteBatch(batchId: String)
}

// ── Database ──────────────────────────────────────────────

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        MetadataEntity::class,
        MetadataOverrideEntity::class,
        MetadataEditHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun metadataDao(): MetadataDao
    abstract fun metadataOverrideDao(): MetadataOverrideDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_player_db"
                )
                .addMigrations(MIGRATION_5_6)
                .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `metadata_overrides` (
                        `audioId` INTEGER NOT NULL,
                        `title` TEXT,
                        `artist` TEXT,
                        `album` TEXT,
                        `albumArtist` TEXT,
                        `trackNumber` INTEGER,
                        `discNumber` INTEGER,
                        `year` INTEGER,
                        `genre` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`audioId`)
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `metadata_edit_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `batchId` TEXT NOT NULL,
                        `audioId` INTEGER NOT NULL,
                        `previousOverrideJson` TEXT,
                        `actionLabel` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_edit_history_batchId` ON `metadata_edit_history` (`batchId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_edit_history_audioId` ON `metadata_edit_history` (`audioId`)")
            }
        }
    }
}
