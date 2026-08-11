package com.hezi.juyumao.data.remote.smb

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SmbFileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)

class SmbClientWrapper @Inject constructor() {

    @Volatile private var client: SMBClient? = null
    @Volatile private var connection: Connection? = null
    @Volatile private var session: Session? = null
    @Volatile private var share: DiskShare? = null

    private val connectMutex = Mutex()

    suspend fun connect(
        host: String,
        port: Int = 445,
        username: String = "",
        password: String = "",
        shareName: String,
        domain: String = "",
    ): Result<Unit> = connectMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                disconnect()

                Log.d("SmbClient", "开始连接: $host:$port, share=$shareName")

                val config = SmbConfig.builder()
                    .withTimeout(15, TimeUnit.SECONDS)
                    .withSoTimeout(15, TimeUnit.SECONDS)
                    .build()

                client = SMBClient(config)

                Log.d("SmbClient", "正在建立 TCP 连接...")
                connection = try {
                    (client ?: error("SMBClient 创建失败")).connect(host, port)
                } catch (e: Exception) {
                    Log.e("SmbClient", "TCP 连接失败", e)
                    throw Exception("无法连接到 $host:$port — ${e.message ?: "请检查 IP 和端口"}", e)
                }
                Log.d("SmbClient", "TCP 连接成功，开始认证...")

                val ac = if (username.isEmpty()) {
                    AuthenticationContext.anonymous()
                } else {
                    AuthenticationContext(username, password.toCharArray(), domain)
                }

                session = try {
                    (connection ?: error("SMB 连接未建立")).authenticate(ac)
                } catch (e: Exception) {
                    Log.e("SmbClient", "认证失败", e)
                    val msg = e.message ?: ""
                    when {
                        msg.contains("STATUS_LOGON_FAILURE", true) -> throw Exception("用户名或密码错误")
                        msg.contains("STATUS_ACCESS_DENIED", true) -> throw Exception("访问被拒绝，请检查用户名密码和权限")
                        msg.contains("auth", true) -> throw Exception("认证失败: $msg")
                        else -> throw Exception("认证失败: $msg", e)
                    }
                }
                Log.d("SmbClient", "认证成功，连接共享: $shareName")

                if (shareName.isBlank()) {
                    disconnect()
                    return@withContext Result.failure(
                        SmbConnectionException("请输入共享名称（如 music、Media）")
                    )
                }

                share = try {
                    session!!.connectShare(shareName) as DiskShare
                } catch (e: Exception) {
                    Log.e("SmbClient", "连接共享失败", e)
                    val msg = e.message ?: ""
                    when {
                        msg.contains("STATUS_BAD_NETWORK_NAME", true) -> throw Exception("共享名 '$shareName' 不存在，请检查共享名是否正确")
                        msg.contains("STATUS_ACCESS_DENIED", true) -> throw Exception("没有访问共享 '$shareName' 的权限")
                        msg.contains("STATUS_OBJECT_NAME_NOT_FOUND", true) -> throw Exception("共享名 '$shareName' 不存在")
                        msg.contains("STATUS_OBJECT_PATH_NOT_FOUND", true) -> throw Exception("共享路径不存在: $shareName")
                        else -> throw Exception("连接共享 '$shareName' 失败: $msg", e)
                    }
                }
                Log.d("SmbClient", "连接成功!")
                Result.success(Unit)
            } catch (e: SmbConnectionException) {
                disconnect()
                throw e
            } catch (e: Exception) {
                disconnect()
                Log.e("SmbClient", "连接失败", e)
                throw SmbConnectionException(e.message ?: "连接失败", e)
            }
        }
    }

    suspend fun listFiles(path: String): Result<List<SmbFileInfo>> = ioMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val currentShare = share ?: return@withContext Result.failure(IllegalStateException("未连接"))
                val files = currentShare.list(path).mapNotNull { info ->
                    if (info.fileName == "." || info.fileName == "..") return@mapNotNull null
                    // 拒绝路径穿越：fileName 含 .. 或分隔符（恶意/异常服务器）
                    if (info.fileName.contains("..") || info.fileName.contains("/") || info.fileName.contains("\\")) {
                        return@mapNotNull null
                    }
                    SmbFileInfo(
                        name = info.fileName,
                        path = if (path.endsWith("/")) "$path${info.fileName}" else "$path/${info.fileName}",
                        isDirectory = info.fileAttributes and 0x10 != 0L,
                        size = info.endOfFile,
                        lastModified = info.lastWriteTime.toEpochMillis(),
                    )
                }
                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // smbj 的 DiskShare 非线程安全，openFile 需串行化
    private val ioMutex = Mutex()

    /**
     * 打开 SMB 文件。ExoPlayer seek/恢复播放时需从指定偏移读取，否则拖动进度条会从文件头重读。
     * 返回的流在 read/skip/close 时持有 ioMutex（smbj DiskShare 非线程安全，避免读取期间
     * 并发 openFile/listFiles 请求交错损坏状态）。
     * @param offset 起始读取偏移（字节）
     */
    suspend fun openFile(path: String, offset: Long = 0): Result<InputStream> = ioMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (path.contains("..")) {
                    return@withContext Result.failure(IllegalArgumentException("非法路径: $path"))
                }
                val currentShare = share ?: return@withContext Result.failure(IllegalStateException("未连接"))
                val accessMask = EnumSet.of(AccessMask.FILE_READ_DATA)
                val shareAccess = EnumSet.of(com.hierynomus.mssmb2.SMB2ShareAccess.FILE_SHARE_READ)
                val file = currentShare.openFile(
                    path, accessMask, null, shareAccess,
                    com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OPEN, null,
                )
                val rawStream = file.inputStream
                if (offset > 0) {
                    var remaining = offset
                    while (remaining > 0) {
                        val skipped = rawStream.skip(remaining)
                        if (skipped <= 0) break
                        remaining -= skipped
                    }
                }
                // 返回持锁委托流：整个读取生命周期串行化所有 smbj 操作
                Result.success(LockedInputStream(rawStream, ioMutex))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // share/connection 是 @Volatile，直接读即可，避免与 connect 的 Mutex 交叉死锁
    fun isConnected(): Boolean = share != null && connection?.isConnected == true

    /**
     * 断开连接。先摘除引用再释放资源（避免与进行中的 connect 竞态：connect 后续写入的是新引用，
     * 不会拿到半关闭对象）。
     */
    fun disconnect() {
        val s = share; val se = session; val c = connection; val cl = client
        share = null
        session = null
        connection = null
        client = null
        try { s?.close() } catch (_: Exception) {}
        try { se?.close() } catch (_: Exception) {}
        try { c?.close() } catch (_: Exception) {}
        try { cl?.close() } catch (_: Exception) {}
    }
}

/**
 * 持锁输入流：read/skip/close 全部在 ioMutex 内执行，保证 smbj DiskShare 的并发安全。
 * ExoPlayer loader 线程非主线程，runBlocking 不会 ANR。
 */
private class LockedInputStream(
    private val delegate: InputStream,
    private val mutex: Mutex,
) : InputStream() {
    override fun read(): Int = runBlocking { mutex.withLock { delegate.read() } }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        runBlocking { mutex.withLock { delegate.read(b, off, len) } }

    override fun skip(n: Long): Long = runBlocking { mutex.withLock { delegate.skip(n) } }

    override fun available(): Int = runBlocking { mutex.withLock { delegate.available() } }

    override fun close() {
        runBlocking { mutex.withLock { delegate.close() } }
    }
}

class SmbConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)
