package app.fen.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.fen.FenApp
import app.fen.data.Snapshot
import app.fen.data.StatsBoardConfig
import app.fen.data.StatsMode
import app.fen.data.StatsSection
import app.fen.data.Tx
import app.fen.data.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class FenViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val STATS_RANGE_FRESH_MS = 3 * 60 * 1000L
    }

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

    private val _statsMode = MutableStateFlow(loadStatsMode())
    val statsMode: StateFlow<StatsMode> = _statsMode.asStateFlow()

    private val _statsAnchor = MutableStateFlow(loadStatsAnchor())
    val statsAnchor: StateFlow<LocalDate> = _statsAnchor.asStateFlow()

    private val _statsCustomRange = MutableStateFlow(loadStatsRange())
    val statsCustomRange: StateFlow<Pair<LocalDate, LocalDate>> = _statsCustomRange.asStateFlow()

    private val _statsCategoryType = MutableStateFlow(loadStatsCategoryType())
    val statsCategoryType: StateFlow<TxType> = _statsCategoryType.asStateFlow()

    private val _statsBoardConfig = MutableStateFlow(prefs.statsBoardConfig())
    val statsBoardConfig: StateFlow<StatsBoardConfig> = _statsBoardConfig.asStateFlow()

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

    /* ------------------------------ 统计看板 ------------------------------ */

    fun setStatsMode(mode: StatsMode) {
        _statsMode.value = mode
        prefs.statsMode = mode.name
        if (mode == StatsMode.RANGE) {
            val (start, end) = _statsCustomRange.value
            prefs.statsRangeStartEpochDay = start.toEpochDay()
            prefs.statsRangeEndEpochDay = end.toEpochDay()
        }
    }

    fun shiftStatsPeriod(delta: Long) {
        val mode = _statsMode.value
        if (mode == StatsMode.ALL || mode == StatsMode.RANGE) return
        val current = _statsAnchor.value
        val shifted = when (mode) {
            StatsMode.WEEK -> current.plusWeeks(delta)
            StatsMode.MONTH -> current.plusMonths(delta)
            StatsMode.YEAR -> current.plusYears(delta)
            StatsMode.ALL, StatsMode.RANGE -> return
        }
        val today = LocalDate.now()
        val shiftedStart = startOfStatsPeriod(mode, shifted)
        val todayStart = startOfStatsPeriod(mode, today)
        if (shiftedStart.isAfter(todayStart)) return
        _statsAnchor.value = shifted
        prefs.statsAnchorEpochDay = shifted.toEpochDay()
    }

    fun setStatsCustomRange(start: LocalDate, end: LocalDate) {
        val from = if (start <= end) start else end
        val until = if (start <= end) end else start
        _statsCustomRange.value = from to until
        _statsMode.value = StatsMode.RANGE
        prefs.statsRangeStartEpochDay = from.toEpochDay()
        prefs.statsRangeEndEpochDay = until.toEpochDay()
        prefs.statsMode = StatsMode.RANGE.name
        prefs.statsRangeSelectedAt = System.currentTimeMillis()
    }

    /** 3 分钟内刚选过范围就沿用，不重复弹框。 */
    fun shouldPromptForStatsRange(): Boolean {
        val last = prefs.statsRangeSelectedAt
        return last <= 0L || System.currentTimeMillis() - last > STATS_RANGE_FRESH_MS
    }

    /** 进入“范围”且沿用旧区间时刷新时间，让连续使用不会反复弹框。 */
    fun touchStatsRange() {
        prefs.statsRangeSelectedAt = System.currentTimeMillis()
    }

    fun setStatsCategoryType(type: TxType) {
        _statsCategoryType.value = type
        prefs.statsCategoryType = type.name
    }

    fun setStatsSectionVisible(section: StatsSection, visible: Boolean) {
        val config = _statsBoardConfig.value
        updateStatsBoard(
            config.copy(
                hidden = if (visible) config.hidden - section else config.hidden + section,
            )
        )
    }

    fun moveStatsSection(section: StatsSection, direction: Int) {
        val config = _statsBoardConfig.value
        val index = config.order.indexOf(section)
        if (index < 0) return
        val target = index + direction
        if (target !in config.order.indices) return
        val order = config.order.toMutableList()
        val temp = order[index]
        order[index] = order[target]
        order[target] = temp
        updateStatsBoard(config.copy(order = order))
    }

    fun setStatsRankingLimit(limit: Int) {
        updateStatsBoard(_statsBoardConfig.value.copy(rankingLimit = limit))
    }

    fun setStatsSummaryShowEmpty(show: Boolean) {
        updateStatsBoard(_statsBoardConfig.value.copy(summaryShowEmpty = show))
    }

    fun resetStatsBoard() {
        updateStatsBoard(StatsBoardConfig())
    }

    private fun updateStatsBoard(config: StatsBoardConfig) {
        _statsBoardConfig.value = config
        prefs.saveStatsBoardConfig(config)
    }

    private fun loadStatsMode(): StatsMode =
        runCatching { StatsMode.valueOf(prefs.statsMode) }.getOrDefault(StatsMode.MONTH)

    private fun loadStatsAnchor(): LocalDate {
        val saved = LocalDate.ofEpochDay(prefs.statsAnchorEpochDay)
        val today = LocalDate.now()
        return if (saved.isAfter(today)) today else saved
    }

    private fun loadStatsRange(): Pair<LocalDate, LocalDate> {
        val start = LocalDate.ofEpochDay(prefs.statsRangeStartEpochDay)
        val end = LocalDate.ofEpochDay(prefs.statsRangeEndEpochDay)
        return if (start <= end) start to end else end to start
    }

    private fun loadStatsCategoryType(): TxType =
        if (prefs.statsCategoryType == TxType.INCOME.name) TxType.INCOME else TxType.EXPENSE

    private fun startOfStatsPeriod(mode: StatsMode, date: LocalDate): LocalDate = when (mode) {
        StatsMode.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatsMode.MONTH -> date.withDayOfMonth(1)
        StatsMode.YEAR -> LocalDate.of(date.year, 1, 1)
        StatsMode.ALL, StatsMode.RANGE -> date
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
