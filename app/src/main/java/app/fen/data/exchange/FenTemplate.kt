package app.fen.data.exchange

/** Fen 自己的导入模板：CSV，用户可以直接用 Excel / WPS 填。 */
object FenTemplate {

    val header: List<String> = listOf("日期", "类型", "金额", "分类", "备注")

    val sampleRows: List<List<String>> = listOf(
        listOf("2026-11-20 12:30", "支出", "28.50", "餐饮", "午饭"),
        listOf("2026-11-20 18:00", "收入", "5000.00", "工资", "月薪"),
    )

    fun content(): String = Csv.writeForExcel(listOf(header) + sampleRows)

    fun fileName(): String = "fen-import-template.csv"
}
