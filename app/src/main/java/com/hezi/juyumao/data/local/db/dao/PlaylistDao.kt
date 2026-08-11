package com.hezi.juyumao.data.local.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hezi.juyumao.data.local.db.entity.PlaylistEntity
import com.hezi.juyumao.data.local.db.entity.PlaylistSongEntity
import com.hezi.juyumao.data.local.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/** 歌单及其歌曲数（列表页用） */
data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int,
)

@Dao
interface PlaylistDao {

    // ── 歌单 CRUD ──

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    // ── 歌单歌曲关联 ──

    // 复合主键 (playlistId, songId) 已唯一；IGNORE 避免重复加入同曲目抛 SQLiteConstraintException 崩溃
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(relation: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongs(relations: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId LIMIT 1")
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Long?

    // ── 联查（歌单内歌曲） ──

    @Transaction
    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId")
    fun getPlaylistSongs(playlistId: Long): Flow<List<SongEntity>>

    @Transaction
    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId")
    suspend fun getPlaylistSongsOnce(playlistId: Long): List<SongEntity>

    // ── 计数 ──

    @Query("SELECT p.*, (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount FROM playlists p ORDER BY p.createdAt DESC")
    fun getPlaylistsWithCount(): Flow<List<PlaylistWithCount>>
}
