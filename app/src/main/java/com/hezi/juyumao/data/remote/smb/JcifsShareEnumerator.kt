package com.hezi.juyumao.data.remote.smb

import android.util.Log
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.inject.Inject

/**
 * 使用 jcifs-ng 枚举 SMB 共享名（SMBJ 不支持，jcifs-ng 通过 smb://host/ 列目录实现）
 */
class JcifsShareEnumerator @Inject constructor() {

    data class JcifsShare(
        val name: String,
        val comment: String = "",
    )

    suspend fun enumerateShares(
        host: String,
        port: Int = 445,
        username: String = "",
        password: String = "",
        domain: String = "",
    ): List<JcifsShare> = withContext(Dispatchers.IO) {
        // 类型推断为非空：初始化失败直接返回（避免 CIFSContext? 可空传播）
        val context = try {
            // jcifs-ng 配置：启用 SMB2/3，设置超时
            val props = Properties().apply {
                setProperty("jcifs.smb.client.enableSMB2", "true")
                setProperty("jcifs.smb.client.enableSMB1", "true")
                setProperty("jcifs.smb.client.responseTimeout", "10000")
                setProperty("jcifs.smb.client.soTimeout", "10000")
                setProperty("jcifs.smb.client.connTimeout", "10000")
            }
            BaseContext(PropertyConfiguration(props))
        } catch (e: Exception) {
            Log.e("JcifsEnum", "jcifs-ng 配置初始化失败", e)
            return@withContext emptyList()
        }
        try {
            val authContext = if (username.isNotEmpty()) {
                context.withCredentials(NtlmPasswordAuthenticator(domain, username, password))
            } else {
                // 匿名：jcifs-ng 传空凭据走匿名空会话（非 guest 用户）
                context
            }

            // 端口非默认 445 时 URL 需带上端口，否则枚举失败或连错端口
            val rootUrl = if (port != 445) "smb://$host:$port/" else "smb://$host/"
            Log.d("JcifsEnum", "列出共享: $rootUrl")
            val dir = SmbFile(rootUrl, authContext)
            // listFiles 在枚举被拒等场景可能返回 null；返回类型为 Java 数组
            val files = dir.listFiles() ?: emptyArray()

            val shares = files.mapNotNull { f ->
                val name = f.name.trimEnd('/')
                if (name.isEmpty()) return@mapNotNull null
                JcifsShare(name = name)
            }
            Log.d("JcifsEnum", "发现 ${shares.size} 个共享")
            shares
        } catch (e: Exception) {
            // 只记消息不记完整堆栈，避免认证失败时的用户名/域进入日志
            Log.e("JcifsEnum", "jcifs-ng 枚举失败: ${e.message}")
            emptyList()
        } finally {
            // 释放 context 持有的底层 transport 连接与线程池
            try { context.close() } catch (_: Exception) {}
        }
    }
}
