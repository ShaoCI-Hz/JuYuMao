package com.hezi.juyumao.data.local.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheDir = File(context.cacheDir, "album_art").apply { mkdirs() }

    // HIGH: 基于内存大小的 LruCache，避免 OOM
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val memoryCache = object : LruCache<String, Bitmap>(maxMemory) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** 低频清理计数：每写 N 次做一次全目录清理（避免每首歌都 O(n) 扫描，批量扫描时 O(N²)） */
    private var writesSinceCleanup = 0

    fun getArtworkPath(songId: Long): String? {
        val file = File(cacheDir, "art_$songId.jpg")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    fun saveArtwork(songId: Long, artworkData: ByteArray): String {
        val file = File(cacheDir, "art_$songId.jpg")
        // 唯一临时名：并发写同一 songId 不再互相覆盖/误删
        val tmp = File(cacheDir, "art_${songId}_${System.nanoTime()}.tmp")
        try {
            tmp.writeBytes(artworkData)
            // rename 失败必须上报，否则调用方拿到"不存在的路径"写入 albumArtUri 后永久判定已缓存
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw IOException("封面写入失败: rename failed")
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        // 覆写后使内存缓存中的旧 Bitmap 失效，避免永远返回旧封面
        memoryCache.remove(file.absolutePath)
        // 低频清理磁盘缓存
        if (++writesSinceCleanup >= 20) {
            writesSinceCleanup = 0
            cleanup()
        }
        return file.absolutePath
    }

    fun getBitmap(path: String): Bitmap? = memoryCache.get(path)

    fun putBitmap(path: String, bitmap: Bitmap) = memoryCache.put(path, bitmap)

    /** 按长边采样解码（超大封面直接全尺寸解码会 OOM） */
    fun decodeBitmap(path: String, maxSize: Int = 2048): Bitmap? {
        getBitmap(path)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        putBitmap(path, bitmap)
        return bitmap
    }

    fun cleanup(maxSizeBytes: Long = 100L * 1024 * 1024) {
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var totalSize = files.sumOf { it.length() }
        for (file in files) {
            if (totalSize <= maxSizeBytes) break
            totalSize -= file.length()
            file.delete()
        }
    }
}
