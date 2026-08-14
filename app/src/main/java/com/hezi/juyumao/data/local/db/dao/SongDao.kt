package com.hezi.juyumao.data.local.db.dao

import androidx.room.*
import com.hezi.juyumao.data.local.db.entity.PlayHistoryEntity
import com.hezi.juyumao.data.local.db.entity.PlayHistoryWithSong
import com.hezi.juyumao.data.local.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/** 播放时段统计行（Room POJO） */
data class PlayHourCount(
    val hour: Int,
    val cnt: Int,
)

// 转义 LIKE 查询中的通配符
private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY addedAt DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY addedAt DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    /** 一次性取某台 SMB 服务器的全部歌曲（扫描差集清理用） */
    @Query("SELECT * FROM songs WHERE smbServerId = :serverId")
    suspend fun getSongsByServerOnce(serverId: Long): List<SongEntity>

    /** 删除某台 SMB 服务器的全部歌曲（删除服务器时清理，避免幽灵数据） */
    @Query("DELETE FROM songs WHERE smbServerId = :serverId")
    suspend fun deleteSongsByServer(serverId: Long)

    @Query("SELECT * FROM songs ORDER BY lastPlayedAt DESC LIMIT 10")
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

    /** 更新星级评分（0-5） */
    @Query("UPDATE songs SET rating = :rating WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Int)

    /** 仅更新标签字段（P1-14 修复：避免全字段回写覆盖并发变更） */
    @Query(
        "UPDATE songs SET title = :title, artist = :artist, album = :album, " +
            "genre = :genre, year = :year WHERE id = :id"
    )
    suspend fun updateTags(id: Long, title: String, artist: String, album: String, genre: String?, year: Int)

    // ── 批量操作（P0-4）──

    @Query("UPDATE songs SET isFavorite = :fav WHERE id IN (:ids)")
    suspend fun updateFavoriteBatch(ids: List<Long>, fav: Boolean)

    @Query("UPDATE songs SET rating = :rating WHERE id IN (:ids)")
    suspend fun updateRatingBatch(ids: List<Long>, rating: Int)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    // ── 相似歌曲（P1-9）──

    /** 相似歌曲：同艺术家或同流派（排除自身），按播放次数排序 */
    @Query(
        "SELECT * FROM songs WHERE id != :excludeId " +
            "AND (artist = :artist OR (:genre IS NOT NULL AND :genre != '' AND genre = :genre)) " +
            "ORDER BY playCount DESC LIMIT :limit"
    )
    suspend fun getSimilarSongs(excludeId: Long, artist: String, genre: String?, limit: Int): List<SongEntity>

    // ── 播放时段分布（P1-10）──

    /** 各小时播放次数（用于时段分布统计） */
    @Query(
        "SELECT CAST(strftime('%H', playedAt / 1000.0, 'unixepoch', 'localtime') AS INTEGER) AS hour, " +
            "COUNT(*) AS cnt FROM play_history GROUP BY hour"
    )
    suspend fun getPlayHourDistribution(): List<PlayHourCount>

    // ── 播放历史（P2-16）──

    @Insert
    suspend fun insertPlayHistory(history: PlayHistoryEntity)

    @Query(
        "SELECT h.id, h.songId, h.playedAt, h.source, s.title, s.artist, s.albumArtUri " +
            "FROM play_history h JOIN songs s ON h.songId = s.id " +
            "ORDER BY h.playedAt DESC LIMIT :limit"
    )
    fun getRecentPlayHistory(limit: Int): Flow<List<PlayHistoryWithSong>>

    // ── 智能歌单查询（P0-3）──

    @Query(
        "SELECT * FROM songs WHERE rating >= :minRating AND playCount >= :minPlayCount " +
            "AND (:genre IS NULL OR :genre = '' OR genre = :genre) " +
            "AND (:source IS NULL OR :source = '' OR source = :source) " +
            "AND (:onlyFav = 0 OR isFavorite = 1) " +
            "AND (:withinDays = 0 OR addedAt >= :cutoff) " +
            "ORDER BY addedAt DESC"
    )
    suspend fun querySmartPlaylist(
        minRating: Int,
        minPlayCount: Int,
        genre: String?,
        source: String?,
        onlyFav: Int,
        withinDays: Int,
        cutoff: Long,
    ): List<SongEntity>

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
