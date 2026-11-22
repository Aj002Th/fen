package app.fen.data.exchange

import app.fen.data.Category
import app.fen.data.TxType

/**
 * 分类映射建议。
 *
 * 原则：只做「低风险」建议——能把来源分类可靠地落到 Fen 已有分类时才建议；
 * 拿不准就留「未分类」，让用户在映射页自己决定，绝不擅自创建 53 个垃圾分类。
 */
object CategorySuggest {

    /** 与 FenDb 的调色板保持一致（不依赖 Compose，便于单元测试）。 */
    val palette: List<Long> = listOf(
        0xFFF2583E, 0xFF3E8BF2, 0xFF17A467, 0xFFF2A93E,
        0xFF8B6CF2, 0xFFE05C9A, 0xFF21B8C7, 0xFF7C8794,
    )

    private val expenseAliases: Map<String, String> = mapOf(
        // 餐饮
        "餐饮" to "餐饮", "餐饮美食" to "餐饮", "三餐" to "餐饮", "零食" to "餐饮",
        "外卖" to "餐饮", "早餐" to "餐饮", "午餐" to "餐饮", "晚餐" to "餐饮",
        "美食" to "餐饮", "烧烤" to "餐饮", "火锅" to "餐饮", "咖啡" to "餐饮",
        "奶茶" to "餐饮", "食堂" to "餐饮", "小吃" to "餐饮",
        // 购物
        "日用百货" to "购物", "日用" to "购物", "衣服" to "购物", "服饰装扮" to "购物",
        "家居家装" to "购物", "数码电器" to "购物", "超市" to "购物", "便利店" to "购物",
        "百货" to "购物", "网购" to "购物", "淘宝" to "购物", "京东" to "购物",
        // 交通
        "交通" to "交通", "交通出行" to "交通", "打车" to "交通", "地铁" to "交通",
        "公交" to "交通", "加油" to "交通", "停车" to "交通", "出行" to "交通",
        "单车" to "交通",
        // 娱乐
        "娱乐" to "娱乐", "文化休闲" to "娱乐", "运动户外" to "娱乐", "旅行" to "娱乐",
        "酒店旅游" to "娱乐", "电影" to "娱乐", "游戏" to "娱乐", "健身" to "娱乐",
        "旅游" to "娱乐",
        // 居住
        "住房" to "居住", "水电煤" to "居住", "话费网费" to "居住", "充值缴费" to "居住",
        "生活服务" to "居住", "房租" to "居住", "物业" to "居住", "维修" to "居住",
        // 医疗
        "医疗" to "医疗", "医疗健康" to "医疗", "药" to "医疗", "医院" to "医疗",
        // 学习
        "学习" to "学习", "应用软件" to "学习", "办公" to "学习", "教育" to "学习",
        "书籍" to "学习",
        // 明确归到「其他」的，只有用户自己建了「其他」分类才会生效
        "其他" to "其他", "商业服务" to "其他", "信用借还" to "其他",
        "美容美发" to "其他", "理发" to "其他",
    )

    private val incomeAliases: Map<String, String> = mapOf(
        "工资" to "工资", "薪资" to "工资", "月薪" to "工资", "薪水" to "工资",
        "奖金" to "奖金", "红包" to "奖金", "微信红包" to "奖金", "微信红包（群红包）" to "奖金",
        "转账红包" to "奖金", "群收款" to "奖金", "收入" to "奖金",
        "理财" to "理财", "基金" to "理财", "利息" to "理财", "投资收益" to "理财",
        "其他" to "其他",
    )

    /** 这些来源分类本身没有语义（是微信的交易类型），建议按备注自动归类。 */
    private val genericCategories: Set<String> = setOf(
        "商户消费", "扫二维码付款", "群收款", "二维码收款",
        "微信红包", "微信红包（群红包）", "转账红包", "其他", "",
    )

    private data class Rule(val category: String, val keywords: List<String>)

    private val expenseRules: List<Rule> = listOf(
        Rule("餐饮", listOf(
            "饭", "餐", "美食", "烧烤", "火锅", "拉面", "米粉", "米线", "麻辣烫", "冒菜",
            "咖啡", "奶茶", "肯德基", "麦当劳", "必胜客", "汉堡", "蛋糕", "面包", "零食",
            "水果", "外卖", "食堂", "粥", "生煎", "小吃", "饮品", "酸奶", "冰淇淋", "早餐",
            "米线", "饺", "面馆", "牛角包", "烘焙", "美团", "饿了么",
        )),
        Rule("交通", listOf(
            "地铁", "公交", "打车", "单车", "滴滴", "出行", "加油", "停车", "高铁", "火车",
            "机票", "车费", "乘车", "12306", "航空", "顺风车", "出租车", "骑行", "哈啰", "高德地图",
        )),
        Rule("购物", listOf(
            "超市", "便利店", "百货", "商城", "衣服", "鞋", "数码", "电器", "家居", "淘宝",
            "京东", "拼多多", "唯品会", "纸巾", "洗衣", "日用", "箱", "包", "快递",
        )),
        Rule("娱乐", listOf(
            "电影", "游戏", "ktv", "音乐", "视频", "会员", "旅游", "酒店", "门票", "健身",
            "球", "台球", "剧本", "演出", "乐园", "点券", "steam",
        )),
        Rule("居住", listOf(
            "房租", "水费", "电费", "燃气", "物业", "话费", "宽带", "流量", "维修", "家政",
        )),
        Rule("医疗", listOf("药", "医院", "门诊", "诊所", "体检", "口腔", "牙")),
        Rule("学习", listOf("书", "课程", "培训", "教育", "文具", "学费", "考试")),
    )

    private val incomeRules: List<Rule> = listOf(
        Rule("工资", listOf("工资", "月薪", "薪资")),
        Rule("奖金", listOf("奖金", "红包", "年终奖")),
        Rule("理财", listOf("理财", "基金", "利息", "分红")),
    )

    fun isGeneric(source: String): Boolean = source.trim() in genericCategories

    /**
     * 解析阶段先把「商户消费 / 扫二维码付款 / 群收款」这类没有语义的来源，
     * 按备注关键词归到具体分类；命中不了就归到「未分类」。
     */
    fun bucketize(records: List<ParsedRecord>, categories: List<Category>): List<ParsedRecord> =
        records.map { record ->
            if (record.type == TxType.EXPENSE && isGeneric(record.sourceCategory)) {
                val inferred = keywordCategory(record.note, record.type, categories)?.name
                    ?: UNCATEGORIZED_NAME
                record.copy(sourceCategory = inferred, rawCategory = record.sourceCategory)
            } else {
                record
            }
        }

    fun suggest(source: String, type: TxType, categories: List<Category>): MappingTarget {
        val name = source.trim()
        val existing = categories.filter { it.type == type }
        if (name.isNotEmpty()) {
            existing.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.let { return MappingTarget(MappingKind.EXISTING, it.id) }
        }
        val aliases = if (type == TxType.INCOME) incomeAliases else expenseAliases
        val alias = aliases[name]
        if (alias != null) {
            existing.firstOrNull { it.name == alias }
                ?.let { return MappingTarget(MappingKind.EXISTING, it.id) }
        }
        // 退款在 iCost 里以「商户名-退款」当分类，没有可用语义
        if (name.endsWith("-退款")) return MappingTarget(MappingKind.UNCATEGORIZED)
        return MappingTarget(MappingKind.UNCATEGORIZED)
    }

    /** 按备注关键词找已有分类；找不到返回 null。 */
    fun keywordCategory(note: String, type: TxType, categories: List<Category>): Category? {
        if (note.isBlank()) return null
        val text = note.lowercase()
        val rules = if (type == TxType.INCOME) incomeRules else expenseRules
        for (rule in rules) {
            val category = categories.firstOrNull { it.type == type && it.name == rule.category } ?: continue
            if (rule.keywords.any { text.contains(it.lowercase()) }) return category
        }
        return null
    }

    /** 新建分类时按名字挑一个合适的线性图标 key。 */
    fun iconFor(name: String, type: TxType): String {
        val text = name.lowercase()
        val expense = listOf(
            "food" to listOf("餐", "饭", "食", "咖啡", "奶茶", "外卖", "零食"),
            "shopping" to listOf("购物", "超市", "日用", "百货", "衣服", "服饰", "家居", "数码"),
            "transport" to listOf("交通", "车", "地铁", "公交", "打车", "加油", "出行"),
            "entertainment" to listOf("娱乐", "电影", "游戏", "旅", "酒店", "健身", "运动"),
            "housing" to listOf("居住", "房", "水电", "物业", "话费", "宽带"),
            "medical" to listOf("医疗", "药", "医院", "体检"),
            "study" to listOf("学习", "书", "课程", "教育", "办公"),
            "pet" to listOf("宠物", "猫", "狗"),
            "beauty" to listOf("美容", "理发"),
        )
        val income = listOf(
            "salary" to listOf("工资", "薪"),
            "bonus" to listOf("奖金", "红包", "礼"),
            "invest" to listOf("理财", "基金", "利息", "投资"),
        )
        val rules = if (type == TxType.INCOME) income else expense
        for ((icon, keywords) in rules) {
            if (keywords.any { text.contains(it) }) return icon
        }
        return if (type == TxType.INCOME) "other_income" else "other"
    }

    fun colorFor(name: String): Long = palette[(name.hashCode() and 0x7fffffff) % palette.size]
}
