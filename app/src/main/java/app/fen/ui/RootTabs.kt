package app.fen.ui

object RootTabs {
    val routes: Set<String> = setOf("bills", "stats")

    fun isRoot(route: String?): Boolean = route in routes

    fun stackAfterSelect(current: List<String>, selected: String): List<String> {
        require(selected in routes) { "Not a root tab: $selected" }
        return if (current == listOf(selected)) current else listOf(selected)
    }
}
