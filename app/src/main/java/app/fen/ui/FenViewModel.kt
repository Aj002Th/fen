package app.fen.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.fen.FenApp
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth

import app.fen.util.defaultInstant
import app.fen.util.toEpochMillis

class FenViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as FenApp).repository
    private val prefs = (app as FenApp).prefs

    val snapshot: StateFlow<Snapshot> = repository.snapshot

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun shiftMonth(months: Long) {
        _month.value = _month.value.plusMonths(months)
    }

    fun jumpTo(target: YearMonth) {
        _month.value = target
    }

    fun setThemeMode(mode: Int) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun addTransaction(
        type: TxType,
        amountMinor: Long,
        categoryId: String?,
        note: String,
        date: LocalDate,
    ) {
        repository.addTransaction(
            type = type,
            amountMinor = amountMinor,
            categoryId = categoryId,
            note = note,
            occurredAt = date.defaultInstant(),
        )
    }

    fun updateTransaction(tx: Tx) = repository.updateTransaction(tx)

    fun delete(tx: Tx) = repository.deleteTransaction(tx.id)
}
