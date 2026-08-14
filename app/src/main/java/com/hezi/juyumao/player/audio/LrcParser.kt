package com.hezi.juyumao.player.audio

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord>? = null,
)

@Immutable
data class LyricWord(
    val timeMs: Long,
    val durationMs: Long,
    val text: String,
)

@Immutable
data class LyricsData(
    val lines: List<LyricLine>,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

object LrcParser {

    private val timePattern = Regex("""\[(\d{2}):(\d{2})[.:](\d{2,3})]""")
    private val metaPattern = Regex("""\[(\w+):(.+?)]""")
    // 逐字时间标签：<mm:ss.xx>（QQ/网易云增强 LRC 格式）
    private val wordTimePattern = Regex("""<(\d{2}):(\d{2})[.:](\d{2,3})>""")

    fun parse(lrcContent: String): LyricsData {
        val lines = mutableListOf<LyricLine>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        for (line in lrcContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check for metadata
            val metaMatch = metaPattern.matchEntire(trimmed)
            if (metaMatch != null) {
                val key = metaMatch.groupValues[1]
                val value = metaMatch.groupValues[2]
                when (key) {
                    "ti" -> title = value
                    "ar" -> artist = value
                    "al" -> album = value
                }
                continue
            }

            // Parse timed lyrics: [mm:ss.xx]text
            val timeMatches = timePattern.findAll(trimmed).toList()
            if (timeMatches.isEmpty()) continue

            val rawText = trimmed.substringAfterLast("]").trim()
            if (rawText.isEmpty()) continue

            // 逐字歌词：剥离 <时间> 标签得纯文本，另存逐字时间序列
            val plainText = rawText.replace(wordTimePattern, "").trim()
            if (plainText.isEmpty()) continue
            val words = parseLineWords(rawText)

            for (timeMatch in timeMatches) {
                val totalMs = parseTimeMs(timeMatch.groupValues)
                lines.add(LyricLine(timeMs = totalMs, text = plainText, words = words))
            }
        }

        return LyricsData(
            lines = lines.sortedBy { it.timeMs },
            title = title,
            artist = artist,
            album = album,
        )
    }

    /** 解析逐字时间：<mm:ss.xx>字<mm:ss.xx>字...；无时间标签返回 null */
    private fun parseLineWords(rawText: String): List<LyricWord>? {
        if (!rawText.contains('<')) return null
        val timeMatches = wordTimePattern.findAll(rawText).toList()
        if (timeMatches.isEmpty()) return null
        val result = mutableListOf<LyricWord>()
        var lastPos = 0
        for (m in timeMatches) {
            val seg = rawText.substring(lastPos, m.range.first)
            if (seg.isNotEmpty()) {
                val t = parseTimeMs(m.groupValues)
                result.add(LyricWord(timeMs = t, durationMs = 500, text = seg))
            }
            lastPos = m.range.last + 1
        }
        val tail = rawText.substring(lastPos)
        if (tail.isNotEmpty() && result.isNotEmpty()) {
            result.add(LyricWord(timeMs = result.last().timeMs + 500, durationMs = 500, text = tail))
        }
        // 计算每字时长（下一字时间差，最小 100ms）
        for (i in 0 until result.size - 1) {
            result[i] = result[i].copy(
                durationMs = (result[i + 1].timeMs - result[i].timeMs).coerceAtLeast(100)
            )
        }
        return result
    }

    private fun parseTimeMs(g: List<String>): Long {
        val minutes = g[1].toLong()
        val seconds = g[2].toLong()
        val millis = g[3].let { if (it.length == 2) it.toLong() * 10 else it.toLong() }
        return minutes * 60_000 + seconds * 1000 + millis
    }

    fun findCurrentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
