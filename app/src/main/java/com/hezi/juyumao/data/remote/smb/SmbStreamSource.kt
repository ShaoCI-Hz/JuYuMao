package com.hezi.juyumao.data.remote.smb

import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import java.io.InputStream

class SmbStreamSource(
    private val smbClient: SmbClientWrapper,
    private val filePath: String,
) : BaseDataSource(/* isNetwork= */ true) {

    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        close() // 先释放上一次未关闭的流（异常路径/重试场景），避免 SMB 文件句柄泄漏
        // ExoPlayer 调用线程已非主线程，runBlocking 不会 ANR
        val result = runBlocking {
            // 按 dataSpec.position 从指定偏移打开：ExoPlayer seek/恢复播放会带非零 position 重新 open
            smbClient.openFile(filePath, dataSpec.position)
        }
        inputStream = result.getOrNull()
            ?: throw Exception("无法打开文件: $filePath")

        // 未知长度原样返回 C.LENGTH_UNSET(-1)，符合 DataSource 契约（影响 seekability 与读满判断）
        bytesRemaining = dataSpec.length
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return -1

        val bytesToRead = if (bytesRemaining > 0) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length // 未知长度（-1）时不截断
        }

        // InputStream 契约：length > 0 时不得返回 0，否则 ExoPlayer 会忙循环空转
        var bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: -1
        while (bytesRead == 0) {
            bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: -1
        }

        if (bytesRead == -1) {
            bytesRemaining = 0
            return -1
        }

        if (bytesRemaining > 0) bytesRemaining -= bytesRead
        return bytesRead
    }

    override fun close() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        inputStream = null
    }

    override fun getUri(): Uri? = Uri.parse("smb://$filePath")
}
