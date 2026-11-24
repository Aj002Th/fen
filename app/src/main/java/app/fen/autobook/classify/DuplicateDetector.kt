package app.fen.autobook.classify

import app.fen.autobook.model.ParsedBill
import app.fen.data.Tx
import kotlin.math.abs

object DuplicateDetector {
    fun find(bill: ParsedBill, existing: List<Tx>, windowMillis: Long = 120_000): Tx? {
        return existing.firstOrNull { tx ->
            (bill.type == null || tx.type == bill.type) &&
                tx.amount == bill.amountMinor &&
                abs(tx.occurredAt - bill.occurredAt) <= windowMillis &&
                merchantMatches(bill.merchant, tx.note)
        }
    }

    private fun merchantMatches(merchant: String, note: String): Boolean {
        if (merchant.isBlank()) return true
        if (note.isBlank()) return false
        return note.contains(merchant, ignoreCase = true) ||
            merchant.contains(note, ignoreCase = true)
    }
}
