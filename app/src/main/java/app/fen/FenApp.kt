package app.fen

import android.app.Application
import app.fen.data.AppPrefs
import app.fen.data.FenDb
import app.fen.data.FenRepository

class FenApp : Application() {

    lateinit var repository: FenRepository
        private set

    lateinit var prefs: AppPrefs
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FenRepository(FenDb(this))
        prefs = AppPrefs(this)
    }
}
