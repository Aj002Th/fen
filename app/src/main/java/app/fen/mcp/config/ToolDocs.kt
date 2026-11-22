package app.fen.mcp.config

import app.fen.mcp.tools.ToolDescriptor
import app.fen.mcp.tools.ToolKind

fun toolDocsMarkdown(tools: List<ToolDescriptor>): String = buildString {
    appendLine("# Fen 本地 MCP 接口说明")
    appendLine()
    appendLine("Fen 是手机上的本地记账账本。金额以分为整数存储，响应同时给 `amount_minor` 和 `amount`（元，两位小数字符串）。日期使用手机本地时区。")
    appendLine()
    appendLine("## 认证")
    appendLine()
    appendLine("每个请求都带 `Authorization: Bearer <token>`。token 在手机上查看/重置。")
    appendLine()
    appendLine("## 工具")
    appendLine()
    tools.forEach { tool ->
        val kind = if (tool.kind == ToolKind.WRITE) "写入" else "读取"
        appendLine("### `${tool.name}`（$kind）")
        appendLine()
        appendLine(tool.description)
        appendLine()
    }
    appendLine("## 约定")
    appendLine()
    appendLine("- 先调用 `get_overview`，再用 `get_summary` 聚合、`list_transactions` 查明细。")
    appendLine("- 分类 id 必须来自 `list_categories`，不要臆造。")
    appendLine("- 写入默认关闭；工具列表里没有写工具时，不要重试。")
    appendLine("- 写入使用 `client_request_id` 幂等；网络重试不会重复记账。")
    appendLine("- 每次调用都会在手机上留下审计记录。")
}
