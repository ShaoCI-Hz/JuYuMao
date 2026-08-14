package com.hezi.juyumao.data.local.metadata

import org.jaudiotagger.audio.AudioFileIO
import java.io.File

/**
 * ReplayGain 读取（P1-8）：读取文件内嵌的 ReplayGain 曲目增益（dB）。
 * 用于播放时音量归一化，解决不同专辑响度跳变。
 */
object ReplayGainReader {

    /** 读取 Track Gain（dB），无标签返回 null */
    fun readTrackGain(file: File): Float? = try {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag ?: return null
        val raw = tag.getFirst("REPLAYGAIN_TRACK_GAIN")
            .ifBlank { tag.getFirst("replaygain_track_gain") }
        if (raw.isBlank()) null
        else raw.replace(" dB", "").trim().toFloatOrNull()
    } catch (_: Exception) {
        null
    }
}
