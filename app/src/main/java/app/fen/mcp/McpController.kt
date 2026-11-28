package app.fen.mcp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.isActive
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
        batchLimit = { config.writeBatchLimit },
    )
    private val registry = ToolRegistry(
        readTools = ReadTools(
            reader = reader,
            serverVersion = BuildConfig.VERSION_NAME,
            writeEnabled = { gate.isEnabled(config.writeUntil) },
            defaultLimit = { config.queryLimit },
        ),
        writeTools = WriteTools(
            service = writeService,
            batchLimit = { config.writeBatchLimit },
        ),
        writeEnabled = { gate.isEnabled(config.writeUntil) },
    )
    private val dispatcher = ProtocolDispatcher(
        tools = { registry.tools() },
        callTool = { name, args -> registry.call(name, args) },
        sessions = sessions,
        serverVersion = BuildConfig.VERSION_NAME,
    )
    private val handler = RpcHandler { call, requestContext ->
        lastActivity = System.currentTimeMillis()
        callLogger.around(requestContext, call) { dispatcher.handle(call, requestContext) }
    }

    private var server: McpServer? = null
    private var expiryJob: Job? = null
    private var idleJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastActivity = System.currentTimeMillis()

    init {
        registerNetworkCallback()
        if (config.enabled && config.autoStart) {
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
        if (Build.VERSION.SDK_INT >= 37 && !config.localOnly &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = McpServerState.Error("需要本地网络权限")
            return
        }
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
                    host = if (config.localOnly) "127.0.0.1" else "0.0.0.0",
                    deleteSession = dispatcher::endSession,
                    rateLimitPerMinute = config.rateLimitPerMinute,
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
                    addresses = listOfNotNull(currentAddress()),
                    writeUntil = config.writeUntil,
                )
                scheduleExpiry()
                startIdleWatch()
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
        idleJob?.cancel()
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

    fun port(): Int = config.port

    fun lanAddresses(): List<String> = currentAddresses()

    fun writeUntil(): Long? = config.writeUntil

    fun autoStartEnabled(): Boolean = config.autoStart
    fun localOnly(): Boolean = config.localOnly
    fun idleMinutes(): Int = config.idleMinutes
    fun queryLimit(): Int = config.queryLimit
    fun rateLimitPerMinute(): Int = config.rateLimitPerMinute
    fun writeBatchLimit(): Int = config.writeBatchLimit

    fun setAutoStart(value: Boolean) {
        config.autoStart = value
    }

    fun setLocalOnly(value: Boolean) {
        config.localOnly = value
        if (_state.value is McpServerState.Running) {
            stopServer()
            startServer()
        }
    }

    fun setIdleMinutes(value: Int) {
        config.idleMinutes = value
        startIdleWatch()
    }

    fun setQueryLimit(value: Int) {
        config.queryLimit = value
    }

    fun setWriteBatchLimit(value: Int) {
        config.writeBatchLimit = value
    }

    fun setRateLimitPerMinute(value: Int) {
        config.rateLimitPerMinute = value
        if (_state.value is McpServerState.Running) {
            stopServer()
            startServer()
        }
    }

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
                error = if (modern.first && legacy.first) null else (modern.second ?: legacy.second),
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

    private fun startIdleWatch() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(60_000)
                val minutes = config.idleMinutes
                if (minutes <= 0) continue
                if (System.currentTimeMillis() - lastActivity >= minutes * 60_000L) {
                    Log.i("FenMcp", "idle timeout, stopping service")
                    stop()
                    break
                }
            }
        }
    }

    private fun updateRunning(block: (McpServerState.Running) -> McpServerState.Running) {
        val running = _state.value as? McpServerState.Running ?: return
        _state.value = block(running)
    }

    private fun currentAddresses(): List<String> =
        selectLanAddresses(currentLanCandidates())

    private fun currentAddress(): String? = currentAddresses().firstOrNull()

    private fun allowedHosts(port: Int): Set<String> =
        buildSet {
            add("127.0.0.1:$port")
            add("localhost:$port")
            if (!config.localOnly) {
                currentAddresses().forEach { add("$it:$port") }
            }
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
        val address = currentAddress()
        updateRunning { it.copy(addresses = listOfNotNull(address)) }
    }

    private fun post(port: Int, body: String, modern: Boolean): Pair<Boolean, String?> {
        val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000
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
