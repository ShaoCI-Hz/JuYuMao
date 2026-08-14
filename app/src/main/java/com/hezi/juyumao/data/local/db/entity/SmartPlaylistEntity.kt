package com.hezi.juyumao.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 智能歌单（Smart Playlist，P0-3）：
 * 按条件动态筛选歌曲（评分/播放次数/最近添加/流派/来源/仅收藏），无需手工维护。
 */
@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minRating: Int = 0,          // 评分 ≥ 该值（0 = 不限）
    val minPlayCount: Int = 0,       // 播放次数 ≥ 该值（0 = 不限）
    val addedWithinDays: Int = 0,    // 最近 N 天内添加（0 = 不限）
    val genre: String? = null,       // 流派筛选（null = 不限）
    val source: String? = null,      // "LOCAL" / "SMB"（null = 不限）
    val isFavoriteOnly: Boolean = false, // 仅收藏
)
