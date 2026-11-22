package app.fen.data.exchange

/**
 * 导入来源。
 *
 * 与 iCost 一样：来源是「提示」而不是硬约束，默认让用户选「自动识别」。
 * 选具体来源时只做两件事：确认文件类型（尤其是 JSON 备份）和给出更准确的提示。
 */
enum class ImportSource(val label: String, val group: String, val note: String = "") {
    AUTO("自动识别", "", "按表头自动判断格式"),
    FEN_CSV("Fen 表格", "Fen", "CSV / xlsx，可自行编辑"),
    FEN_JSON("完整备份", "Fen", "无损 JSON，恢复会覆盖当前数据"),
    ICOST_EXPORT("导出账单", "iCost", "20 列 xlsx / csv"),
    ICOST_TEMPLATE("导入模板", "iCost", "10 列 xlsx / csv"),
    WECHAT("微信账单", "平台账单", "微信导出的 xlsx / csv"),
    ALIPAY("支付宝账单", "平台账单", "支付宝导出的 csv"),
    GENERIC("通用表格", "其他", "按表头尽量识别"),
    ;

    val isBackup: Boolean get() = this == FEN_JSON

    companion object {
        /** 一级菜单：自动识别 + 各分组。 */
        fun groups(): List<String> = listOf("", "Fen", "iCost", "平台账单", "其他")

        fun inGroup(group: String): List<ImportSource> =
            entries.filter { it.group == group && it != AUTO }
    }
}
