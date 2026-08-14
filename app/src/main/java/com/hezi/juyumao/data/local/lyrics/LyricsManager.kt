package com.hezi.juyumao.data.local.lyrics

import android.content.Context
import com.hezi.juyumao.data.local.crypto.decryptPassword
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.metadata.MetadataExtractor
import com.hezi.juyumao.data.remote.smb.SmbConnectionPool
import com.hezi.juyumao.player.audio.LrcParser
import com.hezi.juyumao.player.audio.LyricsData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataExtractor: MetadataExtractor,
    private val smbConnectionPool: SmbConnectionPool,
    private val serverDao: ServerDao,
) {

    suspend fun getLyrics(song: SongEntity): LyricsData? = withContext(Dispatchers.IO) {
        when (song.source) {
            "LOCAL" -> getLyricsLocal(song.filePath)
            "SMB" -> getLyricsSmb(song)
            else -> null
        }
    }

    // ── 在线歌词（P1-7）：lrclib.net 公开 API，本地缓存 ──

    private val onlineLyricsDir: File
        get() = File(context.cacheDir, "lyrics_online").apply { mkdirs() }

    /** 读取已缓存的在线歌词 */
    private fun readOnlineLyricsCache(songId: Long): LyricsData? {
        val f = File(onlineLyricsDir, "$songId.lrc")
        if (!f.exists()) return null
        return try {
            LrcParser.parse(f.readText())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 在线获取歌词（lrclib.net），成功缓存到本地。
     * 返回 null 表示无结果/失败/未开启。调用方需自行判断开关。
     */
    suspend fun fetchOnlineLyrics(song: SongEntity): LyricsData? = withContext(Dispatchers.IO) {
        // 先查缓存
        readOnlineLyricsCache(song.id)?.let { return@withContext it }
        try {
            val track = java.net.URLEncoder.encode(song.title, "UTF-8")
            val artist = java.net.URLEncoder.encode(song.artist, "UTF-8")
            val url = java.net.URL(
                "https://lrclib.net/api/search?track_name=$track&artist_name=$artist"
            )
            val conn = url.openConnection() as java.net.HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                // 不跟随重定向，避免恶意响应指向任意 host
                conn.instanceFollowRedirects = false
                if (conn.responseCode != 200) return@withContext null
                // 限制读取上限 256KB，避免异常大响应 OOM
                val body = conn.inputStream.use { ins ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (total < 256 * 1024) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        buffer.write(buf, 0, n)
                        total += n
                    }
                    buffer.toString("UTF-8")
                }
                // 解析 JSON：取第一条 syncedLyrics
                val synced = extractSyncedLyrics(body) ?: return@withContext null
                val parsed = LrcParser.parse(synced)
                if (parsed.lines.isEmpty()) return@withContext null
                // 缓存
                try {
                    File(onlineLyricsDir, "${song.id}.lrc").writeText(synced)
                } catch (_: Exception) {}
                parsed
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 极简 JSON 提取 syncedLyrics 字段（避免引入 JSON 库） */
    private fun extractSyncedLyrics(json: String): String? {
        val key = "\"syncedLyrics\":"
        val idx = json.indexOf(key)
        if (idx < 0) return null
        var start = idx + key.length
        while (start < json.length && json[start].isWhitespace()) start++
        if (start >= json.length || json[start] != '"') return null
        // 找字符串结束（处理转义引号）
        val sb = StringBuilder()
        var i = start + 1
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                val n = json[i + 1]
                sb.append(if (n == 'n') '\n' else n)
                i += 2
                continue
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return if (sb.isNotEmpty()) sb.toString() else null
    }

    private suspend fun getLyricsLocal(filePath: String): LyricsData? {
        findExternalLrc(filePath)?.let { return it }
        findEmbeddedLyrics(filePath)?.let { return it }
        findExternalTxt(filePath)?.let { return it }
        return null
    }

    private suspend fun getLyricsSmb(song: SongEntity): LyricsData? {
        findSmbLrc(song)?.let { return it }
        findSmbEmbeddedLyrics(song)?.let { return it }
        return null
    }

    private suspend fun findSmbEmbeddedLyrics(song: SongEntity): LyricsData? {
        try {
            val serverId = song.smbServerId ?: return null
            val server = serverDao.getServerById(serverId)?.decryptPassword() ?: return null
            val client = smbConnectionPool.getConnection(
                serverId = server.id,
                host = server.ip,
                port = server.port,
                username = server.username,
                password = server.password,
                shareName = server.effectiveShareName,
            )

            val sharePath = song.smbSharePath ?: return null
            val ext = sharePath.substringAfterLast('.', "").ifBlank { "bin" }
            // 必须保留原始扩展名，jaudiotagger 按扩展名选择解析器，.tmp 会解析失败读不到歌词；
            // 用 nanoTime 命名避免同毫秒并发请求互相覆盖
            val tempFile = File(context.cacheDir, "smb_lyr_${song.id}_${System.nanoTime()}.$ext")
            try {
                client.openFile(sharePath).getOrThrow().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        // 只读头部 1MB 足够提取 ID3/FLAC 内嵌歌词（原 8MB 导致首页每首 SMB 歌
                        // 大流量下载 + 临时文件 IO，拖慢 UI）
                        val maxRead = 1L * 1024 * 1024
                        while (total < maxRead) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            total += n
                            if (total >= maxRead) break
                        }
                    }
                }
                val meta = metadataExtractor.extract(tempFile.absolutePath)
                val lyricsText = meta.embeddedLyrics ?: return null
                return parseLyricsContent(lyricsText)
            } finally {
                tempFile.delete()
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun findExternalLrc(filePath: String): LyricsData? {
        val songFile = File(filePath)
        val baseName = songFile.nameWithoutExtension
        val parentDir = songFile.parentFile ?: return null
        val lrcFile = parentDir.listFiles()?.find {
            it.extension.lowercase() == "lrc" &&
            it.nameWithoutExtension.equals(baseName, ignoreCase = true)
        } ?: return null
        return parseLyricsFile(lrcFile)
    }

    private fun findExternalTxt(filePath: String): LyricsData? {
        val songFile = File(filePath)
        val baseName = songFile.nameWithoutExtension
        val parentDir = songFile.parentFile ?: return null
        val txtFile = parentDir.listFiles()?.find {
            it.extension.lowercase() == "txt" &&
            it.nameWithoutExtension.equals(baseName, ignoreCase = true)
        } ?: return null
        return parsePlainText(decodeSmart(txtFile.readBytes()))
    }

    private suspend fun findEmbeddedLyrics(filePath: String): LyricsData? {
        val meta = metadataExtractor.extract(filePath)
        val lyricsText = meta.embeddedLyrics ?: return null
        return parseLyricsContent(lyricsText)
    }

    private suspend fun findSmbLrc(song: SongEntity): LyricsData? {
        val serverId = song.smbServerId ?: return null
        val sharePath = song.smbSharePath ?: return null
        val server = serverDao.getServerById(serverId)?.decryptPassword() ?: return null

        try {
            val client = smbConnectionPool.getConnection(
                serverId = serverId,
                host = server.ip,
                port = server.port,
                username = server.username,
                password = server.password,
                shareName = server.effectiveShareName,
            )
            val parentDir = sharePath.substringBeforeLast('/')
            val baseName = sharePath.substringAfterLast('/').substringBeforeLast('.')

            val files = client.listFiles(parentDir).getOrNull() ?: return null
            val lrcFile = files.find {
                !it.isDirectory &&
                it.name.endsWith(".lrc", ignoreCase = true) &&
                it.name.substringBeforeLast('.').equals(baseName, ignoreCase = true)
            } ?: return null

            val stream = client.openFile(lrcFile.path).getOrThrow()
            val content = decodeSmart(stream.use { it.readBytes() })
            return parseLyricsContent(content)
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseLyricsFile(file: File): LyricsData? {
        return try {
            parseLyricsContent(decodeSmart(file.readBytes()))
        } catch (_: Exception) { null }
    }

    private fun parseLyricsContent(content: String): LyricsData? {
        // 含 [mm:ss] 时间标签结构但解析后无行（如仅元数据标签/编码损坏）→ 视为无歌词，
        // 不降级纯文本（否则 [ti:xxx]/[ar:xxx] 元数据会当歌词显示）
        val hasTimeTags = TIME_TAG_REGEX.containsMatchIn(content)
        val lrcData = LrcParser.parse(content)
        if (lrcData.lines.isNotEmpty()) return lrcData
        if (hasTimeTags) return null
        return parsePlainText(content)
    }

    private companion object {
        /** 宽松探测时间标签（LrcParser 严格要求两位分钟，这里兼容单数字分钟） */
        val TIME_TAG_REGEX = Regex("""\[\d{1,2}:\d{2}""")
    }

    /**
     * 智能解码：严格 UTF-8 优先（失败回退 GBK/GB2312——中文歌词的常态编码，
     * 硬编码 UTF-8 会把 GBK 字节替换为 U+FFFD 显示乱码）
     */
    private fun decodeSmart(bytes: ByteArray): String {
        // BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val utf8 = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }
        if (utf8 != null && !utf8.contains('\uFFFD')) return utf8
        return String(bytes, Charset.forName("GBK"))
    }

    private fun parsePlainText(content: String): LyricsData? {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        // 纯文本歌词不分配假时间戳，返回单行时间 0
        return LyricsData(
            lines = lines.map { text ->
                com.hezi.juyumao.player.audio.LyricLine(timeMs = 0L, text = text.trim())
            },
        )
    }
}
