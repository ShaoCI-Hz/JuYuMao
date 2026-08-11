package com.hezi.juyumao.data.local.scanner

import android.content.Context
import android.provider.MediaStore
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.metadata.HiRes
import com.hezi.juyumao.data.local.metadata.MetadataExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataExtractor: MetadataExtractor,
) {
    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "aac", "m4a", "ogg", "opus", "wma", "wav", "flac",
            "dsf", "dff", "ape", "wv", "aiff", "aif",
        )
        private val EXCLUDED_DIR_PATTERNS = listOf(
            "/Notifications/", "/Ringtones/", "/Alarms/",
            "/Recordings/", "/Voice Recorder/",
            "/Android/data/", "/Android/obb/",
            "/.微信/", "/Tencent/", "/tencent/",
            "/WhatsApp/", "/com.",
        )
        private val EXCLUDED_FILENAME_KEYWORDS = listOf(
            "ringtone", "notification", "alarm", "voice_record", "wechat", "微信语音",
        )
        private const val MIN_DURATION_MS = 30_000L
        private const val MIN_FILE_SIZE_BYTES = 100_000L
    }

    suspend fun scanAllMusic(): Result<List<SongEntity>> = withContext(Dispatchers.IO) {
        try {
            val songs = mutableListOf<SongEntity>()
            val seenPaths = HashSet<String>()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.IS_NOTIFICATION,
            )

            // 排除正在下载/复制中的不完整文件（API 29+）
            val selection = "${MediaStore.Audio.Media.IS_PENDING}=0"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val isMusicCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
                val isRingtoneCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)
                val isAlarmCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM)
                val isNotifCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION)

                while (cursor.moveToNext()) {
                    val filePath = cursor.getString(dataCol) ?: continue
                    val duration = cursor.getLong(durationCol)
                    val fileSize = cursor.getLong(sizeCol)
                    val title = cursor.getString(titleCol)
                    val isMusic = cursor.getInt(isMusicCol)
                    val isRingtone = cursor.getInt(isRingtoneCol)
                    val isAlarm = cursor.getInt(isAlarmCol)
                    val isNotif = cursor.getInt(isNotifCol)

                    if (!isValidMusicFile(filePath, duration, fileSize, title, isMusic, isRingtone, isAlarm, isNotif)) continue
                    // 去重保留大小写（仅大小写不同的两个真实文件不应被误判为重复）
                    if (!seenPaths.add(filePath)) continue

                    // 基础元数据来自 MediaStore
                    val mediaStoreArtist = cursor.getString(artistCol) ?: "未知艺术家"
                    val mediaStoreAlbum = cursor.getString(albumCol) ?: "未知专辑"
                    val mimeType = cursor.getString(mimeCol) ?: inferMimeType(filePath)

                    // 尝试用 jaudiotagger 提取增强元数据
                    var enhancedTitle: String? = null
                    var enhancedArtist: String? = null
                    var enhancedAlbum: String? = null
                    var enhancedAlbumArtist: String? = null
                    var trackNumber = 0
                    var discNumber = 0
                    var year = 0
                    var genre: String? = null
                    var composer: String? = null
                    var bitrate = 0
                    var sampleRate = 0
                    var bitsPerSample = 0
                    var hasEmbeddedLyrics = false
                    var artworkPath: String? = null

                    try {
                        val meta = metadataExtractor.extract(filePath)

                        // 使用增强元数据（如果有的话）
                        enhancedTitle = meta.title
                        enhancedArtist = meta.artist
                        enhancedAlbum = meta.album
                        enhancedAlbumArtist = meta.albumArtist
                        trackNumber = meta.trackNumber ?: 0
                        discNumber = meta.discNumber ?: 0
                        year = meta.year ?: 0
                        genre = meta.genre
                        composer = meta.composer
                        bitrate = meta.bitrate
                        sampleRate = meta.sampleRate
                        bitsPerSample = meta.bitsPerSample
                        hasEmbeddedLyrics = meta.embeddedLyrics != null
                        // 注意：封面不在此处写盘（扫描阶段无 DB id 作缓存键，写了也对不上），
                        // 由 MetadataRepository.extractAndUpdateSong 在 UI 首次展示时按需缓存（避免 O(N²) 写盘）
                    } catch (_: Exception) {
                        // 元数据提取失败，使用 MediaStore 数据
                    }

                    // 检查是否有外挂 .lrc
                    val hasExternalLrc = checkExternalLrc(filePath)

                    songs.add(
                        SongEntity(
                            title = enhancedTitle ?: title ?: filePath.substringAfterLast('/').substringBeforeLast('.'),
                            artist = enhancedArtist ?: mediaStoreArtist,
                            album = enhancedAlbum ?: mediaStoreAlbum,
                            albumArtist = enhancedAlbumArtist,
                            albumArtUri = artworkPath,
                            duration = duration,
                            filePath = filePath,
                            fileSize = fileSize,
                            mimeType = mimeType,
                            isHiRes = HiRes.isHiRes(sampleRate, bitsPerSample, filePath),
                            source = "LOCAL",
                            smbServerId = null,
                            smbSharePath = null,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            year = year,
                            genre = genre,
                            composer = composer,
                            bitrate = bitrate,
                            sampleRate = sampleRate,
                            bitsPerSample = bitsPerSample,
                            hasEmbeddedLyrics = hasEmbeddedLyrics,
                            hasExternalLyrics = hasExternalLrc,
                        )
                    )
                }
            }

            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isValidMusicFile(
        filePath: String, duration: Long, fileSize: Long, title: String?,
        isMusic: Int, isRingtone: Int, isAlarm: Int, isNotification: Int,
    ): Boolean {
        // IS_MUSIC 是系统启发式标志，MTP 拷贝/Telegram 等来源的真实音乐常为 0，不硬性依赖；
        // 改为显式排除铃声/闹钟/通知（这三个标志才是确定性排除项）
        if (isRingtone == 1 || isAlarm == 1 || isNotification == 1) return false
        val ext = filePath.substringAfterLast('.', "").lowercase()
        if (ext !in AUDIO_EXTENSIONS) return false
        if (duration < MIN_DURATION_MS) return false
        if (fileSize < MIN_FILE_SIZE_BYTES) return false
        val pathLower = filePath.lowercase()
        for (pattern in EXCLUDED_DIR_PATTERNS) {
            if (pathLower.contains(pattern.lowercase())) return false
        }
        val fileName = filePath.substringAfterLast('/').lowercase()
        for (keyword in EXCLUDED_FILENAME_KEYWORDS) {
            if (fileName.contains(keyword.lowercase())) return false
        }
        return true
    }

    /** 检查同目录是否有外挂 .lrc 文件 */
    private fun checkExternalLrc(filePath: String): Boolean {
        val file = java.io.File(filePath)
        val baseName = file.nameWithoutExtension
        val parentDir = file.parentFile ?: return false
        return parentDir.listFiles()?.any {
            it.extension.lowercase() == "lrc" &&
            it.nameWithoutExtension.equals(baseName, ignoreCase = true)
        } ?: false
    }

    private fun inferMimeType(filePath: String): String {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"; "aac", "m4a" -> "audio/mp4"; "flac" -> "audio/flac"
            "wav" -> "audio/wav"; "ogg" -> "audio/ogg"; "opus" -> "audio/opus"
            "dsf", "dff" -> "audio/dsd"; "ape" -> "audio/ape"; "wv" -> "audio/wavpack"
            "wma" -> "audio/x-ms-wma"; "aiff", "aif" -> "audio/aiff"
            else -> "audio/*"
        }
    }
}
