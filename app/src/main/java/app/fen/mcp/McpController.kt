package app.fen.mcp

import android.content.Context
import android.util.Log
import app.fen.BuildConfig
import app.fen.data.ledger.LedgerReader
import app.fen.mcp.audit.CallLogger
import app.fen.mcp.audit.CallRecord
import app.fen.mcp.audit.McpAuditDb
import app.fen.mcp.auth.TokenAuth
import app.fen.mcp.protocol.ProtocolDispatcher
import app.fen.mcp.protocol.SessionStore
import app.fen.mcp.tools.ReadTools
import app.fen.mcp.tools.ToolRegistry
import app.fen.mcp.tools.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

class McpController(
    private val context: Context,
    private val reader: LedgerReader,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    val state: StateFlow<McpServerState> = _state.asStateFlow()

    private var server: McpServer? = null
    private val port = 8067
    private val token = "dev-token"

    private val sessions = SessionStore(System::currentTimeMillis)
    private val auditDb = McpAuditDb(context)
    private val callLogger = CallLogger(
        store = auditDb,
        clock = System::currentTimeMillis,
        tokenFingerprint = TokenAuth.fingerprint(token),
    )
    private val registry = ToolRegistry(
        ReadTools(
            reader = reader,
            serverVersion = BuildConfig.VERSION_NAME,
            writeEnabled = { false },
        )
    )
    private val dispatcher = ProtocolDispatcher(
        tools = { registry.tools(false) },
        callTool = { name, args -> registry.call(name, args, false) },
        sessions = sessions,
        serverVersion = BuildConfig.VERSION_NAME,
    )
    private val handler = RpcHandler { call, requestContext ->
        callLogger.around(requestContext, call) { dispatcher.handle(call, requestContext) }
    }

    fun start() {
        val current = _state.value
        if (current is McpServerState.Running || current is McpServerState.Starting) return
        _state.value = McpServerState.Starting
        Log.i("FenMcp", "start requested on $port")
        scope.launch {
            runCatching { auditDb.prune() }
        }
        scope.launch {
            try {
                val instance = McpServer(
                    port = port,
                    token = token,
                    handler = handler,
                    allowedHosts = { setOf("127.0.0.1:$port", "localhost:$port") },
                    deleteSession = dispatcher::endSession,
                )
                instance.start()
                server = instance
                _state.value = McpServerState.Running(port = port, addresses = emptyList())
                Log.i("FenMcp", "running on $port")
            } catch (e: Exception) {
                Log.e("FenMcp", "start failed", e)
                server = null
                _state.value = McpServerState.Error(e.message ?: "服务启动失败")
            }
        }
    }

    fun stop() {
        server?.stop()
        server = null
        _state.value = McpServerState.Stopped
    }

    suspend fun recentCalls(limit: Int): List<CallRecord> = withContext(Dispatchers.IO) {
        auditDb.recent(limit)
    }

    fun clearCallLog() {
        auditDb.clear()
    }

    suspend fun selfTest(): SelfTestResult = withContext(Dispatchers.IO) {
        val running = _state.value as? McpServerState.Running
            ?: return@withContext SelfTestResult(false, false, 0, 0, "服务未启动")
        val started = System.currentTimeMillis()
        return@withContext try {
            val modern = post(
                port = running.port,
                body = """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}""",
                modern = true,
            )
            val legacy = post(
                port = running.port,
                body = """{"jsonrpc":"2.0","id":2,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"self-test","version":"1"}}}""",
                modern = false,
            )
            SelfTestResult(
                modernOk = modern.first,
                legacyOk = legacy.first,
                toolCount = modern.second?.let { text ->
                    runCatching {
                        Json.parseToJsonElement(text).jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray.size
                    }.getOrNull()
                } ?: 0,
                latencyMs = System.currentTimeMillis() - started,
                error = modern.second ?: legacy.second,
            )
        } catch (e: Exception) {
            SelfTestResult(false, false, 0, System.currentTimeMillis() - started, e.message)
        }
    }

    private fun post(port: Int, body: String, modern: Boolean): Pair<Boolean, String?> {
        val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json, text/event-stream")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (modern) {
                connection.setRequestProperty("MCP-Protocol-Version", "2026-07-28")
                connection.setRequestProperty("Mcp-Method", "tools/list")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            (code == HttpURLConnection.HTTP_OK) to text.ifBlank { null }
        } finally {
            connection.disconnect()
        }
    }
}
