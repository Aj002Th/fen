package app.fen.mcp.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressesTest {

    @Test
    fun keepsPrivateIpv4Only() {
        assertTrue(isPrivateIpv4("192.168.1.2"))
        assertTrue(isPrivateIpv4("10.0.0.5"))
        assertTrue(isPrivateIpv4("172.16.3.4"))
        assertTrue(isPrivateIpv4("172.31.255.254"))
        assertFalse(isPrivateIpv4("172.32.0.1"))
        assertFalse(isPrivateIpv4("127.0.0.1"))
        assertFalse(isPrivateIpv4("8.8.8.8"))
        assertFalse(isPrivateIpv4("::1"))
        assertFalse(isPrivateIpv4("999.1.1.1"))
    }

    @Test
    fun prefersWifiAndDeduplicates() {
        val result = selectLanAddresses(
            listOf(
                LanCandidate("rmnet0", "10.1.2.3", false),
                LanCandidate("wlan0", "192.168.1.2", true),
                LanCandidate("wlan0", "192.168.1.2", true),
                LanCandidate("lo", "127.0.0.1", false),
            )
        )

        assertEquals(listOf("192.168.1.2", "10.1.2.3"), result)
    }

    @Test
    fun emptyWhenNoPrivateAddress() {
        assertEquals(emptyList<String>(), selectLanAddresses(listOf(LanCandidate("lo", "127.0.0.1", false))))
    }
}
