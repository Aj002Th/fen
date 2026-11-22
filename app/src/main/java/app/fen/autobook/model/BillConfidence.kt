package app.fen.autobook.model

object BillConfidence {
    fun of(bill: ParsedBill): Float {
        if (bill.amountMinor <= 0L) return 0f

        var score = 0.35f
        if (bill.type != null) score += 0.25f
        if (bill.occurredAt > 0L) score += 0.2f
        if (bill.merchant.isNotBlank()) score += 0.1f
        if (bill.sourceId != null) score += 0.1f
        return score.coerceIn(0f, 1f)
    }
}
