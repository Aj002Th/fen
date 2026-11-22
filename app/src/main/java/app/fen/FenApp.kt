package app.fen

import android.app.Application
import app.fen.data.AppPrefs
import app.fen.data.FenDb
import app.fen.data.FenRepository
import app.fen.mcp.McpController

class FenApp : Application() {

    lateinit var repository: FenRepository
        private set

    lateinit var prefs: AppPrefs
        private set

    lateinit var mcp: McpController
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FenRepository(FenDb(this))
        prefs = AppPrefs(this)
        mcp = McpController(this, repository, repository)
    }
}
