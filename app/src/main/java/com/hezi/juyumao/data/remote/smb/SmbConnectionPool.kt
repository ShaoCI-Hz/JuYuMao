package com.hezi.juyumao.data.remote.smb

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class PooledConnection(
    val client: SmbClientWrapper,
    val serverId: Long,
    val lastAccessed: Long = System.currentTimeMillis(),
)

class SmbConnectionPool @Inject constructor() {
    private val maxConnections = 8
    private val idleTimeoutMs = 30 * 60 * 1000L  // 30 分钟空闲才断开
    private val connections = ConcurrentHashMap<Long, PooledConnection>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // 仅保护 map 的快速查找/替换/驱逐（网络 connect 在锁外执行，避免一台慢服务器阻塞全部建连）
    private val connectionMutex = Mutex()
    // per-server 建连锁：同一服务器并发请求只建一次连接
    private val perServerLocks = ConcurrentHashMap<Long, Mutex>()

    private val _connectionStates = ConcurrentHashMap<Long, MutableStateFlow<SmbConnectionState>>()

    fun connectionStateFor(serverId: Long): StateFlow<SmbConnectionState> =
        getMutableStateFor(serverId)

    private fun getMutableStateFor(serverId: Long): MutableStateFlow<SmbConnectionState> =
        _connectionStates.getOrPut(serverId) { MutableStateFlow(SmbConnectionState.Disconnected) }

    init {
        scope.launch {
            while (isActive) {
                cleanupIdleConnections()
                delay(30_000)
            }
        }
    }

    suspend fun getConnection(
        serverId: Long,
        host: String,
        port: Int,
        username: String,
        password: String,
        shareName: String,
        domain: String = "",
    ): SmbClientWrapper {
        val stateFlow = getMutableStateFor(serverId)

        // 快速路径（锁内只做查找 + 刷新访问时间，不做网络 IO）
        connectionMutex.withLock {
            connections[serverId]?.takeIf { it.client.isConnected() }?.let { pooled ->
                connections[serverId] = pooled.copy(lastAccessed = System.currentTimeMillis())
                return pooled.client
            }
        }

        // 慢路径：per-server 锁内建连（网络 IO 在锁外执行，但仍按服务器互斥）
        return lockFor(serverId).withLock {
            // 双检：等锁期间可能已被其他协程建好
            connectionMutex.withLock {
                connections[serverId]?.takeIf { it.client.isConnected() }?.let { pooled ->
                    connections[serverId] = pooled.copy(lastAccessed = System.currentTimeMillis())
                    return pooled.client
                }
            }

            stateFlow.value = SmbConnectionState.Connecting
            Log.d("SmbPool", "创建新连接: $host:$port, share=$shareName")

            val client = SmbClientWrapper()
            try {
                client.connect(host, port, username, password, shareName, domain)
                connectionMutex.withLock {
                    // 全局配额：满则驱逐最旧（锁内执行，避免与 cleanup 竞态误驱健康连接）
                    if (connections.size >= maxConnections) {
                        evictOldestLocked()
                    }
                    connections[serverId] = PooledConnection(client, serverId)
                }
                stateFlow.value = SmbConnectionState.Connected
                Log.d("SmbPool", "连接成功")
                client
            } catch (e: Exception) {
                Log.e("SmbPool", "连接失败", e)
                stateFlow.value = SmbConnectionState.Error(e.message ?: "连接失败")
                throw e
            }
        }
    }

    private fun lockFor(serverId: Long): Mutex = perServerLocks.getOrPut(serverId) { Mutex() }

    suspend fun getExistingConnection(serverId: Long): SmbClientWrapper? = connectionMutex.withLock {
        connections[serverId]?.takeIf { it.client.isConnected() }?.also {
            connections[serverId] = it.copy(lastAccessed = System.currentTimeMillis())
        }?.client
    }

    /** 是否有任一活跃连接 */
    fun isAnyConnected(): Boolean = connections.values.any { it.client.isConnected() }

    fun disconnect(serverId: Long) {
        // runBlocking：保护与 getConnection/cleanup 的竞态（网络 close 通常很快）
        runBlocking {
            connectionMutex.withLock {
                connections.remove(serverId)?.client?.disconnect()
                _connectionStates[serverId]?.value = SmbConnectionState.Disconnected
            }
        }
    }

    fun disconnectAll() {
        runBlocking {
            connectionMutex.withLock {
                connections.values.forEach { it.client.disconnect() }
                connections.clear()
                _connectionStates.values.forEach { it.value = SmbConnectionState.Disconnected }
            }
        }
    }

    fun close() {
        scope.cancel()
        disconnectAll()
    }

    private suspend fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        // 与 getConnection 共用同一把锁：杜绝"cleanup 读到旧 lastAccessed 后 disconnect 掉刚借出的活跃连接"
        connectionMutex.withLock {
            val idle = connections.entries.filter { now - it.value.lastAccessed > idleTimeoutMs }
            idle.forEach { (serverId, pooled) ->
                pooled.client.disconnect()
                connections.remove(serverId)
                _connectionStates[serverId]?.value = SmbConnectionState.Disconnected
            }
        }
    }

    /** 仅可在 connectionMutex 持有者内调用 */
    private fun evictOldestLocked() {
        val oldest = connections.entries.minByOrNull { it.value.lastAccessed } ?: return
        oldest.value.client.disconnect()
        connections.remove(oldest.key)
        _connectionStates[oldest.key]?.value = SmbConnectionState.Disconnected
    }
}

sealed class SmbConnectionState {
    data object Disconnected : SmbConnectionState()
    data object Connecting : SmbConnectionState()
    data object Connected : SmbConnectionState()
    data class Error(val message: String) : SmbConnectionState()
}
