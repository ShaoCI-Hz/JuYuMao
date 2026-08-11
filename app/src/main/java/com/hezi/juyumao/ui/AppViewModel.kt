package com.hezi.juyumao.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.crypto.decryptPassword
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.metadata.MetadataBatchProcessor
import com.hezi.juyumao.data.remote.smb.SmbConnectionPool
import com.hezi.juyumao.data.repository.SettingsRepository
import com.hezi.juyumao.player.PlaybackController
import com.hezi.juyumao.player.PlaybackStateHolder
import com.hezi.juyumao.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** 自动重连状态 */
data class ReconnectState(
    val isReconnecting: Boolean = false,
    val message: String? = null,
    val success: Boolean? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val playbackStateHolder: PlaybackStateHolder,
    private val playbackController: PlaybackController,
    private val serverDao: ServerDao,
    private val connectionPool: SmbConnectionPool,
    private val songDao: SongDao,
    private val metadataBatchProcessor: MetadataBatchProcessor,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    /** 首次引导是否完成（T12） */
    val onboardingCompleted: StateFlow<Boolean> = settingsRepository.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentSong: StateFlow<SongEntity?> = playbackStateHolder.currentSong
    val artworkUri: StateFlow<String?> = playbackStateHolder.artworkUri
    val isPlaying: StateFlow<Boolean> = playbackStateHolder.isPlaying
    val position: StateFlow<Long> = playbackStateHolder.position
    val duration: StateFlow<Long> = playbackStateHolder.duration

    private val _reconnectState = MutableStateFlow(ReconnectState())
    val reconnectState: StateFlow<ReconnectState> = _reconnectState.asStateFlow()

    /** 播放错误消息（解码失败等），由 App 层 Snackbar 展示 */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    init {
        // 启动时自动重连已保存的 SMB 服务器
        autoReconnectSavedServers()
        // 播放错误转发为全局提示
        viewModelScope.launch {
            playbackStateHolder.errorMessage.collect { msg ->
                _playbackError.value = msg
            }
        }
    }

    /** 清除播放错误提示（展示后调用） */
    fun clearPlaybackError() {
        _playbackError.value = null
        playbackStateHolder.setErrorMessage(null)
    }

    /** 清空重连提示（提示消失后调用） */
    fun clearReconnectMessage() {
        _reconnectState.value = _reconnectState.value.copy(message = null)
    }

    private fun autoReconnectSavedServers() {
        viewModelScope.launch {
            _reconnectState.value = ReconnectState(isReconnecting = true)
            try {
                val serverList = serverDao.getAutoConnectServers().first()
                // 未配置任何服务器：静默结束，不误报"连接失败"打扰新用户
                if (serverList.isEmpty()) {
                    _reconnectState.value = ReconnectState(isReconnecting = false)
                    return@launch
                }

                // 整体重连最多 20 秒；超时返回 null 时必须补失败状态，避免 UI 永久"重连中"
                val completed = withTimeoutOrNull(20_000) {
                    var anyConnected = false
                    val connectedNames = mutableListOf<String>()
                    for (server in serverList) {
                        val decrypted = try {
                            server.decryptPassword()
                        } catch (e: Exception) {
                            Log.e("AppVM", "解密服务器凭据失败", e)
                            continue // 单台失败不中断整个重连流程
                        }
                        val existing = connectionPool.getExistingConnection(server.id)
                        if (existing == null) {
                            // 最多重试 3 次，间隔递增
                            var success = false
                            repeat(3) { attempt ->
                                if (!success) {
                                    try {
                                        Log.d("AppVM", "自动重连(${attempt + 1}/3): ${decrypted.ip}:${decrypted.port}")
                                        connectionPool.getConnection(
                                            serverId = decrypted.id,
                                            host = decrypted.ip,
                                            port = decrypted.port,
                                            username = decrypted.username,
                                            password = decrypted.password,
                                            shareName = decrypted.effectiveShareName,
                                        )
                                        Log.d("AppVM", "重连成功: ${decrypted.ip}")
                                        success = true
                                        anyConnected = true
                                        connectedNames.add(decrypted.ip)
                                    } catch (e: Exception) {
                                        Log.e("AppVM", "重连失败(${attempt + 1}/3): ${decrypted.ip}", e)
                                        kotlinx.coroutines.delay((attempt + 1) * 2000L)
                                    }
                                }
                            }
                        } else {
                            anyConnected = true
                            connectedNames.add(decrypted.ip)
                        }
                    }

                    // 重连成功且存在未缓存的 NAS 歌曲时，触发批量元数据缓存
                    if (anyConnected) {
                        val smbSongs = songDao.getAllSongs().first().filter { it.source == "SMB" }
                        val needCache = smbSongs.filter { !it.isMetadataCached() }
                        if (needCache.isNotEmpty()) {
                            Log.d("AppVM", "触发批量缓存 ${needCache.size} 首（已缓存 ${smbSongs.size - needCache.size} 首跳过）")
                            metadataBatchProcessor.processSongs(needCache)
                        }
                        val summary = if (connectedNames.isEmpty()) "已连接 NAS" else "已连接 NAS: ${connectedNames.joinToString("、")}"
                        _reconnectState.value = ReconnectState(isReconnecting = false, message = summary, success = true)
                    } else {
                        _reconnectState.value = ReconnectState(
                            isReconnecting = false,
                            message = "自动连接失败，请手动连接 NAS",
                            success = false,
                        )
                    }
                    true
                }

                if (completed == null) {
                    // 20s 超时（单服务器 3 次重试 × 15s 超时最坏 45s > 20s）：补超时失败态
                    _reconnectState.value = ReconnectState(
                        isReconnecting = false,
                        message = "自动连接超时，请手动连接 NAS",
                        success = false,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // 协程取消必须向上传播
            } catch (e: Exception) {
                Log.e("AppVM", "自动重连异常", e)
                _reconnectState.value = ReconnectState(
                    isReconnecting = false,
                    message = "自动连接异常",
                    success = false,
                )
            }
        }
    }

    fun togglePlay() {
        // 委托给 PlaybackController：保证交叉淡化等统一行为，且 isPlaying 由 ExoPlayer 回调同步
        playbackController.togglePlay()
    }

    /** 整单播放（歌单/专辑/艺术家详情共用） */
    fun playSongs(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        playbackController.loadPlaylist(songs, 0)
    }

    /** 标记引导完成（T12） */
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    /** 重新查看引导（设置页入口） */
    fun resetOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(false)
        }
    }
}

/**
 * 判断歌曲元数据是否已缓存
 * 只有封面已缓存才算完成（封面是浏览列表展示的关键，歌手缺失会重新提取）
 */
private fun SongEntity.isMetadataCached(): Boolean =
    !albumArtUri.isNullOrEmpty()
