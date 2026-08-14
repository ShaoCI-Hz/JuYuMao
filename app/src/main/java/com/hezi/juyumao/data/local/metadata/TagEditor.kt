package com.hezi.juyumao.data.local.metadata

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * 本地歌曲 tag 编辑（P2-14）：写入文件内嵌标签（标题/艺术家/专辑/流派/年份）。
 * 仅支持本地文件（NAS 只读）。写入成功需调用方同步更新数据库。
 */
object TagEditor {

    /**
     * 编辑本地歌曲标签并提交到文件。
     * 传 null 的字段保持原值不变。
     */
    fun editLocalTags(
        file: File,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        genre: String? = null,
        year: Int? = null,
    ): Result<Unit> = runCatching {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        title?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TITLE, it) }
        artist?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ARTIST, it) }
        album?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ALBUM, it) }
        genre?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.GENRE, it) }
        year?.takeIf { it in 1900..2100 }?.let { tag.setField(FieldKey.YEAR, it.toString()) }
        audioFile.commit()
    }
}
