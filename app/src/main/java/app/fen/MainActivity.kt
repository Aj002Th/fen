package app.fen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.fen.autobook.capture.IntentImageReader
import app.fen.ui.FenRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleDebugIntent(intent)
        handleImportIntent(intent)
        setContent {
            FenRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        if (intent == null) return
        val uri = IntentImageReader.firstImage(intent) ?: return
        (application as FenApp).imageImport.submit(Uri.parse(uri))
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
        }
    }
}
