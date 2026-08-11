package com.hezi.juyumao.data.remote.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.CopyOnWriteArrayList

data class DiscoveredServer(
    val name: String,
    val host: String,
    val port: Int,
    val domain: String = "",
)

class SmbDiscovery @javax.inject.Inject constructor() {

    // discover 串行化：两次并发调用不再互相覆盖/关闭对方的 JmDNS 实例
    private val discoverMutex = Mutex()

    /** 当前活动的 JmDNS（供 stop() 关闭） */
    @Volatile private var activeJmDns: JmDNS? = null

    private fun getLocalIpAddress(): InetAddress {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val ifName = networkInterface.name.lowercase()
                // 优先 WiFi 接口，跳过 VPN/蜂窝虚拟接口（mDNS 组播在虚拟网段失效）
                if (ifName.startsWith("tun") || ifName.startsWith("tap") ||
                    ifName.startsWith("ppp") || ifName.startsWith("rmnet") || ifName.startsWith("ccmni")
                ) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address
                    }
                }
            }
        } catch (_: Exception) {}
        return InetAddress.getByName("127.0.0.1")
    }

    suspend fun discover(timeoutMs: Long = 5000): Result<List<DiscoveredServer>> =
        discoverMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val servers = CopyOnWriteArrayList<DiscoveredServer>()
                    val jmDns = JmDNS.create(getLocalIpAddress())
                    activeJmDns = jmDns
                    // listener/jmdns 改为局部变量，杜绝跨调用覆盖
                    val listener = object : ServiceListener {
                        override fun serviceAdded(event: ServiceEvent) {
                            jmDns.requestServiceInfo(event.type, event.name, true)
                        }

                        override fun serviceRemoved(event: ServiceEvent) {}

                        override fun serviceResolved(event: ServiceEvent) {
                            // 优先 IPv4 地址（IPv6 无法直接连 SMB），无则跳过
                            val host = event.info.hostAddresses.firstOrNull { a ->
                                runCatching { InetAddress.getByName(a) is Inet4Address }.getOrDefault(false)
                            } ?: return
                            val port = event.info.port
                            // 按 name+host+port 去重，避免重复 serviceAdded/requestServiceInfo 产生重复条目
                            val name = event.info.name
                            if (servers.none { it.name == name && it.host == host && it.port == port }) {
                                servers.add(
                                    DiscoveredServer(
                                        name = name,
                                        host = host,
                                        port = port,
                                        domain = event.info.domain,
                                    )
                                )
                            }
                        }
                    }
                    jmDns.addServiceListener("_smb._tcp.local.", listener)

                    delay(timeoutMs)

                    try { jmDns.removeServiceListener("_smb._tcp.local.", listener) } catch (_: Exception) {}
                    try { jmDns.close() } catch (_: Exception) {}
                    activeJmDns = null

                    Result.success(servers.toList())
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 保持结构化并发：协程取消必须向上传播
                    throw e
                } catch (e: Exception) {
                    try { activeJmDns?.close() } catch (_: Exception) {}
                    activeJmDns = null
                    Result.failure(e)
                }
            }
        }

    fun stop() {
        try { activeJmDns?.close() } catch (_: Exception) {}
        activeJmDns = null
    }
}
