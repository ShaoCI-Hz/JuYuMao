package com.hezi.juyumao.data.local.db.dao

import androidx.room.*
import com.hezi.juyumao.data.local.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

// 转义 LIKE 查询中的通配符
private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY addedAt DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    /** 一次性取某台 SMB 服务器的全部歌曲（扫描差集清理用） */
    @Query("SELECT * FROM songs WHERE smbServerId = :serverId")
    suspend fun getSongsByServerOnce(serverId: Long): List<SongEntity>

    /** 删除某台 SMB 服务器的全部歌曲（删除服务器时清理，避免幽灵数据） */
    @Query("DELETE FROM songs WHERE smbServerId = :serverId")
    suspend fun deleteSongsByServer(serverId: Long)

    @Query("SELECT * FROM songs ORDER BY lastPlayedAt DESC LIMIT 20")
    fun getRecentlyPlayed(): Flow<List<SongEntity>>

    // 搜索时先转义再用 ESCAPE '\' 匹配
    fun search(query: String): Flow<List<SongEntity>> {
        val escaped = query.escapeLike()
        return searchInternal("%$escaped%")
    }

    @Query("SELECT * FROM songs WHERE title LIKE :pattern ESCAPE '\\' OR artist LIKE :pattern ESCAPE '\\' OR album LIKE :pattern ESCAPE '\\'")
    fun searchInternal(pattern: String): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    @Query("SELECT SUM(fileSize) FROM songs")
    fun getTotalSize(): Flow<Long?>

    @Query("SELECT COUNT(DISTINCT album) FROM songs")
    fun getAlbumCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT artist) FROM songs WHERE artist != :unknownArtist")
    fun getArtistCount(unknownArtist: String = SongEntity.UNKNOWN_ARTIST): Flow<Int>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    /** 播放统计埋点：递增播放次数并更新时间戳（T10.9） */
    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :id")
    suspend fun incrementPlayCount(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun getFavorites(): Flow<List<SongEntity>>

    // CRITICAL-6: 使用 Upsert 避免 REPLACE 删除再插入导致丢失 ID
    @Upsert
    suspend fun upsert(song: SongEntity)

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    // 保留旧方法名兼容，内部委托给 upsert
    suspend fun insert(song: SongEntity) = upsert(song)
    suspend fun insertAll(songs: List<SongEntity>) = upsertAll(songs)

    @Update
    suspend fun update(song: SongEntity)

    @Delete
    suspend fun delete(song: SongEntity)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("DELETE FROM songs WHERE source = :source")
    suspend fun deleteBySource(source: String)

    // ── 专辑/艺术家/流派浏览（T10.8） ──

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title ASC")
    fun getSongsByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT album FROM songs WHERE album != :unknown ORDER BY album ASC")
    fun getAlbumNames(unknown: String = SongEntity.UNKNOWN_ALBUM): Flow<List<String>>

    @Query("SELECT DISTINCT artist FROM songs WHERE artist != :unknown ORDER BY artist ASC")
    fun getArtistNames(unknown: String = SongEntity.UNKNOWN_ARTIST): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    fun getGenreNames(): Flow<List<String>>

    // ── 播放统计（T10.10） ──

    /** 总播放次数 */
    @Query("SELECT COALESCE(SUM(playCount), 0) FROM songs")
    fun getTotalPlayCount(): Flow<Long>

    /** 播放过的歌曲（按播放次数降序，用于 TOP 榜） */
    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun getTopPlayedSongs(limit: Int): Flow<List<SongEntity>>

    /** 最近 7 天播放过的歌曲（周报） */
    @Query("SELECT * FROM songs WHERE lastPlayedAt >= :since AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC")
    fun getSongsPlayedSince(since: Long): Flow<List<SongEntity>>

    /** 今日播放次数（首页卡片） */
    @Query("SELECT COUNT(*) FROM songs WHERE lastPlayedAt >= :dayStart AND lastPlayedAt > 0")
    fun getTodayPlayCount(dayStart: Long): Flow<Int>

    // ── 听歌报告 SQL 聚合（避免 top200 截断导致总时长/榜单失真） ──

    /** 全量总播放时长 = Σ duration × playCount */
    @Query("SELECT COALESCE(SUM(duration * playCount), 0) FROM songs")
    suspend fun getTotalPlayDurationOnce(): Long

    /** 全量 TOP10 播放歌曲 */
    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT 10")
    suspend fun getTopPlayedSongsOnce(): List<SongEntity>

    /** 全量 TOP10 艺术家（按播放次数聚合） */
    @Query("SELECT artist AS name, SUM(playCount) AS playCount FROM songs WHERE playCount > 0 AND artist != :unknown GROUP BY artist ORDER BY playCount DESC LIMIT 10")
    suspend fun getTopArtistsOnce(unknown: String = SongEntity.UNKNOWN_ARTIST): List<ArtistPlayCount>
}

/** 艺术家播放次数聚合结果 */
data class ArtistPlayCount(
    val name: String,
    val playCount: Long,
)
