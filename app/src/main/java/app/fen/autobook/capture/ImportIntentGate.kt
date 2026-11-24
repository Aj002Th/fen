package app.fen.autobook.capture

object ImportIntentGate {
    fun shouldHandleOnCreate(restored: Boolean, hasImage: Boolean): Boolean =
        !restored && hasImage

    fun shouldHandleNewIntent(hasImage: Boolean): Boolean = hasImage
}
