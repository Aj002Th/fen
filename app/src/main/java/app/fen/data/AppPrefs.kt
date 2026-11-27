package app.fen.data

import android.content.Context
import java.time.LocalDate

/** 轻量偏好设置：主题、搜索历史、统计看板。 */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("fen_prefs", Context.MODE_PRIVATE)

    /** 0 = 跟随系统，1 = 浅色，2 = 深色 */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME_MODE, 0)
        set(value) = sp.edit().putInt(KEY_THEME_MODE, value).apply()

    /** 被用户长按隐藏的「常用」分类 */
    var hiddenSuggestions: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN_SUGGESTIONS, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_HIDDEN_SUGGESTIONS, value).apply()

    /** 常用分类的降权次数（长按一次 +1，推荐分数按次数扣减） */
    var suggestionPenalties: Map<String, Int>
        get() = run {
            val raw = sp.getString(KEY_PENALTIES, "") ?: ""
            raw.split(";")
                .filter { it.contains(":") }
                .mapNotNull { entry ->
                    val parts = entry.split(":")
                    val count = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                    if (parts[0].isBlank()) null else parts[0] to count
                }
                .toMap()
        }
        set(value) = sp.edit().putString(
            KEY_PENALTIES,
            value.entries.joinToString(";") { "${it.key}:${it.value}" },
        ).apply()

    /** 最近的搜索关键词，最多 5 个 */
    var searchHistory: List<String>
        get() = (sp.getString(KEY_SEARCH_HISTORY, "") ?: "")
            .split("\n")
            .filter { it.isNotBlank() }
            .take(5)
        set(value) = sp.edit()
            .putString(KEY_SEARCH_HISTORY, value.filter { it.isNotBlank() }.take(5).joinToString("\n"))
            .apply()

    /* ------------------------------ 统计看板 ------------------------------ */

    /** 看板顺序（含隐藏项），逗号分隔的 StatsSection.key */
    var statsSectionOrder: List<String>
        get() = (sp.getString(KEY_STATS_SECTION_ORDER, "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        set(value) = sp.edit()
            .putString(KEY_STATS_SECTION_ORDER, value.joinToString(","))
            .apply()

    /** 被隐藏的看板 key */
    var statsHiddenSections: Set<String>
        get() = (sp.getString(KEY_STATS_HIDDEN, "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        set(value) = sp.edit()
            .putString(KEY_STATS_HIDDEN, value.joinToString(","))
            .apply()

    /** 分类排行显示数量：5 / 10 / 0（全部） */
    var statsRankingLimit: Int
        get() = sp.getInt(KEY_STATS_RANKING_LIMIT, 5)
        set(value) = sp.edit().putInt(KEY_STATS_RANKING_LIMIT, value).apply()

    var statsSummaryShowEmpty: Boolean
        get() = sp.getBoolean(KEY_STATS_SUMMARY_EMPTY, false)
        set(value) = sp.edit().putBoolean(KEY_STATS_SUMMARY_EMPTY, value).apply()

    /** 最后一次统计范围 */
    var statsMode: String
        get() = sp.getString(KEY_STATS_MODE, StatsMode.MONTH.name) ?: StatsMode.MONTH.name
        set(value) = sp.edit().putString(KEY_STATS_MODE, value).apply()

    var statsAnchorEpochDay: Long
        get() = sp.getLong(KEY_STATS_ANCHOR, LocalDate.now().toEpochDay())
        set(value) = sp.edit().putLong(KEY_STATS_ANCHOR, value).apply()

    var statsRangeStartEpochDay: Long
        get() = sp.getLong(KEY_STATS_RANGE_START, LocalDate.now().minusDays(29).toEpochDay())
        set(value) = sp.edit().putLong(KEY_STATS_RANGE_START, value).apply()

    var statsRangeEndEpochDay: Long
        get() = sp.getLong(KEY_STATS_RANGE_END, LocalDate.now().toEpochDay())
        set(value) = sp.edit().putLong(KEY_STATS_RANGE_END, value).apply()

    /** 上一次确认/沿用自定义范围的时间，用于 3 分钟内不再弹选择框 */
    var statsRangeSelectedAt: Long
        get() = sp.getLong(KEY_STATS_RANGE_SELECTED_AT, 0L)
        set(value) = sp.edit().putLong(KEY_STATS_RANGE_SELECTED_AT, value).apply()

    /** 分类占比/排行共用：EXPENSE / INCOME */
    var statsCategoryType: String
        get() = sp.getString(KEY_STATS_CATEGORY_TYPE, TxType.EXPENSE.name) ?: TxType.EXPENSE.name
        set(value) = sp.edit().putString(KEY_STATS_CATEGORY_TYPE, value).apply()

    fun statsBoardConfig(): StatsBoardConfig = StatsBoardConfig(
        order = sanitizeStatsOrder(statsSectionOrder),
        hidden = statsHiddenSections.mapNotNull { StatsSection.fromKey(it) }.toSet(),
        rankingLimit = statsRankingLimit,
        summaryShowEmpty = statsSummaryShowEmpty,
    )

    fun saveStatsBoardConfig(config: StatsBoardConfig) {
        statsSectionOrder = config.order.map { it.key }
        statsHiddenSections = config.hidden.map { it.key }.toSet()
        statsRankingLimit = config.rankingLimit
        statsSummaryShowEmpty = config.summaryShowEmpty
    }

    /* ------------------------------ 版本更新 ------------------------------ */

    /** 上一次联网检查更新的时间（毫秒） */
    var lastUpdateCheckAt: Long
        get() = sp.getLong(KEY_UPDATE_CHECK_AT, 0L)
        set(value) = sp.edit().putLong(KEY_UPDATE_CHECK_AT, value).apply()

    /** 最近一次成功检查得到的 release JSON 快照 */
    var cachedReleaseJson: String
        get() = sp.getString(KEY_UPDATE_RELEASE_JSON, "") ?: ""
        set(value) = sp.edit().putString(KEY_UPDATE_RELEASE_JSON, value).apply()

    /** 仅 debug：覆盖 release JSON 地址（验收用） */
    var updateSourceOverride: String
        get() = sp.getString(KEY_UPDATE_SOURCE, "") ?: ""
        set(value) = sp.edit().putString(KEY_UPDATE_SOURCE, value).apply()

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HIDDEN_SUGGESTIONS = "hidden_suggestions"
        const val KEY_PENALTIES = "suggestion_penalties"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_STATS_SECTION_ORDER = "stats_section_order"
        const val KEY_STATS_HIDDEN = "stats_hidden_sections"
        const val KEY_STATS_RANKING_LIMIT = "stats_ranking_limit"
        const val KEY_STATS_SUMMARY_EMPTY = "stats_summary_show_empty"
        const val KEY_STATS_MODE = "stats_mode"
        const val KEY_STATS_ANCHOR = "stats_anchor_epoch_day"
        const val KEY_STATS_RANGE_START = "stats_range_start_epoch_day"
        const val KEY_STATS_RANGE_END = "stats_range_end_epoch_day"
        const val KEY_STATS_RANGE_SELECTED_AT = "stats_range_selected_at"
        const val KEY_STATS_CATEGORY_TYPE = "stats_category_type"
        const val KEY_UPDATE_CHECK_AT = "update_check_at"
        const val KEY_UPDATE_RELEASE_JSON = "update_release_json"
        const val KEY_UPDATE_SOURCE = "update_source"
    }
}
