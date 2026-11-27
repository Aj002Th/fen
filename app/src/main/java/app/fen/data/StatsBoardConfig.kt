package app.fen.data

enum class StatsSection(val key: String, val title: String, val subtitle: String) {
    OVERVIEW("overview", "总览", "支出、收入、结余与关键指标"),
    CATEGORY_MIX("category_mix", "分类占比", "环形图与分类图例"),
    CATEGORY_RANKING("category_ranking", "分类排行", "金额、占比、笔数与均值"),
    TREND("trend", "收支趋势", "按日/月/年的双色柱状图"),
    BALANCE("balance", "累计结余", "区间内累计净结余走势"),
    COMPARISON("comparison", "收支对比", "与上期的金额和百分比"),
    WEEKDAY("weekday", "星期分布", "周一至周日的支出分布"),
    SUMMARY("summary", "账单汇总", "逐日/月/年的明细表");

    companion object {
        val defaultOrder: List<StatsSection> = listOf(
            OVERVIEW,
            CATEGORY_MIX,
            CATEGORY_RANKING,
            TREND,
            BALANCE,
            COMPARISON,
            WEEKDAY,
            SUMMARY,
        )

        fun fromKey(key: String): StatsSection? = entries.firstOrNull { it.key == key }
    }
}

data class StatsBoardConfig(
    val order: List<StatsSection> = StatsSection.defaultOrder,
    val hidden: Set<StatsSection> = emptySet(),
    val rankingLimit: Int = 5,
    val summaryShowEmpty: Boolean = false,
) {
    val visibleSections: List<StatsSection>
        get() = order.filter { it !in hidden }
}

/** 过滤未知 key、去重、补齐新增看板，保证配置永远可用。 */
fun sanitizeStatsOrder(raw: List<String>): List<StatsSection> {
    val seen = linkedSetOf<StatsSection>()
    raw.forEach { key -> StatsSection.fromKey(key)?.let { seen.add(it) } }
    StatsSection.defaultOrder.forEach { seen.add(it) }
    return seen.toList()
}
