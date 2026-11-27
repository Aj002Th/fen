package app.fen.mcp.net

import java.net.NetworkInterface

data class LanCandidate(
    val interfaceName: String,
    val hostAddress: String,
    val isWifi: Boolean,
)

/**
 * 只保留真正能被电脑访问的局域网地址：
 * - 排除 127/8、tun（VPN）、rmnet/ccmni（蜂窝）、p2p 等接口
 * - 有 wlan 时只返回 wlan；否则退回 eth/usb/rndis
 * - 同类型内按 192.168 > 10 > 172.16-31 排序
 */
fun selectLanAddresses(candidates: List<LanCandidate>): List<String> {
    val lan = candidates.filter { isLanInterface(it.interfaceName) && isPrivateIpv4(it.hostAddress) }
    val wifi = lan.filter { it.isWifi }
    val pool = if (wifi.isNotEmpty()) wifi else lan
    return pool
        .sortedWith(compareBy<LanCandidate> { addressRank(it.hostAddress) }.thenBy { it.interfaceName })
        .map { it.hostAddress }
        .distinct()
}

fun currentLanCandidates(): List<LanCandidate> {
    val result = mutableListOf<LanCandidate>()
    runCatching {
        NetworkInterface.getNetworkInterfaces().toList().forEach { network ->
            val name = network.name.orEmpty()
            val wifi = name.startsWith("wlan") || name.startsWith("wifi")
            network.inetAddresses.toList().forEach { address ->
                val host = address.hostAddress ?: return@forEach
                if (host.contains(':')) return@forEach
                result += LanCandidate(name, host, wifi)
            }
        }
    }
    return result
}

fun isPrivateIpv4(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    val (a, b) = octets
    return when {
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        else -> false
    }
}

private fun isLanInterface(name: String): Boolean =
    listOf("wlan", "wifi", "eth", "usb", "rndis", "swlan", "ap").any { name.startsWith(it) }

private fun addressRank(host: String): Int = when {
    host.startsWith("192.168.") -> 0
    host.startsWith("10.") -> 1
    else -> 2
}
