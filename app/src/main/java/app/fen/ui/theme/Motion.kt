package app.fen.ui.theme

object FenMotion {
    /** 页面切换时的横移时长；旧页只挪一小段，靠淡出离场。 */
    const val navSlideMillis: Int = 260

    /** 新页淡入时长。 */
    const val navFadeInMillis: Int = 180

    /** 旧页淡出时长，要比横移短，动画结束时旧页已完全不可见。 */
    const val navFadeOutMillis: Int = 120

    /** 页内状态切换（例如统计页的周/月/年）的时长。 */
    const val durationMillis: Int = 180
}
