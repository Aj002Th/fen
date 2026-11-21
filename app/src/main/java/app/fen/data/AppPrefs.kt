package app.fen.data

import android.content.Context

/** 轻量偏好设置：目前只有主题模式。 */
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

    /** 最近导入过的文件指纹（SHA-256），用于提示重复导入 */
    var importHashes: Set<String>
        get() = sp.getStringSet(KEY_IMPORT_HASHES, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_IMPORT_HASHES, value.toList().takeLast(12).toSet()).apply()

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HIDDEN_SUGGESTIONS = "hidden_suggestions"
        const val KEY_PENALTIES = "suggestion_penalties"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_IMPORT_HASHES = "import_hashes"
    }
}
