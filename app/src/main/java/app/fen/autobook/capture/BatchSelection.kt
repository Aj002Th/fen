package app.fen.autobook.capture

import app.fen.autobook.classify.DuplicateDetector
import app.fen.autobook.model.ParsedBill
import app.fen.data.Tx

object BatchSelection {
    fun initialSelection(items: List<BatchItem>, existing: List<Tx>): Set<Int> {
        return items.indices.filter { index ->
            val bill = items[index].bill ?: return@filter false
            bill.amountMinor > 0 &&
                bill.categoryId != null &&
                DuplicateDetector.find(bill, existing) == null
        }.toSet()
    }

    fun saveable(items: List<BatchItem>, selected: Set<Int>): List<ParsedBill> {
        return items.mapIndexedNotNull { index, item ->
            val bill = item.bill ?: return@mapIndexedNotNull null
            if (index in selected && bill.amountMinor > 0 && bill.categoryId != null) bill else null
        }
    }
}
