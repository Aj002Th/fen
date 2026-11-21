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

class FenViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as FenApp).repository

    val snapshot: StateFlow<Snapshot> = repository.snapshot

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    fun shiftMonth(months: Long) {
        _month.value = _month.value.plusMonths(months)
    }

    fun jumpTo(target: YearMonth) {
        _month.value = target
    }

    fun addTransaction(
        type: TxType,
        amountMinor: Long,
        accountId: String,
        categoryId: String?,
        note: String,
        date: LocalDate,
    ) {
        repository.addTransaction(
            type = type,
            amountMinor = amountMinor,
            accountId = accountId,
            categoryId = categoryId,
            note = note.trim(),
            occurredAt = date.toEpochMillisCompat(),
        )
    }

    fun setBudget(period: YearMonth, amountMinor: Long) {
        repository.setBudget(period.toString(), amountMinor)
    }

    fun delete(tx: Tx) = repository.deleteTransaction(tx.id)

    private fun LocalDate.toEpochMillisCompat(): Long =
        atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
