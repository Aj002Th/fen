package app.fen.data

import android.content.Context

/** 轻量偏好设置：目前只有主题模式。 */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("fen_prefs", Context.MODE_PRIVATE)

    /** 0 = 跟随系统，1 = 浅色，2 = 深色 */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME_MODE, 0)
        set(value) = sp.edit().putInt(KEY_THEME_MODE, value).apply()

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
