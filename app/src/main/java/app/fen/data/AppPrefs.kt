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

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HIDDEN_SUGGESTIONS = "hidden_suggestions"
    }
}
