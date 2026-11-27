package app.fen.data.exchange

import app.fen.data.Category
import app.fen.data.Tx
import app.fen.data.TxType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fen 自己的 CSV 格式：`日期,类型,金额,分类,备注`。
 *
 * 面向人和 Excel：金额永远是无符号两位小数，收支由「类型」列决定；
 * 分类写名字而不是 id，方便用户在表格里直接改。
 * 如果需要无损往返（id / 图标 / 颜色），用 JSON 备份。
 */
object FenCsv {

    val header: List<String> = listOf("日期", "类型", "金额", "分类", "备注")

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun encode(
        categories: List<Category>,
        transactions: List<Tx>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val nameById = categories.associate { it.id to it.name }
        val rows = ArrayList<List<String>>(transactions.size + 1)
        rows.add(header)
        transactions.sortedBy { it.occurredAt }.forEach { tx ->
            rows.add(
                listOf(
                    Instant.ofEpochMilli(tx.occurredAt).atZone(zone).format(dateFormatter),
                    if (tx.type == TxType.EXPENSE) "支出" else "收入",
                    minorToPlainText(tx.amount),
                    tx.categoryId?.let { nameById[it] } ?: "",
                    tx.note,
                )
            )
        }
        return Csv.writeForExcel(rows)
    }
}
