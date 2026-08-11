package com.hezi.juyumao.data.repository

import com.hezi.juyumao.data.local.crypto.decryptPassword
import com.hezi.juyumao.data.local.crypto.encryptPassword
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.entity.ServerEntity
import com.hezi.juyumao.data.remote.discovery.DiscoveredServer
import com.hezi.juyumao.data.remote.discovery.SmbDiscovery
import com.hezi.juyumao.data.remote.smb.SmbConnectionPool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val connectionPool: SmbConnectionPool,
    private val discovery: SmbDiscovery,
) {
    fun connectionStateFor(serverId: Long) = connectionPool.connectionStateFor(serverId)

    fun isAnyConnected(): Boolean = connectionPool.isAnyConnected()

    fun getAllServers(): Flow<List<ServerEntity>> = serverDao.getAllServers().map { list ->
        list.map { it.decryptPassword() }
    }

    fun getAutoConnectServers(): Flow<List<ServerEntity>> = serverDao.getAutoConnectServers().map { list ->
        list.map { it.decryptPassword() }
    }

    suspend fun connect(server: ServerEntity): Result<Unit> {
        return try {
            val decrypted = server.decryptPassword()
            connectionPool.getConnection(
                serverId = decrypted.id,
                host = decrypted.ip,
                port = decrypted.port,
                username = decrypted.username,
                password = decrypted.password,
                shareName = decrypted.effectiveShareName,
            )
            // 强制加密后再写库：getAllServers 返回的是解密后的明文对象，直接 update 会把明文密码回写数据库，
            // 绕过 AES-GCM 加密（安全）
            serverDao.update(server.copy(lastConnectedAt = System.currentTimeMillis()).encryptPassword())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveServer(server: ServerEntity): Long {
        return serverDao.insert(server.encryptPassword())
    }

    suspend fun deleteServer(server: ServerEntity) {
        connectionPool.disconnect(server.id)
        serverDao.delete(server)
    }

    fun disconnect(serverId: Long) {
        connectionPool.disconnect(serverId)
    }

    fun disconnectAll() {
        connectionPool.disconnectAll()
    }

    suspend fun discoverServers(): Result<List<DiscoveredServer>> {
        return discovery.discover()
    }
}
