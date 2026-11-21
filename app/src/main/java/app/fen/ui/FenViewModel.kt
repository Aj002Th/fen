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
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class FenViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as FenApp).repository
    private val prefs = (app as FenApp).prefs
    val snapshot: StateFlow<Snapshot> = repository.snapshot

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _hiddenSuggestions = MutableStateFlow(prefs.hiddenSuggestions)
    val hiddenSuggestions: StateFlow<Set<String>> = _hiddenSuggestions.asStateFlow()

    private val _penalties = MutableStateFlow(prefs.suggestionPenalties)
    val suggestionPenalties: StateFlow<Map<String, Int>> = _penalties.asStateFlow()

    private val _searchHistory = MutableStateFlow(prefs.searchHistory)
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

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

    fun hideSuggestion(categoryId: String) {
        prefs.hiddenSuggestions = prefs.hiddenSuggestions + categoryId
        _hiddenSuggestions.value = prefs.hiddenSuggestions
    }

    /** 长按常用分类：降低它在推荐里的权重 */
    fun penalizeSuggestion(categoryId: String) {
        val current = prefs.suggestionPenalties
        prefs.suggestionPenalties = current + (categoryId to ((current[categoryId] ?: 0) + 1))
        _penalties.value = prefs.suggestionPenalties
    }

    fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        prefs.searchHistory = (listOf(q) + prefs.searchHistory.filter { it != q }).take(5)
        _searchHistory.value = prefs.searchHistory
    }

    fun clearSearchHistory() {
        prefs.searchHistory = emptyList()
        _searchHistory.value = emptyList()
    }

    fun addTransaction(
        type: TxType,
        amountMinor: Long,
        categoryId: String?,
        note: String,
        dateTime: LocalDateTime,
    ) {
        repository.addTransaction(
            type = type,
            amountMinor = amountMinor,
            categoryId = categoryId,
            note = note,
            occurredAt = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    fun updateTransaction(tx: Tx) = repository.updateTransaction(tx)

    fun delete(tx: Tx) = repository.deleteTransaction(tx.id)

    fun addCategory(name: String, type: TxType, icon: String, color: Long) =
        repository.addCategory(name, type, icon, color)

    fun updateCategory(category: app.fen.data.Category) = repository.updateCategory(category)

    fun deleteCategory(category: app.fen.data.Category) = repository.deleteCategory(category.id)

    fun mergeCategory(from: app.fen.data.Category, into: app.fen.data.Category) =
        repository.mergeCategory(from.id, into.id)

    fun deleteCategoryWithTransactions(category: app.fen.data.Category) =
        repository.deleteCategoryWithTransactions(category.id)

    fun categoryUsage(categoryId: String): Int = repository.transactionCount(categoryId)

    fun seedDemoData(count: Int = 400) {
        repository.seedDemoData(count)
    }

    fun clearTransactions() {
        repository.clearTransactions()
    }
}
