package app.fen.mcp.net

import java.net.NetworkInterface

data class LanCandidate(
    val interfaceName: String,
    val hostAddress: String,
    val isWifi: Boolean,
)

fun selectLanAddresses(candidates: List<LanCandidate>): List<String> =
    candidates
        .filter { isPrivateIpv4(it.hostAddress) }
        .sortedWith(compareByDescending<LanCandidate> { it.isWifi }.thenBy { it.interfaceName })
        .map { it.hostAddress }
        .distinct()

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
