package app.fen.mcp

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpServerTest {

    private val port = ServerSocket(0).use { it.localPort }
    private val token = "test-token"
    private lateinit var server: McpServer

    private val handler = RpcHandler { call, _ ->
        RpcOutcome.Json(
            """{"jsonrpc":"2.0","id":${call.id ?: "null"},"result":{"resultType":"complete","ok":true}}"""
        )
    }

    @Before
    fun setUp() {
        server = McpServer(
            port = port,
            token = token,
            handler = handler,
            allowedHosts = { setOf("127.0.0.1:$port", "localhost:$port") },
            host = "127.0.0.1",
        )
        server.start()
        waitForPort()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun postReturnsJson() {
        val response = post(modernBody())

        assertEquals(200, response.first)
        assertTrue(response.second!!.contains("\"ok\":true"))
        assertTrue(response.third!!.startsWith("application/json"))
    }

    @Test
    fun wrongTokenIsRejected() {
        val response = post(modernBody(), authorization = "Bearer wrong")

        assertEquals(401, response.first)
    }

    @Test
    fun originIsRejected() {
        val body = modernBody()
        val status = rawStatus(
            """
            POST /mcp HTTP/1.1
            Host: 127.0.0.1:$port
            Origin: http://evil.example
            Authorization: Bearer $token
            Content-Type: application/json
            Accept: application/json
            MCP-Protocol-Version: 2026-07-28
            Mcp-Method: tools/list
            Content-Length: ${body.toByteArray(Charsets.UTF_8).size}
            Connection: close

            $body
            """.trimIndent().replace("\n", "\r\n"),
        )

        assertEquals(403, status)
    }

    @Test
    fun rateLimitUsesConfiguredPerMinute() {
        val altPort = ServerSocket(0).use { it.localPort }
        val alt = McpServer(
            port = altPort,
            token = token,
            handler = handler,
            allowedHosts = { setOf("127.0.0.1:$altPort") },
            host = "127.0.0.1",
            rateLimitPerMinute = 1,
        )
        alt.start()
        try {
            waitForPort(altPort)

            assertEquals(200, post(modernBody(), port = altPort).first)
            assertEquals(429, post(modernBody(), port = altPort).first)
        } finally {
            alt.stop()
        }
    }

    private fun rawStatus(request: String): Int {
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            val statusLine = socket.getInputStream().bufferedReader().readLine()
            return statusLine.split(" ")[1].toInt()
        }
    }

    @Test
    fun getIsMethodNotAllowed() {
        val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        try {
            assertEquals(405, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun missingModernHeaderIsRejected() {
        val response = post(modernBody(), mcpMethod = null)

        assertEquals(400, response.first)
        assertTrue(response.second!!.contains("-32020"))
    }

    @Test
    fun notificationReturns202() {
        val body = """{"jsonrpc":"2.0","method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}}"""
        val response = post(body)

        assertEquals(202, response.first)
        assertEquals("", response.second)
    }

    @Test
    fun sseOnlyAcceptIsWrappedAsOneEvent() {
        val response = post(modernBody(), accept = "text/event-stream")

        assertEquals(200, response.first)
        assertTrue(response.third!!.startsWith("text/event-stream"))
        assertTrue(response.second!!.startsWith("data: "))
    }

    private fun modernBody() =
        """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}}"""

    private fun post(
        body: String,
        authorization: String = "Bearer $token",
        origin: String? = null,
        mcpMethod: String? = "tools/list",
        accept: String = "application/json, text/event-stream",
        port: Int = this.port,
    ): Triple<Int, String?, String?> {
        val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("Authorization", authorization)
            connection.setRequestProperty("MCP-Protocol-Version", "2026-07-28")
            if (mcpMethod != null) connection.setRequestProperty("Mcp-Method", mcpMethod)
            if (origin != null) connection.setRequestProperty("Origin", origin)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            Triple(code, text, connection.contentType)
        } finally {
            connection.disconnect()
        }
    }

    private fun waitForPort(target: Int = port) {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket("127.0.0.1", target).use { return }
            } catch (_: Exception) {
                Thread.sleep(25)
            }
        }
        error("server did not start on port $target")
    }
}
