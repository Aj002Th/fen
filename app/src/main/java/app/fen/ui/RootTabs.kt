package app.fen.ui

object RootTabs {
    val routes: Set<String> = setOf("bills", "stats")

    fun isRoot(route: String?): Boolean = route in routes

    /** 根页之间切换保持瞬间切换；推入和返回都横滑。 */
    fun slidesWhenEntering(from: String?, to: String?, isPop: Boolean): Boolean =
        !(isRoot(from) && isRoot(to) && !isPop)

    fun stackAfterSelect(current: List<String>, selected: String): List<String> {
        require(selected in routes) { "Not a root tab: $selected" }
        return if (current == listOf(selected)) current else listOf(selected)
    }
}
