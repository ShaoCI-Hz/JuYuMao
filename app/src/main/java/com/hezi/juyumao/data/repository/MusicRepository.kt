package com.hezi.juyumao.data.repository

import androidx.room.withTransaction
import com.hezi.juyumao.data.local.db.JuYuMaoDatabase
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.scanner.LocalMusicScanner
import com.hezi.juyumao.data.remote.smb.SmbClientWrapper
import com.hezi.juyumao.data.remote.smb.SmbFileScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val database: JuYuMaoDatabase,
    private val songDao: SongDao,
    private val localMusicScanner: LocalMusicScanner,
    private val smbScanner: SmbFileScanner,
) {

    fun getAllSongsPaged() = songDao.getAllSongsPaged()

    fun getRecentlyPlayed() = songDao.getRecentlyPlayed()

    fun search(query: String) = songDao.search(query)

    fun getSongCount() = songDao.getSongCount()

    fun getTotalSize() = songDao.getTotalSize()

    fun getAlbumCount() = songDao.getAlbumCount()

    fun getArtistCount() = songDao.getArtistCount()

    fun getFavorites() = songDao.getFavorites()

    suspend fun toggleFavorite(songId: Long, isFavorite: Boolean) =
        songDao.updateFavorite(songId, isFavorite)

    // ── 播放统计（T10.10） ──

    fun getTotalPlayCount() = songDao.getTotalPlayCount()

    fun getTopPlayedSongs(limit: Int) = songDao.getTopPlayedSongs(limit)

    fun getSongsPlayedSince(since: Long) = songDao.getSongsPlayedSince(since)

    suspend fun getTotalPlayDurationOnce() = songDao.getTotalPlayDurationOnce()

    suspend fun getTopPlayedSongsOnce() = songDao.getTopPlayedSongsOnce()

    suspend fun getTopArtistsOnce() = songDao.getTopArtistsOnce()

    suspend fun scanSmbDirectory(smbClient: SmbClientWrapper, path: String, serverId: Long): Result<Int> {
        val result = smbScanner.scanDirectory(smbClient, path, serverId)
        return result.map { songs ->
            database.withTransaction {
                // 只清理"本次扫描目录前缀内"的旧记录（防 NAS 已删文件残留幽灵数据，
                // 且不会误删其他目录的歌曲），并合并已存在的收藏/统计状态
                val prefix = if (path.endsWith("/")) path else "$path/"
                val existing = songDao.getSongsByServerOnce(serverId)
                    .filter { it.smbSharePath?.startsWith(prefix) == true }
                    .associateBy { it.smbSharePath }
                val merged = songs.map { new ->
                    val old = existing[new.smbSharePath]
                    if (old != null) {
                        new.copy(
                            id = old.id,
                            isFavorite = old.isFavorite,
                            playCount = old.playCount,
                            lastPlayedAt = old.lastPlayedAt,
                            addedAt = old.addedAt,
                        )
                    } else new
                }
                val newPaths = merged.map { it.smbSharePath }.toSet()
                existing.values.filter { it.smbSharePath !in newPaths }.forEach { songDao.delete(it) }
                songDao.upsertAll(merged)
            }
            songs.size
        }
    }

    suspend fun scanLocalMusic(): Result<Int> {
        return try {
            val result = localMusicScanner.scanAllMusic()
            result.map { songs ->
                database.withTransaction {
                    // 保留旧状态：按 filePath 合并已存在的 isFavorite/playCount/lastPlayedAt/addedAt/id，
                    // 绝不 delete+insert（否则重扫会清空收藏与播放统计、歌单外键级联删歌）
                    val existing = songDao.getAllSongs().first()
                        .filter { it.source == "LOCAL" }
                        .associateBy { it.filePath }
                    val merged = songs.map { new ->
                        val old = existing[new.filePath]
                        if (old != null) {
                            new.copy(
                                id = old.id,
                                isFavorite = old.isFavorite,
                                playCount = old.playCount,
                                lastPlayedAt = old.lastPlayedAt,
                                addedAt = old.addedAt,
                            )
                        } else new
                    }
                    // 差集清理：本地已删除/移走的文件从索引移除，其余 upsert 保留 ID 与统计
                    val newPaths = merged.map { it.filePath }.toSet()
                    existing.values.filter { it.filePath !in newPaths }.forEach { songDao.delete(it) }
                    merged.chunked(200).forEach { batch -> songDao.upsertAll(batch) }
                }
                songs.size
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertSong(song: SongEntity) = songDao.insert(song)

    suspend fun updateSong(song: SongEntity) = songDao.update(song)

    suspend fun deleteSong(song: SongEntity) = songDao.delete(song)
}
