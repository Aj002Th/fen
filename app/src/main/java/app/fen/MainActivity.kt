package app.fen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.fen.autobook.capture.ImportIntentGate
import app.fen.autobook.capture.IntentImageReader
import app.fen.ui.FenRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val openMcp = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openMcp.value = intent?.getBooleanExtra(EXTRA_OPEN_MCP, false) == true
        if (savedInstanceState == null) {
            handleDebugIntent(intent)
        }
        handleImportIntent(intent, restored = savedInstanceState != null)
        setContent {
            FenRoot(
                openMcpRequest = openMcp.value,
                onMcpOpened = { openMcp.value = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openMcp.value = intent.getBooleanExtra(EXTRA_OPEN_MCP, false)
        handleDebugIntent(intent)
        handleImportIntent(intent, restored = false)
    }

    private fun handleImportIntent(intent: Intent?, restored: Boolean) {
        if (intent == null) return
        val uris = IntentImageReader.allImages(intent)
        val shouldHandle = if (restored) {
            ImportIntentGate.shouldHandleOnCreate(restored = true, hasImage = uris.isNotEmpty())
        } else {
            ImportIntentGate.shouldHandleNewIntent(hasImage = uris.isNotEmpty())
        }
        if (!shouldHandle) return
        val parsed = uris.map(Uri::parse)
        val imageImport = (application as FenApp).imageImport
        if (parsed.size == 1) {
            imageImport.submit(parsed.first())
        } else {
            imageImport.submitAll(parsed)
        }
    }

    /**
     * 仅 debug 构建生效，用于注入验收数据：
     * adb shell am start -a android.intent.action.VIEW -d "fen://debug/seed?count=400"
     * adb shell am start -a android.intent.action.VIEW -d "fen://debug/clear"
     */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val uri = intent?.data ?: return
        if (uri.scheme != "fen" || uri.host != "debug") return
        val repository = (application as FenApp).repository
        when (uri.path?.trimEnd('/')) {
            "/seed" -> {
                val count = uri.getQueryParameter("count")?.toIntOrNull() ?: 400
                val days = uri.getQueryParameter("days")?.toIntOrNull() ?: 180
                CoroutineScope(Dispatchers.IO).launch { repository.seedDemoData(count, days) }
            }
            "/clear" -> CoroutineScope(Dispatchers.IO).launch { repository.clearTransactions() }
            "/mcp/start" -> (application as FenApp).mcp.start()
            "/mcp/stop" -> (application as FenApp).mcp.stop()
            "/mcp/write" -> {
                val minutes = uri.getQueryParameter("minutes")?.toLongOrNull()
                (application as FenApp).mcp.setWriteDuration(
                    if (minutes == null || minutes <= 0) null else minutes * 60_000
                )
            }
            "/mcp/undo" -> {
                val batch = uri.getQueryParameter("batch")
                Log.i("FenMcp", "undo requested batch=$batch")
                if (batch != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        Log.i("FenMcp", "undo result=${(application as FenApp).mcp.undo(batch)}")
                    }
                }
            }
            "/mcp/test" -> CoroutineScope(Dispatchers.IO).launch {
                val result = (application as FenApp).mcp.selfTest()
                Log.i("FenMcp", "selfTest=$result")
            }
            "/update-source" -> {
                (application as FenApp).prefs.updateSourceOverride =
                    uri.getQueryParameter("json").orEmpty()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_MCP = "open_mcp"
    }
}
