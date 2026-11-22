package app.fen.mcp

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import app.fen.BuildConfig
import app.fen.data.ledger.LedgerReader
import app.fen.data.ledger.LedgerWriter
import app.fen.mcp.audit.CallLogger
import app.fen.mcp.audit.CallRecord
import app.fen.mcp.audit.McpAuditDb
import app.fen.mcp.auth.TokenAuth
import app.fen.mcp.net.currentLanCandidates
import app.fen.mcp.net.selectLanAddresses
import app.fen.mcp.protocol.ProtocolDispatcher
import app.fen.mcp.protocol.SessionStore
import app.fen.mcp.tools.ReadTools
import app.fen.mcp.tools.ToolDescriptor
import app.fen.mcp.tools.ToolRegistry
import app.fen.mcp.tools.WriteTools
import app.fen.mcp.write.WriteGate
import app.fen.mcp.write.WriteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val writer: LedgerWriter,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    val state: StateFlow<McpServerState> = _state.asStateFlow()

    private val config = McpConfig(context)
    private val gate = WriteGate(System::currentTimeMillis)
    private val sessions = SessionStore(System::currentTimeMillis)
    private val auditDb = McpAuditDb(context)
    private val callLogger = CallLogger(
        store = auditDb,
        clock = System::currentTimeMillis,
        tokenFingerprint = TokenAuth.fingerprint(config.token),
    )
    private val writeService = WriteService(
        reader = reader,
        writer = writer,
        clock = System::currentTimeMillis,
    )
    private val registry = ToolRegistry(
        readTools = ReadTools(
            reader = reader,
            serverVersion = BuildConfig.VERSION_NAME,
            writeEnabled = { gate.isEnabled(config.writeUntil) },
        ),
        writeTools = WriteTools(writeService),
        writeEnabled = { gate.isEnabled(config.writeUntil) },
    )
    private val dispatcher = ProtocolDispatcher(
        tools = { registry.tools() },
        callTool = { name, args -> registry.call(name, args) },
        sessions = sessions,
        serverVersion = BuildConfig.VERSION_NAME,
    )
    private val handler = RpcHandler { call, requestContext ->
        callLogger.around(requestContext, call) { dispatcher.handle(call, requestContext) }
    }

    private var server: McpServer? = null
    private var expiryJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerNetworkCallback()
        if (config.enabled) {
            runCatching { McpService.start(context) }
            scope.launch { startServer() }
        }
    }

    fun start() {
        config.enabled = true
        McpService.start(context)
        startServer()
    }

    fun startServer() {
        val current = _state.value
        if (current is McpServerState.Running || current is McpServerState.Starting) return
        _state.value = McpServerState.Starting
        scope.launch {
            runCatching { auditDb.prune() }
            val port = config.port
            try {
                val instance = McpServer(
                    port = port,
                    token = config.token,
                    handler = handler,
                    allowedHosts = { allowedHosts(port) },
                    deleteSession = dispatcher::endSession,
                    onRejected = { headers, ip, status, message ->
                        scope.launch {
                            runCatching {
                                auditDb.insert(
                                    CallRecord(
                                        tsMs = System.currentTimeMillis(),
                                        clientIp = ip,
                                        clientName = null,
                                        clientVersion = null,
                                        era = if (headers.sessionId != null) "LEGACY" else "UNKNOWN",
                                        protocolVersion = headers.protocolVersion,
                                        method = "transport",
                                        tool = null,
                                        argsSummary = null,
                                        ok = false,
                                        httpStatus = status,
                                        errorCode = "REJECTED",
                                        errorMessage = message,
                                        durationMs = 0,
                                        requestId = null,
                                        batchId = null,
                                        tokenFp = TokenAuth.fingerprint(config.token),
                                        bytesIn = 0,
                                        bytesOut = 0,
                                    )
                                )
                            }
                        }
                    },
                )
                instance.start()
                server = instance
                _state.value = McpServerState.Running(
                    port = port,
                    addresses = currentAddresses(),
                    writeUntil = config.writeUntil,
                )
                scheduleExpiry()
                Log.i("FenMcp", "running on $port")
            } catch (e: Exception) {
                server = null
                _state.value = McpServerState.Error(e.message ?: "服务启动失败")
                Log.e("FenMcp", "start failed", e)
            }
        }
    }

    fun stop() {
        config.enabled = false
        stopServer()
        context.stopService(Intent(context, McpService::class.java))
    }

    fun stopServer() {
        expiryJob?.cancel()
        server?.stop()
        server = null
        _state.value = McpServerState.Stopped
    }

    fun setWriteDuration(durationMs: Long?) {
        config.writeUntil = durationMs?.let { System.currentTimeMillis() + it }
        updateRunning { it.copy(writeUntil = config.writeUntil) }
        scheduleExpiry()
    }

    fun token(): String = config.token

    fun writeUntil(): Long? = config.writeUntil

    fun toolDescriptors(writeEnabled: Boolean = gate.isEnabled(config.writeUntil)): List<ToolDescriptor> =
        registry.tools(writeEnabled)

    fun resetToken() {
        config.resetToken()
        sessions.clear()
        val running = _state.value as? McpServerState.Running
        if (running != null) {
            stopServer()
            startServer()
        }
    }

    suspend fun recentCalls(limit: Int): List<CallRecord> = withContext(Dispatchers.IO) {
        auditDb.recent(limit)
    }

    fun clearCallLog() {
        auditDb.clear()
    }

    suspend fun undo(batchId: String): app.fen.data.ledger.UndoResult = withContext(Dispatchers.IO) {
        writer.undoBatch(batchId)
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

    private fun scheduleExpiry() {
        expiryJob?.cancel()
        val until = config.writeUntil ?: return
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0) {
            updateRunning { it.copy(writeUntil = null) }
            return
        }
        expiryJob = scope.launch {
            delay(remaining + 50)
            if (!gate.isEnabled(config.writeUntil)) {
                config.writeUntil = null
                updateRunning { it.copy(writeUntil = null) }
            }
        }
    }

    private fun updateRunning(block: (McpServerState.Running) -> McpServerState.Running) {
        val running = _state.value as? McpServerState.Running ?: return
        _state.value = block(running)
    }

    private fun currentAddresses(): List<String> =
        selectLanAddresses(currentLanCandidates())

    private fun allowedHosts(port: Int): Set<String> =
        buildSet {
            add("127.0.0.1:$port")
            add("localhost:$port")
            currentAddresses().forEach { add("$it:$port") }
        }

    private fun registerNetworkCallback() {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = updateAddresses()
            override fun onLost(network: Network) = updateAddresses()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) =
                updateAddresses()
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        networkCallback = callback
    }

    private fun updateAddresses() {
        val addresses = currentAddresses()
        updateRunning { it.copy(addresses = addresses) }
    }

    private fun post(port: Int, body: String, modern: Boolean): Pair<Boolean, String?> {
        val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json, text/event-stream")
            connection.setRequestProperty("Authorization", "Bearer ${config.token}")
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
