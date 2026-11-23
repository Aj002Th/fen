package app.fen.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.fen.FenApp
import app.fen.data.ledger.UndoResult
import app.fen.mcp.McpServerState
import app.fen.mcp.SelfTestResult
import app.fen.mcp.audit.CallRecord
import app.fen.mcp.tools.ToolDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class McpViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = (app as FenApp).mcp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<McpServerState> = controller.state

    private val _calls = MutableStateFlow<List<CallRecord>>(emptyList())
    val calls: StateFlow<List<CallRecord>> = _calls.asStateFlow()

    fun start() = controller.start()
    fun stop() = controller.stop()
    fun setWriteDuration(durationMs: Long?) = controller.setWriteDuration(durationMs)
    fun resetToken() = controller.resetToken()
    fun token(): String = controller.token()
    fun toolDescriptors(writeEnabled: Boolean): List<ToolDescriptor> =
        controller.toolDescriptors(writeEnabled)

    fun autoStartEnabled(): Boolean = controller.autoStartEnabled()
    fun localOnly(): Boolean = controller.localOnly()
    fun idleMinutes(): Int = controller.idleMinutes()
    fun queryLimit(): Int = controller.queryLimit()

    fun setAutoStart(value: Boolean) = controller.setAutoStart(value)
    fun setLocalOnly(value: Boolean) = controller.setLocalOnly(value)
    fun setIdleMinutes(value: Int) = controller.setIdleMinutes(value)
    fun setQueryLimit(value: Int) = controller.setQueryLimit(value)

    fun refreshCalls() {
        scope.launch { _calls.value = controller.recentCalls(100) }
    }

    suspend fun selfTest(): SelfTestResult = controller.selfTest()
    suspend fun undo(batchId: String): UndoResult = controller.undo(batchId)

    fun clearLog() {
        controller.clearCallLog()
        _calls.value = emptyList()
    }
}
