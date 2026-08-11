package com.hezi.juyumao.data.remote.smb

import android.util.Log
import com.hezi.juyumao.data.local.db.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * SMB 文件扫描进度
 */
data class ScanProgress(
    val scannedFiles: Int = 0,
    val foundSongs: Int = 0,
    val currentPath: String = "",
    val isComplete: Boolean = false,
    val error: String? = null,
)

class SmbFileScanner @Inject constructor() {

    private companion object {
        /** 递归深度上限：防止符号链接/reparse point 成环导致栈溢出或超时 */
        const val MAX_DEPTH = 64
    }

    suspend fun scanDirectory(
        smbClient: SmbClientWrapper,
        path: String,
        serverId: Long,
        onProgress: (ScanProgress) -> Unit = {},
    ): Result<List<SongEntity>> = withContext(Dispatchers.IO) {
        try {
            val songs = mutableListOf<SongEntity>()
            val progressState = ScanProgressState()
            // visited 集合做循环检测（符号链接环）
            val visited = java.util.HashSet<String>()
            scanRecursive(smbClient, path, serverId, songs, onProgress, progressState, visited, isRoot = true)
            onProgress(ScanProgress(
                scannedFiles = progressState.totalScanned,
                foundSongs = songs.size,
                currentPath = path,
                isComplete = true,
            ))
            Result.success(songs)
        } catch (e: Exception) {
            onProgress(ScanProgress(error = e.message, isComplete = true))
            Result.failure(e)
        }
    }

    private class ScanProgressState {
        var lastProgressTime = 0L
        var lastProgressCount = -1
        var totalScanned = 0
    }

    private suspend fun scanRecursive(
        smbClient: SmbClientWrapper,
        path: String,
        serverId: Long,
        result: MutableList<SongEntity>,
        onProgress: (ScanProgress) -> Unit,
        progressState: ScanProgressState,
        visited: java.util.HashSet<String>,
        isRoot: Boolean = false,
        depth: Int = 0,
    ) {
        if (depth > MAX_DEPTH) return
        // 循环检测：同一路径已访问则跳过（SMB 符号链接/reparse point 可能成环）
        if (!visited.add(path)) return

        val filesResult = smbClient.listFiles(path)
        if (filesResult.isFailure) {
            // 根目录失败必须上报（UI 显示"扫描完成找到 0 首"是误导）；子目录失败仅记录继续
            if (isRoot) {
                throw filesResult.exceptionOrNull() ?: Exception("扫描失败: $path")
            }
            Log.e("SmbFileScanner", "列出文件失败: $path", filesResult.exceptionOrNull())
            return
        }

        val files = filesResult.getOrNull() ?: return

        for (file in files) {
            progressState.totalScanned++
            // 跳过隐藏文件/目录（NAS 常见 @eaDir/#recycle/.Trash/.thumbnail 等）
            if (file.name.startsWith(".") || file.name.startsWith("@") || file.name.startsWith("#")) continue
            if (file.isDirectory) {
                // 排除系统目录
                if (!AudioFileFilter.isExcludedPath(file.path)) {
                    scanRecursive(smbClient, file.path, serverId, result, onProgress, progressState, visited, depth = depth + 1)
                }
            } else if (AudioFileFilter.isAudioFile(file.name)
                && !AudioFileFilter.isExcludedPath(file.path)
                && !AudioFileFilter.isExcludedFileName(file.name)
                && file.size >= 100_000 // 最小 100KB
            ) {
                // 从目录结构推断元数据
                val (artist, album, title) = AudioFileFilter.inferMetadata(file.path)

                result.add(
                    SongEntity(
                        title = title,
                        artist = artist,
                        album = album,
                        filePath = file.path,
                        fileSize = file.size,
                        mimeType = AudioFileFilter.getMimeType(file.name),
                        isHiRes = AudioFileFilter.isHiRes(file.name),
                        source = "SMB",
                        smbServerId = serverId,
                        smbSharePath = file.path,
                    )
                )
            }
            // 节流：每 50 个文件或 500ms 调用一次
            val now = System.currentTimeMillis()
            if (progressState.totalScanned - progressState.lastProgressCount >= 50 || now - progressState.lastProgressTime >= 500) {
                progressState.lastProgressCount = progressState.totalScanned
                progressState.lastProgressTime = now
                onProgress(ScanProgress(
                    scannedFiles = progressState.totalScanned,
                    foundSongs = result.size,
                    currentPath = file.path,
                ))
            }
        }
    }
}
