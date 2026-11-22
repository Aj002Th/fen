package app.fen

import android.app.Application
import app.fen.autobook.classify.AutoMapStore
import app.fen.autobook.classify.SqliteAutoMapStore
import app.fen.data.AppPrefs
import app.fen.data.FenDb
import app.fen.data.FenRepository

class FenApp : Application() {

    lateinit var repository: FenRepository
        private set

    lateinit var prefs: AppPrefs
        private set

    lateinit var autoMap: AutoMapStore
        private set

    override fun onCreate() {
        super.onCreate()
        val db = FenDb(this)
        repository = FenRepository(db)
        prefs = AppPrefs(this)
        autoMap = SqliteAutoMapStore(db)
    }
}
