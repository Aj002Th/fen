package app.fen.data.ledger

import app.fen.data.Snapshot

fun interface LedgerReader {
    fun readSnapshot(): Snapshot
}
