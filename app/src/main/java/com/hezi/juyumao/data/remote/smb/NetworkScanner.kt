package com.hezi.juyumao.data.remote.smb

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject

/**
 * 局域网 SMB 服务器扫描器
 * 通过扫描本地网段的 445 端口来发现 SMB 服务器
 */
class NetworkScanner @Inject constructor() {

    data class ScannedHost(
        val ip: String,
        val hostname: String = "",
        val hasSmb: Boolean = false,
    )

    private companion object {
        /** 并发连接上限（254 路并发会被路由器判定为扫描而丢包，也易耗尽 socket fd） */
        const val MAX_CONCURRENCY = 32
        /** 网段过小时的实际主机数上限 */
        const val MAX_HOSTS = 1024
    }

    /**
     * 获取本机 IP 和子网（优先 WiFi 接口，跳过 VPN/蜂窝虚拟接口）
     */
    private fun getLocalNetwork(): Pair<String, Int>? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val ifName = networkInterface.name.lowercase()
                // 跳过 VPN（tun/tap/ppp）与蜂窝数据（rmnet/ccmni）接口，避免扫描到虚拟网段
                if (ifName.startsWith("tun") || ifName.startsWith("tap") ||
                    ifName.startsWith("ppp") || ifName.startsWith("rmnet") || ifName.startsWith("ccmni")
                ) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: continue
                        val prefixLength = networkInterface.interfaceAddresses
                            .firstOrNull { it.address == address }
                            ?.networkPrefixLength ?: 24
                        return Pair(ip, prefixLength.toInt())
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /** 按真实前缀长度计算网段起始地址与主机数（不再硬编码 /24） */
    private fun computeNetwork(localIp: String, prefixLength: Int): Pair<Long, Int> {
        val bytes = InetAddress.getByName(localIp).address
        val ipInt = ((bytes[0].toLong() and 0xFF) shl 24) or
            ((bytes[1].toLong() and 0xFF) shl 16) or
            ((bytes[2].toLong() and 0xFF) shl 8) or
            (bytes[3].toLong() and 0xFF)
        val mask = if (prefixLength >= 32) -1L else (-1L shl (32 - prefixLength)) and 0xFFFFFFFFL
        val networkBase = ipInt and mask
        val hostBits = (32 - prefixLength).coerceIn(1, 32)
        val hostCount = if (hostBits >= 24) {
            MAX_HOSTS // /8 及更大网段主机数巨大，限制扫描范围
        } else {
            ((1L shl hostBits) - 2).coerceIn(1, MAX_HOSTS.toLong()).toInt() // 排除网络地址与广播地址
        }
        return networkBase to hostCount
    }

    private fun ipFromInt(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

    /**
     * 扫描局域网内开放 445 端口的设备
     * @param onProgress 进度回调 (已扫描IP, 发现的SMB服务器列表)
     * @param timeoutMs 每个 IP 的连接超时
     */
    suspend fun scanLocalNetwork(
        onProgress: (Int, List<ScannedHost>) -> Unit = { _, _ -> },
        timeoutMs: Int = 200,
    ): List<ScannedHost> = withContext(Dispatchers.IO) {
        val (localIp, prefixLength) = getLocalNetwork() ?: return@withContext emptyList()
        val (networkBase, hostCount) = computeNetwork(localIp, prefixLength)

        val hosts = mutableListOf<ScannedHost>()
        val scanned = java.util.concurrent.atomic.AtomicInteger(0)
        val mutex = Mutex()
        val semaphore = Semaphore(MAX_CONCURRENCY)

        // 并发扫描（限流），按真实网段范围遍历
        val jobs = (1..hostCount).map { i ->
            launch {
                semaphore.withPermit {
                    val targetIp = ipFromInt(networkBase + i)
                    if (targetIp == localIp) {
                        scanned.incrementAndGet()
                        return@withPermit
                    }

                    val hasSmb = try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(targetIp, 445), timeoutMs)
                            true
                        }
                    } catch (_: Exception) {
                        false
                    }

                    if (hasSmb) {
                        val hostname = try {
                            InetAddress.getByName(targetIp).hostName ?: targetIp
                        } catch (_: Exception) {
                            targetIp
                        }
                        val snapshot = mutex.withLock {
                            hosts.add(ScannedHost(targetIp, hostname, true))
                            hosts.toList() // 锁内取快照，避免并发读写 ArrayList
                        }
                        onProgress(scanned.incrementAndGet(), snapshot)
                    } else {
                        scanned.incrementAndGet()
                    }
                    // 每扫描 20 个 IP 更新一次进度
                    if (scanned.get() % 20 == 0) {
                        onProgress(scanned.get(), mutex.withLock { hosts.toList() })
                    }
                }
            }
        }

        jobs.forEach { it.join() }
        onProgress(hostCount, mutex.withLock { hosts.toList() })
        mutex.withLock { hosts.toList() }.sortedBy { it.ip.split(".").last().toIntOrNull() ?: 0 }
    }
}
