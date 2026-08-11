package com.hezi.juyumao.data.local.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存管理器：统一管理封面缓存、歌词缓存、NAS 下载歌曲、临时文件
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val TAG = "CacheManager"

        // 缓存目录名
        const val DIR_ALBUM_ART = "album_art"
        const val DIR_NAS_DOWNLOADS = "nas_downloads"
        const val DIR_LYRICS = "lyrics_cache"
        const val DIR_TEMP = "smb_temp"

        // 封面缓存上限
        const val MAX_ALBUM_ART_SIZE = 100L * 1024 * 1024 // 100MB
        // NAS 下载歌曲默认保留
        const val MAX_NAS_DOWNLOAD_SIZE = 1024L * 1024 * 1024 // 1GB

        /** 格式化大小显示 */
        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unit = 0
            while (value >= 1024 && unit < units.size - 1) {
                value /= 1024
                unit++
            }
            return if (unit == 0) "${bytes} ${units[unit]}" else String.format("%.1f %s", value, units[unit])
        }
    }

    // 基础目录：封面/歌词/临时用 cacheDir（系统可清理），NAS 下载用 filesDir（用户数据不丢失）
    private val cacheRoot: File = context.cacheDir
    private val filesRoot: File = File(context.filesDir, "cache").apply { mkdirs() }

    val albumArtDir: File get() = File(cacheRoot, DIR_ALBUM_ART).apply { mkdirs() }
    val nasDownloadDir: File get() = File(filesRoot, DIR_NAS_DOWNLOADS).apply { mkdirs() }
    val lyricsDir: File get() = File(cacheRoot, DIR_LYRICS).apply { mkdirs() }
    val tempDir: File get() = File(cacheRoot, DIR_TEMP).apply { mkdirs() }

    /** 各类缓存大小 */
    data class CacheSizes(
        val albumArt: Long = 0,
        val nasDownloads: Long = 0,
        val lyrics: Long = 0,
        val temp: Long = 0,
    ) {
        val total: Long get() = albumArt + nasDownloads + lyrics + temp
    }

    fun getCacheSizes(): CacheSizes = CacheSizes(
        albumArt = dirSize(albumArtDir),
        nasDownloads = dirSize(nasDownloadDir),
        lyrics = dirSize(lyricsDir),
        temp = dirSize(tempDir),
    )

    /** 已下载的 NAS 歌曲列表 */
    data class CachedNasSong(
        val songId: Long,
        val fileName: String,
        val originalPath: String,
        val file: File,
    )

    fun getCachedNasSongs(): List<CachedNasSong> {
        return nasDownloadDir.listFiles()?.mapNotNull { file ->
            if (!file.isFile) return@mapNotNull null
            // 文件名格式: song_{id}_{originalName}
            val prefix = "song_"
            if (!file.name.startsWith(prefix)) return@mapNotNull null
            val rest = file.name.removePrefix(prefix)
            val idPart = rest.substringBefore('_').toLongOrNull() ?: return@mapNotNull null
            CachedNasSong(
                songId = idPart,
                fileName = rest.substringAfter('_', rest),
                originalPath = file.absolutePath,
                file = file,
            )
        } ?: emptyList()
    }

    /** 下载 NAS 歌曲到本地缓存（带总大小限制）；写/删/清共用同一把锁，避免并发 unlink 正在写入的文件 */
    private val lock = Any()

    /** 下载 NAS 歌曲到本地缓存（带总大小限制） */
    fun saveNasSong(songId: Long, fileName: String, data: java.io.InputStream): File = synchronized(lock) {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = File(nasDownloadDir, "song_${songId}_$safeName")
        file.outputStream().use { out ->
            data.copyTo(out, bufferSize = 64 * 1024)
        }
        // 超过上限时清理最旧的缓存（跳过刚写入的文件，避免单文件超限时被自身清理逻辑删掉）
        enforceNasLimit(justWritten = file)
        file
    }

    /** 保持 NAS 下载缓存总量在上限内（删除最旧文件，排除刚写入的文件） */
    private fun enforceNasLimit(justWritten: File) {
        val files = nasDownloadDir.listFiles()
            ?.filter { it.isFile && it != justWritten }
            ?.sortedBy { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() } + justWritten.length()
        for (file in files) {
            if (total <= MAX_NAS_DOWNLOAD_SIZE) break
            total -= file.length()
            // 删除失败回滚计数，避免记账偏差与后续循环误判
            if (!file.delete()) total += file.length()
        }
    }

    /** 删除单个 NAS 缓存歌曲 */
    fun deleteNasSong(songId: Long): Boolean = synchronized(lock) {
        var deleted = false
        nasDownloadDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("song_${songId}_")) {
                if (file.delete()) deleted = true
            }
        }
        deleted
    }

    /** 清除指定类型的缓存 */
    fun clearCache(clearAlbumArt: Boolean, clearNas: Boolean, clearLyrics: Boolean, clearTemp: Boolean) = synchronized(lock) {
        if (clearAlbumArt) clearDir(albumArtDir)
        if (clearNas) clearDir(nasDownloadDir)
        if (clearLyrics) clearDir(lyricsDir)
        if (clearTemp) clearDir(tempDir)
    }

    /** 清除所有缓存 */
    fun clearAllCache() = synchronized(lock) {
        clearDir(albumArtDir)
        clearDir(nasDownloadDir)
        clearDir(lyricsDir)
        clearDir(tempDir)
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.listFiles()?.sumOf { if (it.isFile) it.length() else dirSize(it) } ?: 0
    }

    private fun clearDir(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) clearDir(file)
            file.delete()
        }
    }
}
