package com.hezi.juyumao.ui.smb

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.crypto.decryptPassword
import com.hezi.juyumao.data.local.crypto.encryptPassword
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.entity.ServerEntity
import com.hezi.juyumao.data.remote.discovery.DiscoveredServer
import com.hezi.juyumao.data.local.metadata.MetadataBatchProcessor
import com.hezi.juyumao.data.remote.discovery.SmbDiscovery
import com.hezi.juyumao.data.remote.smb.*
import com.hezi.juyumao.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmbConnectUiState(
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val scannedHosts: List<NetworkScanner.ScannedHost> = emptyList(),
    val savedServers: List<ServerEntity> = emptyList(),
    val connectionState: SmbConnectionState = SmbConnectionState.Disconnected,
    val errorMessage: String? = null,
    val connectSuccess: Boolean = false,
    val availableShares: List<ShareDiscovery.DiscoveredShare> = emptyList(),
    val isDiscoveringShares: Boolean = false,
    val currentServerIp: String = "",
    val currentServerPort: Int = 445,
    val isScanningMusic: Boolean = false,
    val scanMusicMessage: String = "",
    val scannedSongCount: Int = 0,
    val isConnected: Boolean = false,
    val connectedServerName: String = "",
)

@HiltViewModel
class SmbConnectViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val connectionPool: SmbConnectionPool,
    private val discovery: SmbDiscovery,
    private val networkScanner: NetworkScanner,
    private val shareDiscovery: ShareDiscovery,
    private val musicRepository: MusicRepository,
    private val songDao: com.hezi.juyumao.data.local.db.dao.SongDao,
    private val metadataBatchProcessor: MetadataBatchProcessor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmbConnectUiState())
    val uiState: StateFlow<SmbConnectUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            serverDao.getAllServers().collect { servers ->
                _uiState.value = _uiState.value.copy(savedServers = servers)
            }
        }
        // 检查是否有已存在的连接
        checkExistingConnection()
    }

    private fun checkExistingConnection() {
        // 检查连接池中是否有活跃连接
        // 这样用户从主页返回时不会丢失连接状态
    }

    fun discover() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, discoveredServers = emptyList())
            val result = discovery.discover()
            result.fold(
                onSuccess = { servers ->
                    _uiState.value = _uiState.value.copy(isScanning = false, discoveredServers = servers)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isScanning = false, errorMessage = "扫描失败: ${e.message}")
                },
            )
        }
    }

    fun scanNetwork() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, scannedHosts = emptyList(), scanProgress = 0)
            val hosts = networkScanner.scanLocalNetwork(
                onProgress = { scanned, found ->
                    _uiState.value = _uiState.value.copy(scanProgress = scanned, scannedHosts = found)
                },
                timeoutMs = 300,
            )
            _uiState.value = _uiState.value.copy(isScanning = false, scannedHosts = hosts, scanProgress = 254)
        }
    }

    fun discoverShares(host: String, port: Int, username: String, password: String, domain: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDiscoveringShares = true, availableShares = emptyList(),
                currentServerIp = host, currentServerPort = port,
            )
            val shares = shareDiscovery.discoverShares(host, port, username, password, domain)
            _uiState.value = _uiState.value.copy(isDiscoveringShares = false, availableShares = shares)
            if (shares.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "未找到可用共享。请手动输入共享名。",
                )
            }
        }
    }

    /**
     * 连接 SMB 服务器
     * @param sharePath 用户输入的路径，可以是：
     *   - "我的文档-2281044176" → 共享名，扫描根目录
     *   - "我的文档-2281044176/Music" → 共享名 + 子目录
     *   - "我的文档-2281044176/Music/Rock" → 共享名 + 深层子目录
     */
    fun connect(ip: String, port: Int, username: String, password: String, sharePath: String, domain: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState = SmbConnectionState.Connecting,
                errorMessage = null,
                connectSuccess = false,
                availableShares = emptyList(),
            )

            // 解析路径：第一段是共享名，剩下的是子目录
            val parts = sharePath.split("/", limit = 2)
            val shareName = parts[0].trim()
            val subPath = if (parts.size > 1) parts[1].trim() else ""

            if (shareName.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    connectionState = SmbConnectionState.Disconnected,
                    errorMessage = "请输入共享名称",
                )
                return@launch
            }

            Log.d("SmbConnect", "连接: $ip:$port, share=$shareName, subPath=$subPath")

            val existing = _uiState.value.savedServers.find { it.ip == ip && it.port == port }
            // 已存在时用本次输入的凭据/路径更新并重新加密：否则用户改密码/路径只用于本次连接，
            // 下次启动自动重连（AppViewModel）会用库内旧凭据必然认证失败
            val server = if (existing != null) {
                existing.copy(
                    username = username,
                    password = password,
                    shareName = sharePath,
                ).encryptPassword()
            } else {
                ServerEntity(
                    name = ip, ip = ip, port = port,
                    username = username, password = password,
                    shareName = sharePath, autoConnect = true,
                ).encryptPassword()
            }
            val serverId = if (existing != null) existing.id else serverDao.insert(server)

            try {
                connectionPool.getConnection(
                    serverId = serverId, host = ip, port = port,
                    username = username, password = password,
                    shareName = shareName, domain = domain,
                )
                serverDao.update(server.copy(id = serverId, lastConnectedAt = System.currentTimeMillis()))
                Log.d("SmbConnect", "连接成功!")

                _uiState.value = _uiState.value.copy(
                    connectionState = SmbConnectionState.Connected,
                    isConnected = true,
                    connectedServerName = "$ip/$shareName",
                    isScanningMusic = true,
                    scanMusicMessage = if (subPath.isNotEmpty()) "正在扫描 $subPath ..." else "正在扫描 NAS 音乐文件...",
                    scannedSongCount = 0,
                )

                // 扫描音乐
                scanJob?.cancel()
                scanJob = viewModelScope.launch {
                    try {
                        val client = connectionPool.getExistingConnection(serverId)
                        if (client == null) {
                            _uiState.value = _uiState.value.copy(
                                isScanningMusic = false,
                                scanMusicMessage = "连接已断开",
                                connectSuccess = true,
                            )
                            return@launch
                        }

                        val scanPath = if (subPath.isNotEmpty()) "/$subPath" else "/"
                        Log.d("SmbConnect", "扫描路径: $scanPath")
                        val result = musicRepository.scanSmbDirectory(client, scanPath, serverId)
                        result.fold(
                            onSuccess = { count ->
                                Log.d("SmbConnect", "扫描完成: $count 首")
                                _uiState.value = _uiState.value.copy(
                                    isScanningMusic = false,
                                    scanMusicMessage = "扫描完成，找到 $count 首歌曲，正在缓存元数据...",
                                    scannedSongCount = count,
                                    connectSuccess = true,
                                )
                                // 扫描完成后自动批量缓存元数据（歌手/封面/歌词）
                                val smbSongs = songDao.getAllSongs().first().filter { it.source == "SMB" }
                                if (smbSongs.isNotEmpty()) {
                                    metadataBatchProcessor.processSongs(smbSongs)
                                }
                            },
                            onFailure = { e ->
                                Log.e("SmbConnect", "扫描失败", e)
                                _uiState.value = _uiState.value.copy(
                                    isScanningMusic = false,
                                    scanMusicMessage = "扫描失败: ${e.message}",
                                    connectSuccess = true,
                                )
                            },
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isScanningMusic = false,
                            scanMusicMessage = "扫描异常: ${e.message}",
                            connectSuccess = true,
                        )
                    }
                }
            } catch (e: SmbConnectionException) {
                Log.e("SmbConnect", "连接失败: ${e.message}", e)
                serverDao.updateConnectionError(serverId, e.message)
                _uiState.value = _uiState.value.copy(
                    connectionState = SmbConnectionState.Error(e.message ?: "连接失败"),
                    errorMessage = e.message,
                )
            } catch (e: Exception) {
                Log.e("SmbConnect", "连接异常", e)
                val msg = "连接失败: ${e.message}"
                serverDao.updateConnectionError(serverId, msg)
                _uiState.value = _uiState.value.copy(
                    connectionState = SmbConnectionState.Error(msg),
                    errorMessage = msg,
                )
            }
        }
    }

    fun connectWithShare(sharePath: String) {
        val current = _uiState.value
        val server = current.savedServers.lastOrNull()?.decryptPassword() ?: return
        connect(server.ip, server.port, server.username, server.password, sharePath)
    }

    fun connectToScanned(host: NetworkScanner.ScannedHost) {
        connect(ip = host.ip, port = 445, username = "", password = "", sharePath = "")
    }

    fun connectToDiscovered(server: DiscoveredServer) {
        connect(ip = server.host, port = server.port, username = "", password = "", sharePath = "")
    }

    fun connectToSaved(server: ServerEntity) {
        val decrypted = server.decryptPassword()
        connect(decrypted.ip, decrypted.port, decrypted.username, decrypted.password, decrypted.shareName)
    }

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            connectionPool.disconnect(server.id)
            serverDao.delete(server)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun resetConnectSuccess() {
        _uiState.value = _uiState.value.copy(connectSuccess = false)
    }

    override fun onCleared() {
        super.onCleared()
        discovery.stop()
    }
}
