package com.hezi.juyumao.data.repository

import android.content.Context
import com.hezi.juyumao.data.local.artwork.ArtworkCache
import com.hezi.juyumao.data.local.crypto.decryptPassword
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.lyrics.LyricsManager
import com.hezi.juyumao.data.local.metadata.AudioMetadata
import com.hezi.juyumao.data.local.metadata.HiRes
import com.hezi.juyumao.data.local.metadata.MetadataExtractor
import com.hezi.juyumao.data.remote.smb.SmbConnectionPool
import com.hezi.juyumao.player.audio.LyricsData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 元数据统一入口，供 UI 层调用
 */
@Singleton
class MetadataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataExtractor: MetadataExtractor,
    private val lyricsManager: LyricsManager,
    private val artworkCache: ArtworkCache,
    private val smbConnectionPool: SmbConnectionPool,
    private val serverDao: ServerDao,
) {

    /**
     * 提取并缓存封面，返回缓存路径
     */
    suspend fun extractAndCacheArtwork(song: SongEntity): String? {
        // 已有缓存
        artworkCache.getArtworkPath(song.id)?.let { return it }

        // 提取元数据
        val meta = try {
            extractMetadataForSong(song)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 协程取消必须向上传播
        } catch (e: Exception) {
            android.util.Log.w("MetadataRepo", "封面提取失败: ${song.title}", e)
            return null
        }

        // 缓存封面
        val artworkData = meta.artworkData ?: return null
        return artworkCache.saveArtwork(song.id, artworkData)
    }

    /**
     * 获取歌词
     */
    suspend fun getLyrics(song: SongEntity): LyricsData? {
        return lyricsManager.getLyrics(song)
    }

    /**
     * 提取完整元数据
     */
    suspend fun extractMetadata(filePath: String): AudioMetadata {
        return metadataExtractor.extract(filePath)
    }

    /**
     * 获取封面路径（优先缓存，否则提取）
     */
    fun getCachedArtworkPath(songId: Long): String? {
        return artworkCache.getArtworkPath(songId)
    }

    /**
     * 提取完整元数据并更新 SongEntity（歌手/专辑/歌词标志/封面）
     * 返回更新后的实体，供 UI 刷新显示
     */
    suspend fun extractAndUpdateSong(song: SongEntity): SongEntity = extractAndUpdateSong(song, null)

    /**
     * 提取完整元数据并更新 SongEntity（支持外部 SMB 连接，批量缓存用）
     */
    suspend fun extractAndUpdateSong(
        song: SongEntity,
        client: com.hezi.juyumao.data.remote.smb.SmbClientWrapper?,
    ): SongEntity = withContext(Dispatchers.IO) {
        try {
            val meta = extractMetadataForSong(song, client)
            // 缓存封面
            var artPath = artworkCache.getArtworkPath(song.id)
            val artData = meta.artworkData
            if (artPath == null && artData != null && artData.isNotEmpty()) {
                artPath = artworkCache.saveArtwork(song.id, artData)
            }
            song.copy(
                title = meta.title ?: song.title,
                artist = meta.artist ?: song.artist,
                album = meta.album ?: song.album,
                // 以下字段提取失败时必须保留旧值，否则会清空数据库已有内容
                albumArtist = meta.albumArtist ?: song.albumArtist,
                albumArtUri = artPath ?: song.albumArtUri,
                trackNumber = meta.trackNumber ?: song.trackNumber,
                discNumber = meta.discNumber ?: song.discNumber,
                year = meta.year ?: song.year,
                genre = meta.genre ?: song.genre,
                composer = meta.composer ?: song.composer,
                bitrate = if (meta.bitrate > 0) meta.bitrate else song.bitrate,
                sampleRate = if (meta.sampleRate > 0) meta.sampleRate else song.sampleRate,
                bitsPerSample = if (meta.bitsPerSample > 0) meta.bitsPerSample else song.bitsPerSample,
                hasEmbeddedLyrics = meta.embeddedLyrics != null || song.hasEmbeddedLyrics,
                duration = if (meta.duration > 0) meta.duration else song.duration,
                // 用提取到的完整信息刷新 HiRes 判定（SMB 歌曲扫描时只有扩展名判定）
                isHiRes = if (meta.sampleRate > 0 || meta.bitsPerSample > 0) {
                    HiRes.isHiRes(meta.sampleRate, meta.bitsPerSample, song.filePath)
                } else {
                    song.isHiRes
                },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 协程取消必须向上传播，不能静默吞掉
        } catch (e: Exception) {
            android.util.Log.w("MetadataRepo", "元数据提取失败: ${song.title}", e)
            song
        }
    }

    /**
     * 根据歌曲来源提取元数据
     * LOCAL: 直接读本地文件
     * SMB: 下载到临时文件后解析（读取前 1MB 足够提取标签）
     */
    suspend fun extractMetadataForSong(song: SongEntity): AudioMetadata = withContext(Dispatchers.IO) {
        if (song.source == "SMB" && song.smbServerId != null && song.smbSharePath != null) {
            extractSmbMetadata(song, null)
        } else {
            metadataExtractor.extract(song.filePath)
        }
    }

    /**
     * 使用外部提供的 SMB 连接提取元数据（用于批量缓存多线程场景）
     * @param client 外部传入的连接，为 null 时自动从连接池获取
     */
    suspend fun extractMetadataForSong(song: SongEntity, client: com.hezi.juyumao.data.remote.smb.SmbClientWrapper?): AudioMetadata = withContext(Dispatchers.IO) {
        if (song.source == "SMB" && song.smbServerId != null && song.smbSharePath != null) {
            extractSmbMetadata(song, client)
        } else {
            metadataExtractor.extract(song.filePath)
        }
    }

    private suspend fun extractSmbMetadata(
        song: SongEntity,
        externalClient: com.hezi.juyumao.data.remote.smb.SmbClientWrapper?,
    ): AudioMetadata {
        val serverId = song.smbServerId ?: throw IllegalStateException("无服务器 ID")
        val server = serverDao.getServerById(serverId)?.decryptPassword()
            ?: throw IllegalStateException("服务器不存在")

        // 优先使用外部传入的连接（批量缓存多线程场景），否则从连接池获取
        val client = externalClient ?: smbConnectionPool.getConnection(
            serverId = server.id,
            host = server.ip,
            port = server.port,
            username = server.username,
            password = server.password,
            shareName = server.effectiveShareName,
        )

        val sharePath = song.smbSharePath ?: throw IllegalStateException("无共享路径")
        val tempFile = downloadSmbHeadToTemp(client, sharePath, "smb_meta_${song.id}_${System.currentTimeMillis()}")
        try {
            return metadataExtractor.extract(tempFile.absolutePath)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 下载 SMB 文件头部到临时文件（保留原始扩展名，jaudiotagger 按扩展名选解析器）
     * 读取前 8MB 覆盖 ID3v2/FLAC 头部标签（大封面 + 内嵌歌词）
     */
    suspend fun downloadSmbHeadToTemp(
        client: com.hezi.juyumao.data.remote.smb.SmbClientWrapper,
        sharePath: String,
        namePrefix: String,
        maxBytes: Long = 8L * 1024 * 1024,
    ): File {
        val ext = sharePath.substringAfterLast('.', "").ifBlank { "bin" }
        val tempFile = File(context.cacheDir, "${namePrefix}.$ext")
        client.openFile(sharePath).getOrThrow().use { input ->
            // output 流必须 try/finally 关闭（异常时也要关闭并清理临时文件）
            val output = tempFile.outputStream()
            try {
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (total < maxBytes) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    total += n
                    if (total >= maxBytes) break
                }
            } finally {
                try { output.close() } catch (_: Exception) {}
            }
        }
        return tempFile
    }
}
