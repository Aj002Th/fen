package app.fen.data.ledger

fun canUndo(
    batch: BatchSummary,
    now: Long,
    windowMs: Long = 24L * 60 * 60 * 1000,
): Boolean = batch.undoneAt == null && now - batch.createdAt <= windowMs
