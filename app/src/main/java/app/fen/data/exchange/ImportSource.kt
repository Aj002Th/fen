package app.fen.data.exchange

/**
 * 导入来源。
 *
 * 与 iCost 一样：来源是「提示」而不是硬约束，默认「自动识别」。
 * 平台账单和记账 App 各有一级分组，二级才是具体来源。
 */
enum class ImportSource(val label: String, val group: String, val note: String = "") {
    AUTO("自动识别", "", "按表头自动判断格式"),
    FEN_CSV("模板文件", "", "日期 / 类型 / 金额 / 分类 / 备注"),

    WECHAT("微信账单", "平台账单", "微信导出的 xlsx / csv"),
    ALIPAY("支付宝账单", "平台账单", "支付宝导出的 csv"),
    JD("京东账单", "平台账单", "京东 / 京东金融导出的账单"),
    MEITUAN("美团账单", "平台账单", "美团钱包 / 外卖导出的账单"),

    ICOST("iCost", "记账 App", "iCost 导出的 xlsx / csv"),
    SHAYU("鲨鱼记账", "记账 App"),
    YIMU("一木记账", "记账 App"),
    MUMU("木木记账", "记账 App"),
    QIANJI("钱迹", "记账 App"),
    TUANTUAN("团团记账", "记账 App"),

    GENERIC("通用表格", "", "只依赖表头：日期/金额/分类或类型"),

    /** 只在「备份 → 恢复」里使用，不出现在导入来源菜单。 */
    FEN_JSON("完整备份", "备份", "无损 JSON，恢复会覆盖当前数据"),
    ;

    val isBackup: Boolean get() = this == FEN_JSON

    companion object {
        /** 来源选择器里的平铺顺序（不做二级菜单）。 */
        val pickerOrder: List<ImportSource> = listOf(
            AUTO, FEN_CSV,
            WECHAT, ALIPAY, JD, MEITUAN,
            ICOST, SHAYU, YIMU, MUMU, QIANJI, TUANTUAN,
            GENERIC,
        )
    }
}
