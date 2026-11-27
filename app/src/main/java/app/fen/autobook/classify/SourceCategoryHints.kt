package app.fen.autobook.classify

object SourceCategoryHints {
    fun categoryName(sourceId: String?, sourcePage: String?): String? = when (sourceId) {
        "meituan", "meituan_takeout" -> "餐饮"
        "jd", "taobao", "pdd", "douyin" -> "购物"
        else -> when (sourcePage) {
            "order_detail" -> "购物"
            else -> null
        }
    }
}
