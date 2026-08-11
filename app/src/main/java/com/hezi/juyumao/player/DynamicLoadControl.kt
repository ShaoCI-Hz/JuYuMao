package com.hezi.juyumao.player

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动态缓冲 LoadControl：根据用户设置的缓冲大小与当前歌曲是否 HiRes 分档。
 *
 * Media3 的 LoadControl 在构建播放器时固定，无法运行时替换，因此继承 DefaultLoadControl，
 * 在 shouldStartPlayback 时按当前档位动态计算最低缓冲时长：
 * - 普通歌曲：默认 1.5s（对应 256KB 设置）
 * - HiRes 歌曲：翻倍预缓冲，缓解大文件 SMB 串流卡顿
 *
 * 注意：不能用 `LoadControl by delegate` 接口委托——Kotlin 委托不会转发 Java 接口的
 * default 方法（如 getBackBufferDurationUs），会导致 "not implemented" 崩溃。
 */
@Singleton
class DynamicLoadControl @Inject constructor() : DefaultLoadControl() {

    private companion object {
        const val DEFAULT_BUFFER_FOR_PLAYBACK_MS = 1_500
        const val HIRES_MULTIPLIER = 2
    }

    @Volatile
    private var playbackBufferMs: Int = DEFAULT_BUFFER_FOR_PLAYBACK_MS

    @Volatile
    private var isHiResActive: Boolean = false

    /**
     * 由 PlaybackController 在每次播放前调用
     * @param userBufferKb 用户设置的缓冲大小（KB），0 表示默认
     * @param isHiRes 当前歌曲是否 HiRes
     */
    fun updateSettings(userBufferKb: Int, isHiRes: Boolean) {
        // 用户设置单位 KB，换算成播放缓冲毫秒（256KB ≈ 1.5s @320kbps）
        val baseMs = if (userBufferKb > 0) (userBufferKb * 6).coerceIn(1_500, 30_000) else DEFAULT_BUFFER_FOR_PLAYBACK_MS
        playbackBufferMs = if (isHiRes) baseMs * HIRES_MULTIPLIER else baseMs
        isHiResActive = isHiRes
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        // HiRes 歌曲需要更多预缓冲才启动播放，避免 SMB 串流起播即卡
        val requiredUs = playbackBufferMs * 1000L
        if (parameters.bufferedDurationUs < requiredUs) return false
        return super.shouldStartPlayback(parameters)
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        // HiRes 歌曲持续加载到目标缓冲，减少中断
        if (isHiResActive && parameters.bufferedDurationUs < playbackBufferMs * 2L * 1000L) return true
        return super.shouldContinueLoading(parameters)
    }
}
