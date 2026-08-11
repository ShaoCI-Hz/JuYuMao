package com.hezi.juyumao.data.local.metadata

import android.util.Log
import com.hezi.juyumao.data.local.crypto.decryptPassword
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.remote.smb.SmbClientWrapper
import com.hezi.juyumao.data.repository.MetadataRepository
import com.hezi.juyumao.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** 批量缓存进度状态 */
data class BatchCacheState(
    val isRunning: Boolean = false,
    val total: Int = 0,
    val processed: Int = 0,
    val currentSongTitle: String = "",
    val currentSongId: Long = 0L,
    val threadCount: Int = 4,
) {
    val progress: Float
        get() = if (total == 0) 0f else processed.toFloat() / total.toFloat()
}

/**
 * 批量缓存 NAS 歌曲的内嵌元数据（歌手/专辑/封面/歌词）
 * 每个 worker 使用独立 SMB 连接，真正多线程并行（smbj 单连接是串行的）
 */
@Singleton
class MetadataBatchProcessor @Inject constructor(
    private val songDao: SongDao,
    private val serverDao: ServerDao,
    private val metadataRepository: MetadataRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = MutableStateFlow(BatchCacheState())
    val state: StateFlow<BatchCacheState> = _state.asStateFlow()

    /** 运行中标志：AtomicBoolean CAS 原子占用，避免并发调用双双通过检查启动两个任务 */
    private val runningFlag = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 是否已在运行（防止重复启动） */
    fun isRunning(): Boolean = runningFlag.get()

    /**
     * 批量处理指定歌曲列表（多线程并行，每个 worker 独立 SMB 连接）
     */
    fun processSongs(songs: List<SongEntity>) {
        // 原子占用运行权：检查与设置之间不留窗口
        if (!runningFlag.compareAndSet(false, true)) {
            Log.d("BatchCache", "批量缓存已在运行，跳过")
            return
        }
        if (songs.isEmpty()) {
            runningFlag.set(false)
            Log.d("BatchCache", "无歌曲需要处理")
            return
        }

        scope.launch {
            val total = songs.size
            // DataStore 读取可能挂起，用超时保护，失败时用默认 4
            val threadCount = try {
                withTimeoutOrNull(5000) { settingsRepository.cacheThreads.first() }?.coerceIn(1, 8) ?: 4
            } catch (e: Exception) {
                Log.w("BatchCache", "读取线程数失败，使用默认 4", e)
                4
            }
            _state.value = BatchCacheState(isRunning = true, total = total, threadCount = threadCount)
            Log.d("BatchCache", "开始批量缓存 $total 首，线程数 $threadCount")

            val songsByServer = songs.groupBy { it.smbServerId }
            val mutex = Mutex()
            var processed = 0

            try {
                coroutineScope {
                    for ((serverId, serverSongs) in songsByServer) {
                        if (serverId == null) {
                            for (song in serverSongs) {
                                processOne(song, null, mutex, total) { processed = it }
                            }
                            continue
                        }
                        val server = serverDao.getServerById(serverId)?.decryptPassword()
                        if (server == null) {
                            for (song in serverSongs) {
                                processOne(song, null, mutex, total) { processed = it }
                            }
                            continue
                        }

                        // 建立 worker 连接；单个失败不影响整体（失败的 worker 丢弃，至少保留 1 个）
                        val workerCount = minOf(threadCount, serverSongs.size).coerceAtLeast(1)
                        val workers = mutableListOf<SmbClientWrapper>()
                        for (i in 0 until workerCount) {
                            try {
                                val w = SmbClientWrapper()
                                w.connect(
                                    host = server.ip,
                                    port = server.port,
                                    username = server.username,
                                    password = server.password,
                                    shareName = server.effectiveShareName,
                                ).getOrThrow()
                                workers.add(w)
                            } catch (e: Exception) {
                                Log.e("BatchCache", "worker $i 连接失败，降级", e)
                            }
                        }

                        if (workers.isEmpty()) {
                            // 全部连接失败：跳过该服务器的 SMB 歌曲（不再降级逐首重连，
                            // 否则服务器宕机时 N 首歌 = N 次连接尝试并阻塞全局连接池）
                            Log.w("BatchCache", "服务器 ${server.ip} 连接失败，跳过元数据缓存")
                            for (song in serverSongs) {
                                processOne(song, null, mutex, total) { processed = it }
                            }
                            continue
                        }

                        try {
                            // 每个 worker 一个协程，循环从队列取歌处理
                            val taskQueue = java.util.concurrent.ConcurrentLinkedQueue(serverSongs)
                            workers.map { worker ->
                                async {
                                    while (true) {
                                        if (!coroutineContext.isActive) break
                                        val song = taskQueue.poll() ?: break
                                        mutex.withLock {
                                            _state.value = BatchCacheState(
                                                isRunning = true, total = total,
                                                processed = processed,
                                                currentSongTitle = song.title,
                                                currentSongId = song.id,
                                                threadCount = threadCount,
                                            )
                                        }
                                        try {
                                            val enriched = metadataRepository.extractAndUpdateSong(song, worker)
                                            if (enriched != song) songDao.update(enriched)
                                        } catch (e: Exception) {
                                            Log.e("BatchCache", "处理失败: ${song.title}", e)
                                        }
                                        mutex.withLock {
                                            processed++
                                            _state.value = BatchCacheState(
                                                isRunning = true, total = total,
                                                processed = processed, threadCount = threadCount,
                                            )
                                        }
                                    }
                                }
                            }.awaitAll()
                        } finally {
                            workers.forEach { runCatching { it.disconnect() } }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BatchCache", "批量缓存异常", e)
            }

            // 收尾用实际处理数（中途异常/取消时不虚报 100% 完成）
            val actual = _state.value.processed
            _state.value = BatchCacheState(isRunning = false, total = total, processed = actual, threadCount = threadCount)
            Log.d("BatchCache", "批量缓存完成: $actual/$total 首")
            runningFlag.set(false)
        }
    }

    private suspend fun processOne(
        song: SongEntity,
        client: SmbClientWrapper?,
        mutex: Mutex,
        total: Int,
        onProcessed: (Int) -> Unit,
    ) {
        // 无外部连接时跳过 SMB 歌曲：extractAndUpdateSong(song, null) 会对每首重新发起完整
        // SMB 连接（服务器宕机时最坏 15s+/首），违背"全失败跳过下载"的既定策略
        if (client == null && song.source == "SMB") {
            Log.w("BatchCache", "无可用连接，跳过 SMB 歌曲: ${song.title}")
            mutex.withLock {
                val p = _state.value.processed + 1
                onProcessed(p)
                _state.value = BatchCacheState(
                    isRunning = true, total = total,
                    processed = p, threadCount = _state.value.threadCount,
                )
            }
            return
        }
        mutex.withLock {
            _state.value = BatchCacheState(
                isRunning = true, total = total,
                processed = _state.value.processed,
                currentSongTitle = song.title,
                currentSongId = song.id,
                threadCount = _state.value.threadCount,
            )
        }
        try {
            val enriched = metadataRepository.extractAndUpdateSong(song, client)
            if (enriched != song) songDao.update(enriched)
        } catch (e: Exception) {
            Log.e("BatchCache", "处理失败: ${song.title}", e)
        }
        mutex.withLock {
            val p = _state.value.processed + 1
            onProcessed(p)
            _state.value = BatchCacheState(
                isRunning = true, total = total,
                processed = p, threadCount = _state.value.threadCount,
            )
        }
    }
}
