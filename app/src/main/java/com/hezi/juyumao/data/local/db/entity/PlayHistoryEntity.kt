package com.hezi.juyumao.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 播放历史记录（时间线，P2-16）
 */
@Entity(
    tableName = "play_history",
    indices = [Index(value = ["playedAt"]), Index(value = ["songId"])],
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val source: String = "LOCAL",
)

/** 播放历史 + 歌曲信息（JOIN 查询结果） */
data class PlayHistoryWithSong(
    val id: Long,
    val songId: Long,
    val playedAt: Long,
    val source: String,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
)
