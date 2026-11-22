package app.fen.autobook.capture

import app.fen.autobook.model.ParsedBill

data class BatchItem(val bill: ParsedBill?, val error: String?)
